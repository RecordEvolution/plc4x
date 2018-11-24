/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.java.scraper;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.scraper.config.ScraperConfiguration;
import org.apache.plc4x.java.scraper.util.PercentageAboveThreshold;
import org.apache.plc4x.java.utils.connectionpool.PooledPlcDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Main class that orchestrates scraping.
 */
public class Scraper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Scraper.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10,
        new BasicThreadFactory.Builder()
            .namingPattern("scheduler-thread-%d")
            .daemon(false)
            .build()
    );
    private final ExecutorService handlerPool = Executors.newFixedThreadPool(4,
        new BasicThreadFactory.Builder()
            .namingPattern("handler-thread-%d")
            .daemon(true)
            .build()
    );

    private final ResultHandler resultHandler;

    private final MultiValuedMap<ScrapeJob, ScraperTask> tasks = new ArrayListValuedHashMap<>();
    private final MultiValuedMap<ScraperTask, ScheduledFuture<?>> futures = new ArrayListValuedHashMap<>();
    private final PlcDriverManager driverManager;
    private final List<ScrapeJob> jobs;

    /**
     * Creates a Scraper instance from a configuration.
     * By default a {@link PooledPlcDriverManager} is used.
     * @param config Configuration to use.
     * @param resultHandler
     */
    public Scraper(ScraperConfiguration config, ResultHandler resultHandler) {
        this(resultHandler, new PooledPlcDriverManager(), config.getJobs());
    }

    /**
     *
     * @param resultHandler
     * @param driverManager
     * @param jobs
     */
    public Scraper(ResultHandler resultHandler, PlcDriverManager driverManager, List<ScrapeJob> jobs) {
        this.resultHandler = resultHandler;
        Validate.notEmpty(jobs);
        this.driverManager = driverManager;
        this.jobs = jobs;
    }

    /**
     * Start the scraping.
     */
    public void start() {
        // Schedule all jobs
        LOGGER.info("Starting jobs...");
        jobs.stream()
            .flatMap(job -> job.getConnections().entrySet().stream()
                .map(entry -> Triple.of(job, entry.getKey(), entry.getValue()))
            )
            .forEach(
                tuple -> {
                    LOGGER.debug("Register task for job {} for conn {} ({}) at rate {} ms",
                        tuple.getLeft().getName(), tuple.getMiddle(), tuple.getRight(), tuple.getLeft().getScrapeRate());
                    ScraperTask task = new ScraperTask(driverManager,
                        tuple.getLeft().getName(), tuple.getMiddle(), tuple.getRight(),
                        tuple.getLeft().getFields(),
                        1_000,
                        handlerPool, resultHandler);
                    // Add task to internal list
                    tasks.put(tuple.getLeft(), task);
                    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(task,
                        0, tuple.getLeft().getScrapeRate(), TimeUnit.MILLISECONDS);

                    // Store the handle for stopping, etc.
                    futures.put(task, future);
                }
            );

        // Add statistics tracker
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<ScrapeJob, ScraperTask> entry : tasks.entries()) {
                DescriptiveStatistics statistics = entry.getValue().getLatencyStatistics();
                String msg = String.format(Locale.ENGLISH, "Job statistics (%s, %s) number of requests: %d (%d success, %.1f %% failed, %.1f %% too slow), mean latency: %.2f ms, median: %.2f ms",
                    entry.getValue().getJobName(), entry.getValue().getConnectionAlias(), entry.getValue().getRequestCounter(), entry.getValue().getSuccessfullRequestCounter(), entry.getValue().getPercentageFailed(), statistics.apply(new PercentageAboveThreshold(entry.getKey().getScrapeRate() * 1e6)), statistics.getMean() * 1e-6, statistics.getPercentile(50) * 1e-6);
                LOGGER.info(msg);
            }
        }, 1_000, 1_000, TimeUnit.MILLISECONDS);
    }

    /**
     * For testing.
     */
    ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public int getNumberOfActiveTasks() {
        return (int) futures.entries().stream().filter(entry -> !entry.getValue().isDone()).count();
    }

    public void stop() {
        // Stop all futures
        LOGGER.info("Stopping scraper...");
        for (Map.Entry<ScraperTask, ScheduledFuture<?>> entry : futures.entries()) {
            LOGGER.debug("Stopping task {}...", entry.getKey());
            entry.getValue().cancel(true);
        }
        // Clear the map
        futures.clear();
    }

    @FunctionalInterface
    public interface ResultHandler {

        void handle(Map<String, Object> results);

    }

}
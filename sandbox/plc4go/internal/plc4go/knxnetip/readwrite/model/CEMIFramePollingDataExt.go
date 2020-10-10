//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
package model

import (
	"plc4x.apache.org/plc4go-modbus-driver/0.8.0/internal/plc4go/spi"
)

// The data-structure of this message
type CEMIFramePollingDataExt struct {
	CEMIFrame
}

// The corresponding interface
type ICEMIFramePollingDataExt interface {
	ICEMIFrame
	Serialize(io spi.WriteBuffer)
}

// Accessors for discriminator values.
func (m CEMIFramePollingDataExt) NotAckFrame() bool {
	return true
}

func (m CEMIFramePollingDataExt) StandardFrame() bool {
	return false
}

func (m CEMIFramePollingDataExt) Polling() bool {
	return true
}

func (m CEMIFramePollingDataExt) initialize(repeated bool, priority CEMIPriority, acknowledgeRequested bool, errorFlag bool) spi.Message {
	m.repeated = repeated
	m.priority = priority
	m.acknowledgeRequested = acknowledgeRequested
	m.errorFlag = errorFlag
	return m
}

func NewCEMIFramePollingDataExt() CEMIFrameInitializer {
	return &CEMIFramePollingDataExt{}
}

func (m CEMIFramePollingDataExt) LengthInBits() uint16 {
	var lengthInBits uint16 = m.CEMIFrame.LengthInBits()

	return lengthInBits
}

func (m CEMIFramePollingDataExt) LengthInBytes() uint16 {
	return m.LengthInBits() / 8
}

func CEMIFramePollingDataExtParse(io spi.ReadBuffer) (CEMIFrameInitializer, error) {

	// Create the instance
	return NewCEMIFramePollingDataExt(), nil
}

func (m CEMIFramePollingDataExt) Serialize(io spi.WriteBuffer) {
	serializeFunc := func(typ interface{}) {
		if _, ok := typ.(ICEMIFramePollingDataExt); ok {
		}
	}
	serializeFunc(m)
}
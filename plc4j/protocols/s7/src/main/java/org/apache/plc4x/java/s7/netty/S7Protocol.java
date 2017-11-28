/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.plc4x.java.s7.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.apache.plc4x.java.isotp.netty.model.IsoTPMessage;
import org.apache.plc4x.java.isotp.netty.model.tpdus.DataTpdu;
import org.apache.plc4x.java.netty.events.S7ConnectionEvent;
import org.apache.plc4x.java.netty.events.S7ConnectionState;
import org.apache.plc4x.java.s7.netty.model.messages.S7Message;
import org.apache.plc4x.java.s7.netty.model.messages.S7RequestMessage;
import org.apache.plc4x.java.s7.netty.model.messages.S7ResponseMessage;
import org.apache.plc4x.java.s7.netty.model.messages.SetupCommunicationRequestMessage;
import org.apache.plc4x.java.s7.netty.model.params.ReadVarParameter;
import org.apache.plc4x.java.s7.netty.model.params.S7Parameter;
import org.apache.plc4x.java.s7.netty.model.params.SetupCommunicationParameter;
import org.apache.plc4x.java.s7.netty.model.params.items.ReadVarRequestItem;
import org.apache.plc4x.java.s7.netty.model.params.items.S7AnyReadVarRequestItem;
import org.apache.plc4x.java.s7.netty.model.payloads.S7AnyReadVarPayload;
import org.apache.plc4x.java.s7.netty.model.payloads.S7Payload;
import org.apache.plc4x.java.s7.netty.model.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class S7Protocol extends MessageToMessageCodec<IsoTPMessage, S7Message> {

    private static final byte S7_PROTOCOL_MAGIC_NUMBER = 0x32;

    private final static Logger logger = LoggerFactory.getLogger(S7Protocol.class);

    private short maxAmqCaller;
    private short maxAmqCallee;
    private short pduSize;

    public S7Protocol(short requestedMaxAmqCaller, short requestedMaxAmqCallee, short requestedPduSize) {
        this.maxAmqCaller = requestedMaxAmqCaller;
        this.maxAmqCallee = requestedMaxAmqCallee;
        this.pduSize = requestedPduSize;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof S7ConnectionEvent &&
            ((S7ConnectionEvent) evt).getState() == S7ConnectionState.ISO_TP_CONNECTION_RESPONSE_RECEIVED) {
            // Setup Communication
            SetupCommunicationRequestMessage setupCommunicationRequest =
                new SetupCommunicationRequestMessage((short) 7, maxAmqCaller, maxAmqCallee, pduSize);

            ctx.channel().writeAndFlush(setupCommunicationRequest);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, S7Message in, List<Object> out) throws Exception {
        logger.debug("S7 Message sent");

        ByteBuf buf = Unpooled.buffer();

        buf.writeByte(S7_PROTOCOL_MAGIC_NUMBER);
        buf.writeByte(in.getMessageType().getCode());
        // Reserved (is always constant 0x0000)
        buf.writeShort((short) 0x0000);
        // PDU Reference (Request Id, generated by the initiating node)
        buf.writeShort(in.getTpduReference());
        // S7 message parameters length
        buf.writeShort(getParametersLength(in.getParameters()));
        // Data field length
        buf.writeShort(getPayloadsLength(in.getPayloads()));
        if (in instanceof S7ResponseMessage) {
            S7ResponseMessage s7ResponseMessage = (S7ResponseMessage) in;
            buf.writeByte(s7ResponseMessage.getErrorClass());
            buf.writeByte(s7ResponseMessage.getErrorCode());
        }

        for (S7Parameter s7Parameter : in.getParameters()) {
            buf.writeByte(s7Parameter.getType().getCode());
            switch (s7Parameter.getType()) {
                case READ_VAR: {
                    ReadVarParameter readVar = (ReadVarParameter) s7Parameter;
                    List<ReadVarRequestItem> items = readVar.getItems();
                    // Item count (Read one variable at a time)
                    buf.writeByte((byte) items.size());
                    for (ReadVarRequestItem item : items) {
                        switch (item.getAddressingMode()) {
                            case S7ANY: {
                                S7AnyReadVarRequestItem s7AnyRequestItem = (S7AnyReadVarRequestItem) item;
                                buf.writeByte(s7AnyRequestItem.getSpecificationType().getCode());
                                // Length of this item (excluding spec type and length)
                                buf.writeByte((byte) 0x0a);
                                buf.writeByte(s7AnyRequestItem.getAddressingMode().getCode());
                                buf.writeByte(s7AnyRequestItem.getTransportSize().getCode());
                                buf.writeShort(s7AnyRequestItem.getNumElements());
                                buf.writeShort(s7AnyRequestItem.getDataBlockNumber());
                                buf.writeByte(s7AnyRequestItem.getMemoryArea().getCode());
                                buf.writeShort(s7AnyRequestItem.getByteOffset());
                                buf.writeByte(s7AnyRequestItem.getBitOffset());
                                break;
                            }
                            default:
                                logger.error("writing this item type not implemented");
                                return;
                        }
                    }
                    break;
                }
                case SETUP_COMMUNICATION: {
                    SetupCommunicationParameter setupCommunication = (SetupCommunicationParameter) s7Parameter;
                    // Reserved (is always constant 0x00)
                    buf.writeByte((byte) 0x00);
                    buf.writeShort(setupCommunication.getMaxAmqCaller());
                    buf.writeShort(setupCommunication.getMaxAmqCallee());
                    buf.writeShort(setupCommunication.getPduLength());
                    break;
                }
                case WRITE_VAR: {
                    break;
                }
            }
        }

        for (S7Payload payload : in.getPayloads()) {
            switch (payload.getType()) {
                case READ_VAR: {
                    break;
                }
                case WRITE_VAR: {
                    break;
                }
            }
        }

        out.add(new DataTpdu(true, (byte) 1, Collections.emptyList(), buf));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, IsoTPMessage in, List<Object> out) throws Exception {
        if(logger.isTraceEnabled()) {
            logger.trace("Got Data: {}", ByteBufUtil.hexDump(in.getUserData()));
        }
        ByteBuf userData = in.getUserData();
        if(userData.readableBytes() > 0) {
            logger.debug("S7 Message received");

            if (userData.readByte() != S7_PROTOCOL_MAGIC_NUMBER) {
                logger.warn("Expecting S7 protocol magic number.");
                logger.debug("Got Data: {}", ByteBufUtil.hexDump(userData));
                return;
            }

            MessageType messageType = MessageType.valueOf(userData.readByte());
            boolean isResponse = messageType == MessageType.ACK_DATA;
            // Reserved (is always constant 0x0000)
            userData.readShort();
            short tpduReference = userData.readShort();
            short headerParametersLength = userData.readShort();
            short userDataLength = userData.readShort();
            byte errorClass = 0;
            byte errorCode = 0;
            if (messageType == MessageType.ACK_DATA) {
                errorClass = userData.readByte();
                errorCode = userData.readByte();
            }
            List<S7Parameter> s7Parameters = new LinkedList<>();
            SetupCommunicationParameter setupCommunicationParameter = null;
            for (int i = 0; i < headerParametersLength; ) {
                S7Parameter parameter = parseParameter(userData, isResponse, headerParametersLength - i);
                s7Parameters.add(parameter);
                if (parameter instanceof SetupCommunicationParameter) {
                    setupCommunicationParameter = (SetupCommunicationParameter) parameter;
                }
                i += getParameterLength(parameter);
            }
            List<S7Payload> s7Payloads = new LinkedList<>();
            for (int i = 0; i < userDataLength; ) {
                DataTransportErrorCode dataTransportErrorCode = DataTransportErrorCode.valueOf(userData.readByte());
                DataTransportSize dataTransportSize = DataTransportSize.valueOf(userData.readByte());
                short length = (dataTransportSize.isSizeInBits()) ?
                    (short) Math.ceil(userData.readShort() / 8) : userData.readShort();
                byte[] data = new byte[length];
                userData.readBytes(data);
                S7AnyReadVarPayload payload = new S7AnyReadVarPayload(
                    null, dataTransportErrorCode, dataTransportSize, data);
                s7Payloads.add(payload);
                i += getPayloadLength(payload);
            }

            if (messageType == MessageType.ACK_DATA) {
                // If we got a SetupCommunicationParameter as part of the response
                // we are currently in the process of establishing a connection with
                // the PLC, so save some of the information in the session and tell
                // the next layer to negotiate the connection parameters.
                if (setupCommunicationParameter != null) {
                    maxAmqCaller = setupCommunicationParameter.getMaxAmqCaller();
                    maxAmqCallee = setupCommunicationParameter.getMaxAmqCallee();
                    pduSize = setupCommunicationParameter.getPduLength();

                    // Send an event that setup is complete.
                    ctx.channel().pipeline().fireUserEventTriggered(
                        new S7ConnectionEvent(S7ConnectionState.SETUP_COMPLETE));
                }
                out.add(new S7ResponseMessage(
                    messageType, tpduReference, s7Parameters, s7Payloads, errorClass, errorCode));
            } else {
                out.add(new S7RequestMessage(messageType, tpduReference, s7Parameters, s7Payloads));
            }
        }
    }

    private S7Parameter parseParameter(ByteBuf in, boolean isResponse, int restLength) {
        ParameterType parameterType = ParameterType.valueOf(in.readByte());
        if (parameterType == null) {
            logger.error("Could not find parameter type");
            return null;
        }
        switch (parameterType) {
            case CPU_SERVICES: {
                // Just read in the rest of the header as content of this parameter.
                // Will have to do a lot more investigation on how this parameter is
                // constructed.
                byte[] cpuServices = new byte[restLength - 1];
                in.readBytes(cpuServices);
            }
            case READ_VAR: {
                ReadVarParameter readVarParameter = new ReadVarParameter();
                byte numItems = in.readByte();
                for (int i = 0; i < numItems; i++) {
                    if (!isResponse) {
                        SpecificationType specificationType = SpecificationType.valueOf(in.readByte());
                        // Length of the rest of this item.
                        byte itemLength = in.readByte();
                        if (itemLength != 0x0a) {
                            logger.warn("Expecting a length of 10 here.");
                            return null;
                        }
                        VariableAddressingMode variableAddressingMode = VariableAddressingMode.valueOf(in.readByte());
                        switch (variableAddressingMode) {
                            case S7ANY: {
                                TransportSize transportSize = TransportSize.valueOf(in.readByte());
                                short length = in.readShort();
                                short dbNumber = in.readShort();
                                MemoryArea memoryArea = MemoryArea.valueOf(in.readByte());
                                short byteAddress = (short) (in.readShort() << 5);
                                byte tmp = in.readByte();
                                // Only the least 3 bits are the bit address, the
                                byte bitAddress = (byte) (tmp & 0x07);
                                // Bits 4-8 belong to the byte address
                                byteAddress = (short) (byteAddress | (tmp >> 3));
                                S7AnyReadVarRequestItem item = new S7AnyReadVarRequestItem(
                                    specificationType, memoryArea, transportSize,
                                    length, dbNumber, byteAddress, bitAddress);
                                readVarParameter.addRequestItem(item);
                            }
                            default: {
                                logger.error("Error parsing item type");
                                return null;
                            }
                        }
                    }
                }
                return readVarParameter;
            }
            case SETUP_COMMUNICATION: {
                // Reserved (is always constant 0x00)
                in.readByte();
                short callingMaxAmq = in.readShort();
                short calledMaxAmq = in.readShort();
                short pduLength = in.readShort();
                return new SetupCommunicationParameter(callingMaxAmq, calledMaxAmq, pduLength);
            }
            default: {
                System.out.println("Unimplemented parameter type: " + parameterType.name());
            }
        }
        return null;
    }

    private short getParametersLength(List<S7Parameter> parameters) {
        short l = 0;
        if (parameters != null) {
            for (S7Parameter parameter : parameters) {
                l += getParameterLength(parameter);
            }
        }
        return l;
    }

    private short getPayloadsLength(List<S7Payload> payloads) {
        short l = 0;
        if (payloads != null) {
            for (S7Payload payload : payloads) {
                l += getPayloadLength(payload);
            }
        }
        return l;
    }

    private short getParameterLength(S7Parameter parameter) {
        if (parameter != null) {
            switch (parameter.getType()) {
                case READ_VAR: {
                    ReadVarParameter readVarParameter = (ReadVarParameter) parameter;
                    short length = 2;
                    for (ReadVarRequestItem readVarRequestItem : readVarParameter.getItems()) {
                        switch (readVarRequestItem.getAddressingMode()) {
                            case S7ANY: {
                                length += 12;
                                break;
                            }
                            default: {
                                logger.error("Not implemented");
                                break;
                            }
                        }
                    }
                    return length;
                }
                case SETUP_COMMUNICATION: {
                    return 8;
                }
            }
        }
        return 0;
    }

    private short getPayloadLength(S7Payload payload) {
        if (payload != null) {
            switch (payload.getType()) {
                case READ_VAR: {
                    S7AnyReadVarPayload readVarPayload = (S7AnyReadVarPayload) payload;
                    return (short) (4 + readVarPayload.getData().length);
                }
                default: {
                    logger.error("Not implemented");
                }
            }
        }
        return 0;
    }

}

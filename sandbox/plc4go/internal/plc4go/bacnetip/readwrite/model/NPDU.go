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
	"errors"
	log "github.com/sirupsen/logrus"
	"plc4x.apache.org/plc4go-modbus-driver/0.8.0/internal/plc4go/spi"
	"reflect"
)

// The data-structure of this message
type NPDU struct {
	protocolVersionNumber     uint8
	messageTypeFieldPresent   bool
	destinationSpecified      bool
	sourceSpecified           bool
	expectingReply            bool
	networkPriority           uint8
	destinationNetworkAddress *uint16
	destinationLength         *uint8
	destinationAddress        []uint8
	sourceNetworkAddress      *uint16
	sourceLength              *uint8
	sourceAddress             []uint8
	hopCount                  *uint8
	nlm                       *NLM
	apdu                      *APDU
}

// The corresponding interface
type INPDU interface {
	spi.Message
	Serialize(io spi.WriteBuffer)
}

func NewNPDU(protocolVersionNumber uint8, messageTypeFieldPresent bool, destinationSpecified bool, sourceSpecified bool, expectingReply bool, networkPriority uint8, destinationNetworkAddress *uint16, destinationLength *uint8, destinationAddress []uint8, sourceNetworkAddress *uint16, sourceLength *uint8, sourceAddress []uint8, hopCount *uint8, nlm *NLM, apdu *APDU) spi.Message {
	return &NPDU{protocolVersionNumber: protocolVersionNumber, messageTypeFieldPresent: messageTypeFieldPresent, destinationSpecified: destinationSpecified, sourceSpecified: sourceSpecified, expectingReply: expectingReply, networkPriority: networkPriority, destinationNetworkAddress: destinationNetworkAddress, destinationLength: destinationLength, destinationAddress: destinationAddress, sourceNetworkAddress: sourceNetworkAddress, sourceLength: sourceLength, sourceAddress: sourceAddress, hopCount: hopCount, nlm: nlm, apdu: apdu}
}

func (m NPDU) LengthInBits() uint16 {
	var lengthInBits uint16 = 0

	// Simple field (protocolVersionNumber)
	lengthInBits += 8

	// Simple field (messageTypeFieldPresent)
	lengthInBits += 1

	// Reserved Field (reserved)
	lengthInBits += 1

	// Simple field (destinationSpecified)
	lengthInBits += 1

	// Reserved Field (reserved)
	lengthInBits += 1

	// Simple field (sourceSpecified)
	lengthInBits += 1

	// Simple field (expectingReply)
	lengthInBits += 1

	// Simple field (networkPriority)
	lengthInBits += 2

	// Optional Field (destinationNetworkAddress)
	if m.destinationNetworkAddress != nil {
		lengthInBits += 16
	}

	// Optional Field (destinationLength)
	if m.destinationLength != nil {
		lengthInBits += 8
	}

	// Array field
	if len(m.destinationAddress) > 0 {
		lengthInBits += 8 * uint16(len(m.destinationAddress))
	}

	// Optional Field (sourceNetworkAddress)
	if m.sourceNetworkAddress != nil {
		lengthInBits += 16
	}

	// Optional Field (sourceLength)
	if m.sourceLength != nil {
		lengthInBits += 8
	}

	// Array field
	if len(m.sourceAddress) > 0 {
		lengthInBits += 8 * uint16(len(m.sourceAddress))
	}

	// Optional Field (hopCount)
	if m.hopCount != nil {
		lengthInBits += 8
	}

	// Optional Field (nlm)
	if m.nlm != nil {
		lengthInBits += m.nlm.LengthInBits()
	}

	// Optional Field (apdu)
	if m.apdu != nil {
		lengthInBits += m.apdu.LengthInBits()
	}

	return lengthInBits
}

func (m NPDU) LengthInBytes() uint16 {
	return m.LengthInBits() / 8
}

func NPDUParse(io spi.ReadBuffer, npduLength uint16) (spi.Message, error) {

	// Simple Field (protocolVersionNumber)
	var protocolVersionNumber uint8 = io.ReadUint8(8)

	// Simple Field (messageTypeFieldPresent)
	var messageTypeFieldPresent bool = io.ReadBit()

	// Reserved Field (Compartmentalized so the "reserved" variable can't leak)
	{
		var reserved uint8 = io.ReadUint8(1)
		if reserved != uint8(0) {
			log.WithFields(log.Fields{
				"expected value": uint8(0),
				"got value":      reserved,
			}).Info("Got unexpected response.")
		}
	}

	// Simple Field (destinationSpecified)
	var destinationSpecified bool = io.ReadBit()

	// Reserved Field (Compartmentalized so the "reserved" variable can't leak)
	{
		var reserved uint8 = io.ReadUint8(1)
		if reserved != uint8(0) {
			log.WithFields(log.Fields{
				"expected value": uint8(0),
				"got value":      reserved,
			}).Info("Got unexpected response.")
		}
	}

	// Simple Field (sourceSpecified)
	var sourceSpecified bool = io.ReadBit()

	// Simple Field (expectingReply)
	var expectingReply bool = io.ReadBit()

	// Simple Field (networkPriority)
	var networkPriority uint8 = io.ReadUint8(2)

	// Optional Field (destinationNetworkAddress) (Can be skipped, if a given expression evaluates to false)
	var destinationNetworkAddress *uint16 = nil
	if destinationSpecified {
		_val := io.ReadUint16(16)
		destinationNetworkAddress = &_val
	}

	// Optional Field (destinationLength) (Can be skipped, if a given expression evaluates to false)
	var destinationLength *uint8 = nil
	if destinationSpecified {
		_val := io.ReadUint8(8)
		destinationLength = &_val
	}

	// Array field (destinationAddress)
	var destinationAddress []uint8
	// Count array
	{
		destinationAddress := make([]uint8, spi.InlineIf((destinationSpecified), uint16(destinationLength), uint16(0)))
		for curItem := uint16(0); curItem < uint16(spi.InlineIf((destinationSpecified), uint16(destinationLength), uint16(0))); curItem++ {

			destinationAddress = append(destinationAddress, io.ReadUint8(8))
		}
	}

	// Optional Field (sourceNetworkAddress) (Can be skipped, if a given expression evaluates to false)
	var sourceNetworkAddress *uint16 = nil
	if sourceSpecified {
		_val := io.ReadUint16(16)
		sourceNetworkAddress = &_val
	}

	// Optional Field (sourceLength) (Can be skipped, if a given expression evaluates to false)
	var sourceLength *uint8 = nil
	if sourceSpecified {
		_val := io.ReadUint8(8)
		sourceLength = &_val
	}

	// Array field (sourceAddress)
	var sourceAddress []uint8
	// Count array
	{
		sourceAddress := make([]uint8, spi.InlineIf((sourceSpecified), uint16(sourceLength), uint16(0)))
		for curItem := uint16(0); curItem < uint16(spi.InlineIf((sourceSpecified), uint16(sourceLength), uint16(0))); curItem++ {

			sourceAddress = append(sourceAddress, io.ReadUint8(8))
		}
	}

	// Optional Field (hopCount) (Can be skipped, if a given expression evaluates to false)
	var hopCount *uint8 = nil
	if destinationSpecified {
		_val := io.ReadUint8(8)
		hopCount = &_val
	}

	// Optional Field (nlm) (Can be skipped, if a given expression evaluates to false)
	var nlm *NLM = nil
	if messageTypeFieldPresent {
		_message, _err := NLMParse(io, uint16((npduLength)-((((2)+(spi.InlineIf((sourceSpecified), uint16((3)+(sourceLength)), uint16(0))))+(spi.InlineIf((destinationSpecified), uint16((3)+(destinationLength)), uint16(0))))+(spi.InlineIf(((destinationSpecified) || (sourceSpecified)), uint16(1), uint16(0))))))
		if _err != nil {
			return nil, errors.New("Error parsing 'nlm' field " + _err.Error())
		}
		var _item NLM
		_item, _ok := _message.(NLM)
		if !_ok {
			return nil, errors.New("Couldn't cast message of type " + reflect.TypeOf(_item).Name() + " to NLM")
		}
		nlm = &_item
	}

	// Optional Field (apdu) (Can be skipped, if a given expression evaluates to false)
	var apdu *APDU = nil
	if !(messageTypeFieldPresent) {
		_message, _err := APDUParse(io, uint16((npduLength)-((((2)+(spi.InlineIf((sourceSpecified), uint16((3)+(sourceLength)), uint16(0))))+(spi.InlineIf((destinationSpecified), uint16((3)+(destinationLength)), uint16(0))))+(spi.InlineIf(((destinationSpecified) || (sourceSpecified)), uint16(1), uint16(0))))))
		if _err != nil {
			return nil, errors.New("Error parsing 'apdu' field " + _err.Error())
		}
		var _item APDU
		_item, _ok := _message.(APDU)
		if !_ok {
			return nil, errors.New("Couldn't cast message of type " + reflect.TypeOf(_item).Name() + " to APDU")
		}
		apdu = &_item
	}

	// Create the instance
	return NewNPDU(protocolVersionNumber, messageTypeFieldPresent, destinationSpecified, sourceSpecified, expectingReply, networkPriority, destinationNetworkAddress, destinationLength, destinationAddress, sourceNetworkAddress, sourceLength, sourceAddress, hopCount, nlm, apdu), nil
}

func (m NPDU) Serialize(io spi.WriteBuffer) {
	serializeFunc := func(typ interface{}) {
		if _, ok := typ.(INPDU); ok {

			// Simple Field (protocolVersionNumber)
			var protocolVersionNumber uint8 = m.protocolVersionNumber
			io.WriteUint8(8, (protocolVersionNumber))

			// Simple Field (messageTypeFieldPresent)
			var messageTypeFieldPresent bool = m.messageTypeFieldPresent
			io.WriteBit((bool)(messageTypeFieldPresent))

			// Reserved Field (reserved)
			io.WriteUint8(1, uint8(0))

			// Simple Field (destinationSpecified)
			var destinationSpecified bool = m.destinationSpecified
			io.WriteBit((bool)(destinationSpecified))

			// Reserved Field (reserved)
			io.WriteUint8(1, uint8(0))

			// Simple Field (sourceSpecified)
			var sourceSpecified bool = m.sourceSpecified
			io.WriteBit((bool)(sourceSpecified))

			// Simple Field (expectingReply)
			var expectingReply bool = m.expectingReply
			io.WriteBit((bool)(expectingReply))

			// Simple Field (networkPriority)
			var networkPriority uint8 = m.networkPriority
			io.WriteUint8(2, (networkPriority))

			// Optional Field (destinationNetworkAddress) (Can be skipped, if the value is null)
			var destinationNetworkAddress *uint16 = nil
			if m.destinationNetworkAddress != nil {
				destinationNetworkAddress = m.destinationNetworkAddress
				io.WriteUint16(16, *(destinationNetworkAddress))
			}

			// Optional Field (destinationLength) (Can be skipped, if the value is null)
			var destinationLength *uint8 = nil
			if m.destinationLength != nil {
				destinationLength = m.destinationLength
				io.WriteUint8(8, *(destinationLength))
			}

			// Array Field (destinationAddress)
			if m.destinationAddress != nil {
				for _, _element := range m.destinationAddress {
					io.WriteUint8(8, _element)
				}
			}

			// Optional Field (sourceNetworkAddress) (Can be skipped, if the value is null)
			var sourceNetworkAddress *uint16 = nil
			if m.sourceNetworkAddress != nil {
				sourceNetworkAddress = m.sourceNetworkAddress
				io.WriteUint16(16, *(sourceNetworkAddress))
			}

			// Optional Field (sourceLength) (Can be skipped, if the value is null)
			var sourceLength *uint8 = nil
			if m.sourceLength != nil {
				sourceLength = m.sourceLength
				io.WriteUint8(8, *(sourceLength))
			}

			// Array Field (sourceAddress)
			if m.sourceAddress != nil {
				for _, _element := range m.sourceAddress {
					io.WriteUint8(8, _element)
				}
			}

			// Optional Field (hopCount) (Can be skipped, if the value is null)
			var hopCount *uint8 = nil
			if m.hopCount != nil {
				hopCount = m.hopCount
				io.WriteUint8(8, *(hopCount))
			}

			// Optional Field (nlm) (Can be skipped, if the value is null)
			var nlm *NLM = nil
			if m.nlm != nil {
				nlm = m.nlm
				nlm.Serialize(io)
			}

			// Optional Field (apdu) (Can be skipped, if the value is null)
			var apdu *APDU = nil
			if m.apdu != nil {
				apdu = m.apdu
				apdu.Serialize(io)
			}
		}
	}
	serializeFunc(m)
}
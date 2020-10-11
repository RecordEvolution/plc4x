//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

////////////////////////////////////////////////////////////////
// AMS/TCP Packet
////////////////////////////////////////////////////////////////

[enum uint 8 'Operation'
    ['0x01' DISCOVERY]
    ['0x06' ROUTE    ]
]

[enum uint 8 'Direction'
    ['0x00' REQUEST ]
    ['0x80' RESPONSE]
]

[discriminatedType 'AdsDiscovery'
    [const uint 32 'header' '0x03661471L']
    [reserved   uint 32  '0x00000000L']
    [enum Operation 'operation']
    [reserved   uint 16  '0x0000']
    [enum Direction 'direction']
    [typeSwitch 'operation', 'direction'
        ['Operation.DISCOVERY', 'Direction.REQUEST' DiscoveryRequest
            [simple AmsNetId 'amsNetId']
            [reserved uint 16 '0x1027']
            [reserved uint 32 '0x00000000L']
        ]
        ['Operation.DISCOVERY', 'Direction.RESPONSE' DiscoveryResponse
            [simple AmsNetId 'amsNetId']
            [reserved uint 16 '0x1027']
            [reserved uint 16 '0x0400']
            [reserved uint 32 '0x00000500L']
        ]
        ['Operation.ROUTE', 'Direction.REQUEST' RouteRequest
            [simple     AmsNetId 'sender']
            [reserved   uint 16  '0x1027']
            [reserved   uint 16  '0x0500']
            [reserved   uint 24  '0x0C00']
            [simple AmsMagicString 'ip' ]
            [reserved   uint 16 '0x0007']
            [implicit   uint 8 'amsSize' 'target.lengthInBytes']
            [simple AmsNetId 'target']
            [const uint 16 'usernamePrefix' '0x000D']
            [simple AmsMagicString 'username']
            [const uint 16 'passwordPrefix' '0x0002']
            [simple AmsMagicString 'password']
            [const uint 16 'routePrefix' '0x0005']
            [simple AmsMagicString 'routeName']

        ]
        ['Operation.ROUTE', 'Direction.RESPONSE' RouteResponse
            [simple AmsNetId 'amsNetId']
            [reserved uint 16 '0x1027']
            [reserved uint 16 '0x0100']
            [reserved uint 32 '0x00000100']
            [enum RouteStatus 'status']
            [reserved uint 24 '0x000000']
        ]
    ]
]

[enum uint 24 'RouteStatus'
    ['0x040000' SUCCESS]
    ['0x000407' FAILURE]
]

[type 'AmsMagicString'
    [implicit uint 8 'len' 'COUNT(text)']
    [reserved uint 8 '0x00']
    [array int 8 'text' COUNT 'len']
    [reserved uint 8 '0x00']
]

[type 'IPv4Address'
    [simple     uint        8   'octet1'            ]
    [simple     uint        8   'octet2'            ]
    [simple     uint        8   'octet3'            ]
    [simple     uint        8   'octet4'            ]
]


[type 'AmsNetId'
    [simple     uint        8   'octet1'            ]
    [simple     uint        8   'octet2'            ]
    [simple     uint        8   'octet3'            ]
    [simple     uint        8   'octet4'            ]
    [simple     uint        8   'octet5'            ]
    [simple     uint        8   'octet6'            ]
]

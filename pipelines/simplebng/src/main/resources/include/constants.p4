/*
 * Copyright 2018-present Johan Boer
 * This file is part of SD-Access (https://github.com/Johan-EU/sd-access).
 *
 * SD-Access is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SD-Access is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SD-Access.  If not, see <https://www.gnu.org/licenses/>.
*/

#ifndef __DEFINES__
#define __DEFINES__

const bit<16> ETHERTYPE_VLAN = 0x8100;
const bit<16> ETHERTYPE_IPV4 = 0x0800;
const bit<16> ETHERTYPE_IPV6 = 0x86dd;
const bit<16> ETHERTYPE_ARP  = 0x0806;

const bit<16> ARP_HTYPE_ETHERNET = 0x0001;
const bit<16> ARP_PTYPE_IPV4 = 0x0800;
const bit<8> ARP_HLEN_ETHERNET = 6;
const bit<8> ARP_PLEN_IPV4 = 4;
const bit<16> ARP_OPC_REQ = 1;
const bit<16> ARP_OPC_REPLY = 2;

#define IP_PROTO_TCP 8w6
#define IP_PROTO_UDP 8w17
#define MAX_PORTS 511
#define DOWNSTREAM_MAC 0xE4A7A09A549D
#define MAX_HOSTS 50

typedef bit<9>  port_t;
//const port_t CPU_PORT = 255;
typedef bit<16> next_hop_id_t;

typedef bit<3>  fwd_type_t;
const fwd_type_t FWD_DROP        = 0;
const fwd_type_t FWD_IPV4_ROUTED = 1;
const fwd_type_t FWD_TUN_BRIDGED = 2;
const fwd_type_t FWD_TUN_ROUTED  = 3;

typedef bit<2>  traffic_type_t;
const traffic_type_t DOWNSTREAM = 0;
const traffic_type_t UPSTREAM = 1;


typedef bit<8> MeterColor;
const MeterColor MeterColor_GREEN = 8w0;
const MeterColor MeterColor_YELLOW = 8w1;
const MeterColor MeterColor_RED = 8w2;
#endif

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

#ifndef __PARSERS__
#define __PARSERS__

#include "headers.p4"
#include "constants.p4"

parser BngParser(packet_in packet,
                 out headers_t hdr,
                 inout local_metadata_t local_metadata,
                 inout standard_metadata_t standard_metadata)
{

    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_controller_packet;
            default: parse_ethernet;
        }
    }

    state parse_controller_packet {
        packet.extract(hdr.from_controller);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition select(hdr.ethernet.ether_type) {
            ETHERTYPE_ARP: parse_arp;
            ETHERTYPE_IPV4: parse_ipv4;
            default: accept;
        }
    }

    state parse_arp {
        packet.extract(hdr.arp);
        transition select(hdr.arp.hw_type,
                          hdr.arp.proto_type,
                          hdr.arp.hw_addr_len,
                          hdr.arp.proto_addr_len,
                          hdr.arp.opcode) {
            (ARP_HTYPE_ETHERNET,
             ARP_PTYPE_IPV4,
             ARP_HLEN_ETHERNET,
             ARP_PLEN_IPV4,
             ARP_OPC_REQ) : parse_arp_ipv4;
            default : accept;
        }
    }

    state parse_arp_ipv4 {
        packet.extract(hdr.arp_ipv4);
        transition accept;
    }

    state parse_ipv4 {
        packet.extract(hdr.ipv4);
        transition select(hdr.ipv4.protocol) {
            IP_PROTO_TCP: parse_tcp;
            IP_PROTO_UDP: parse_udp;
            default: accept;
        }
    }

    state parse_tcp {
        packet.extract(hdr.tcp);
        local_metadata.l4_src_port = hdr.tcp.src_port;
        local_metadata.l4_dst_port = hdr.tcp.dst_port;
        transition accept;
    }

    state parse_udp {
        packet.extract(hdr.udp);
        local_metadata.l4_src_port = hdr.udp.src_port;
        local_metadata.l4_dst_port = hdr.udp.dst_port;
        transition accept;
    }
}

control BngDeparser(packet_out packet, in headers_t hdr) {
    apply {
        packet.emit(hdr.to_controller);
        packet.emit(hdr.ethernet);
        packet.emit(hdr.arp);
        packet.emit(hdr.arp_ipv4);
        packet.emit(hdr.ipv4);
        packet.emit(hdr.tcp);
        packet.emit(hdr.udp);
    }
}

#endif

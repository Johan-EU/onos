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

#ifndef __DP_PROTOCOLS__
#define __DP_PROTOCOLS__

#include "../headers.p4"
#include "../constants.p4"

control DataplaneProtocolsControl(inout headers_t hdr,
                                  inout local_metadata_t local_metadata,
                                  inout standard_metadata_t standard_metadata) {


    action drop_packet() {
        mark_to_drop();
    }

    action arp_reply(bit<48> new_mac_sa) {
        standard_metadata.egress_spec = standard_metadata.ingress_port;
        hdr.ethernet.dst_addr = hdr.ethernet.src_addr;
        hdr.ethernet.src_addr = new_mac_sa;
        hdr.arp.opcode = ARP_OPC_REPLY;
        hdr.arp_ipv4.tha = hdr.arp_ipv4.sha;
        hdr.arp_ipv4.sha = new_mac_sa;
        bit<32> tmp;
        tmp = hdr.arp_ipv4.spa;
        hdr.arp_ipv4.spa = hdr.arp_ipv4.tpa;
        hdr.arp_ipv4.tpa = tmp;
    }

    table arp_local_ip {
        key = {
            standard_metadata.ingress_port: ternary;
            hdr.arp_ipv4.tpa: exact;
        }
        actions = {
            arp_reply;
        }
        size = 256;
    }

    apply {
        if (hdr.arp_ipv4.isValid()) {
            if (local_metadata.traffic_type == UPSTREAM) {
                // Do a proxy arp for requests coming from hosts
                arp_reply(DOWNSTREAM_MAC);
            } else {
                // Only reply for local IP addresses
                arp_local_ip.apply();
            }
        }
     }
}

#endif

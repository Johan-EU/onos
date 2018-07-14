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

#ifndef __FORWARDING__
#define __FORWARDING__

#include "../headers.p4"
#include "../constants.p4"

control ForwardingControl(inout headers_t hdr,
                          inout local_metadata_t local_metadata,
                          inout standard_metadata_t standard_metadata) {


    action drop_packet() {
        mark_to_drop();
    }

    action l3_switch_upstream(bit<9> port,
                              bit<48> new_mac_da,
                              bit<48> new_mac_sa)
    {
        /* Forward the packet to the specified port */
        standard_metadata.egress_spec = port;
        /* L2 Modifications */
        hdr.ethernet.dst_addr = new_mac_da;
        hdr.ethernet.src_addr = new_mac_sa;
        /* IP header modification (TTL decrement) */
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    action l3_switch_downstream(bit<9> port,
                                bit<48> new_mac_da)
    {
        /* Forward the packet to the specified port */
        standard_metadata.egress_spec = port;
        /* L2 Modifications */
        hdr.ethernet.dst_addr = new_mac_da;
        hdr.ethernet.src_addr = DOWNSTREAM_MAC;
        /* IP header modification (TTL decrement) */
        hdr.ipv4.ttl = hdr.ipv4.ttl - 1;
    }

    table fwd_ipv4_upstream {
        key = {
            hdr.ipv4.dst_addr: lpm;
        }
        actions = {
            l3_switch_upstream;
            drop_packet;
        }
        default_action = drop_packet();
    }

    /*
    *  This table will be filled by the controller when a DHCP ACK is sent
    */
    table fwd_ipv4_downstream {
        key = {
            hdr.ipv4.dst_addr: exact;
        }
        actions = {
            l3_switch_downstream;
        }
        size = MAX_HOSTS;
    }

    apply {
        // Do not touch packets going to the controller
        if (standard_metadata.egress_spec == CPU_PORT)
            return;
        if (local_metadata.fwd_type == FWD_DROP) {
            drop_packet();
        } else {
            // Always do downstream first to cover the case of two hosts communicating
            // Cannot get more specific than the exact IP match of downstream!
            if (!fwd_ipv4_downstream.apply().hit && local_metadata.traffic_type == UPSTREAM)
                fwd_ipv4_upstream.apply();
            else
                drop_packet();
        }
     }
}

#endif

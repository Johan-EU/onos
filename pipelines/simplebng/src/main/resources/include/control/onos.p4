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

#ifndef __ONOS__
#define __ONOS__

#include "../headers.p4"
#include "../constants.p4"

control OnosForward(inout headers_t hdr,
                    inout local_metadata_t local_metadata,
                    inout standard_metadata_t standard_metadata)
{
    direct_counter(CounterType.packets_and_bytes) onos_counter;

    action send_to_controller() {
        standard_metadata.egress_spec = CPU_PORT;
    }

    table onos {
        key = {
            standard_metadata.ingress_port : ternary;
            hdr.ethernet.ether_type        : ternary;
            hdr.ipv4.protocol              : ternary;
            local_metadata.l4_src_port     : ternary;
            local_metadata.l4_dst_port     : ternary;
        }
        actions = {
            send_to_controller;
        }
        counters = onos_counter;
        size = 256;
    }

    apply {
        onos.apply();
     }
}

#endif

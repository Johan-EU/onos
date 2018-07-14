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

#ifndef __FILTERING__
#define __FILTERING__

#include "../headers.p4"
#include "../constants.p4"

control FilterControl(inout headers_t hdr,
                      inout local_metadata_t local_metadata,
                      inout standard_metadata_t standard_metadata) {

    action set_forwarding_type(fwd_type_t fwd_type) {
        local_metadata.fwd_type = fwd_type;
    }

    action forward_to_host() {
        local_metadata.fwd_type = FWD_IPV4_ROUTED;
        local_metadata.traffic_type = DOWNSTREAM;
    }

    /*
    *  This table will be filled by the controller when a DHCP ACK is sent
    */
    table host_ingress {
        key = {
            // TODO: add vlan as key in order to support VPNs
            hdr.ethernet.src_addr: exact;
            hdr.ipv4.src_addr    : exact;
        }
        actions = {
            set_forwarding_type;
        }
        const default_action = set_forwarding_type(FWD_DROP);
        size = MAX_HOSTS;
    }

    table network_ingress {
        key = {
            standard_metadata.ingress_port: exact;
            // TODO: add distinction between tunneled and non tunneled
        }
        actions = {
            forward_to_host;
        }
        size = MAX_PORTS;
    }

    apply {
        if (host_ingress.apply().hit) {
            local_metadata.traffic_type = UPSTREAM;
        } else {
            network_ingress.apply();
        }
     }
}

#endif

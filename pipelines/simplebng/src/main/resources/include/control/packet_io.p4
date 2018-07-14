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

#include "../headers.p4"

control PacketIoIngress(inout headers_t hdr,
                        inout standard_metadata_t standard_metadata) {
    apply {
        if (hdr.from_controller.isValid()) {
            standard_metadata.egress_spec = hdr.from_controller.egress_port;
            //standard_metadata.egress_port = hdr.from_controller.egress_port;
            hdr.from_controller.setInvalid();
            exit;
        }
    }
}

control PacketIoEgress(inout headers_t hdr,
                       inout standard_metadata_t standard_metadata) {
    apply {
        if (standard_metadata.egress_port == CPU_PORT) {
            hdr.to_controller.setValid();
            hdr.to_controller.ingress_port = standard_metadata.ingress_port;
        }
    }
}

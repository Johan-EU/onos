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

#include <core.p4>
#include <v1model.p4>

#include "include/headers.p4"
#include "include/constants.p4"
#include "include/parsers.p4"
#include "include/actions.p4"
#include "include/port_counters.p4"
#include "include/checksums.p4"
#include "include/control/packet_io.p4"
#include "include/control/onos.p4"
#include "include/control/filtering.p4"
#include "include/control/dataplane_protocols.p4"
#include "include/control/forwarding.p4"

//------------------------------------------------------------------------------
// INGRESS PIPELINE
//------------------------------------------------------------------------------

control BngIngress(inout headers_t hdr,
                   inout local_metadata_t local_metadata,
                   inout standard_metadata_t standard_metadata)
{
    PortCountersIngress() port_counters;
    PacketIoIngress() packetio;
    OnosForward() onos;
    FilterControl() filter;
    DataplaneProtocolsControl() dataplane_protocols;
    ForwardingControl() forwarding;

    apply {
        port_counters.apply(hdr, standard_metadata);
        //port_meters_ingress.apply(hdr, standard_metadata);
        packetio.apply(hdr, standard_metadata);
        onos.apply(hdr, local_metadata, standard_metadata);
        filter.apply(hdr, local_metadata, standard_metadata);
        dataplane_protocols.apply(hdr, local_metadata, standard_metadata);
        forwarding.apply(hdr, local_metadata, standard_metadata);
        //host_meter_control.apply(hdr, local_metadata, standard_metadata);
     }
}

//------------------------------------------------------------------------------
// EGRESS PIPELINE
//------------------------------------------------------------------------------

control BngEgress(inout headers_t hdr,
                  inout local_metadata_t local_metadata,
                  inout standard_metadata_t standard_metadata)
{
    PortCountersEgress() port_counters;
    PacketIoEgress() packetio;

    apply {
        port_counters.apply(hdr, standard_metadata);
        //port_meters_egress.apply(hdr, standard_metadata);
        packetio.apply(hdr, standard_metadata);
    }
}

//------------------------------------------------------------------------------
// SWITCH INSTANTIATION
//------------------------------------------------------------------------------

V1Switch(
    BngParser(),
    BngVerifyChecksum(),
    BngIngress(),
    BngEgress(),
    BngComputeChecksum(),
    BngDeparser()
) main;

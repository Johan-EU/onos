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

package org.onosproject.pipelines.simplebng;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiControlMetadataId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;

/**
 * Constants for SimpleBng pipeline.
 */
public final class SimpleBngConstants {

    // hide default constructor
    private SimpleBngConstants() {
    }

    // Constants that are defined in the P4 but not part of the P4 info file
    public static final int PORT_FIELD_BITWIDTH = 9;
    public static final int FWD_DROP        = 0;
    public static final int FWD_IPV4_ROUTED = 1;
    public static final int FWD_TUN_BRIDGED = 2;
    public static final int FWD_TUN_ROUTED  = 3;

    public static final String DOT = ".";
    // Header IDs
    public static final String HDR = "hdr";
    public static final String STANDARD_METADATA = "standard_metadata";
    public static final String ETHERNET = "ethernet";
    public static final String ARP_IPV4 = "arp_ipv4";
    public static final String IPV4 = "ipv4";
    public static final String LOCAL_METADATA = "local_metadata";

    // Header field IDs
    public static final PiMatchFieldId HF_IPV4_PROTOCOL_ID =
            buildPiMatchField(IPV4, "protocol", true);
    public static final PiMatchFieldId HF_IPV4_SRC_ADDR_ID =
            buildPiMatchField(IPV4, "src_addr", true);
    public static final PiMatchFieldId HF_ETHERNET_ETHER_TYPE_ID =
            buildPiMatchField(ETHERNET, "ether_type", true);
    public static final PiMatchFieldId HF_ETHERNET_SRC_ADDR_ID =
            buildPiMatchField(ETHERNET, "src_addr", true);
    public static final PiMatchFieldId HF_LOCAL_METADATA_L4_SRC_PORT_ID =
            buildPiMatchField(LOCAL_METADATA, "l4_src_port", false);
    public static final PiMatchFieldId HF_STANDARD_METADATA_INGRESS_PORT_ID =
            buildPiMatchField(STANDARD_METADATA, "ingress_port", false);
    public static final PiMatchFieldId HF_IPV4_DST_ADDR_ID =
            buildPiMatchField(IPV4, "dst_addr", true);
    public static final PiMatchFieldId HF_ARP_IPV4_TPA_ID =
            buildPiMatchField(ARP_IPV4, "tpa", true);
    public static final PiMatchFieldId HF_LOCAL_METADATA_L4_DST_PORT_ID =
            buildPiMatchField(LOCAL_METADATA, "l4_dst_port", false);

    private static PiMatchFieldId buildPiMatchField(String header, String field, boolean withHdrPrefix) {
        if (withHdrPrefix) {
            return PiMatchFieldId.of(HDR + DOT + header + DOT + field);
        } else {
            return PiMatchFieldId.of(header + DOT + field);
        }
    }

    // Table IDs
    public static final PiTableId TBL_ARP_LOCAL_IP_ID =
            PiTableId.of("BngIngress.dataplane_protocols.arp_local_ip");
    public static final PiTableId TBL_NETWORK_INGRESS_ID =
            PiTableId.of("BngIngress.filter.network_ingress");
    public static final PiTableId TBL_FWD_IPV4_UPSTREAM_ID =
            PiTableId.of("BngIngress.forwarding.fwd_ipv4_upstream");
    public static final PiTableId TBL_HOST_INGRESS_ID =
            PiTableId.of("BngIngress.filter.host_ingress");
    public static final PiTableId TBL_FWD_IPV4_DOWNSTREAM_ID =
            PiTableId.of("BngIngress.forwarding.fwd_ipv4_downstream");
    public static final PiTableId TBL_ONOS_ID =
            PiTableId.of("BngIngress.onos.onos");

    // Indirect Counter IDs
    public static final PiCounterId CNT_INGRESS_PORT_COUNTER_ID =
            PiCounterId.of("BngIngress.port_counters.ingress_port_counter");
    public static final PiCounterId CNT_EGRESS_PORT_COUNTER_ID =
            PiCounterId.of("BngEgress.port_counters.egress_port_counter");

    // Direct Counter IDs
    public static final PiCounterId CNT_ONOS_COUNTER_ID =
            PiCounterId.of("BngIngress.onos.onos_counter");

    // Action IDs
    public static final PiActionId ACT_BNGINGRESS_FILTER_FORWARD_TO_HOST_ID =
            PiActionId.of("BngIngress.filter.forward_to_host");
    public static final PiActionId ACT_BNGINGRESS_FILTER_SET_FORWARDING_TYPE_ID =
            PiActionId.of("BngIngress.filter.set_forwarding_type");
    public static final PiActionId ACT_BNGINGRESS_FORWARDING_L3_SWITCH_DOWNSTREAM_ID =
            PiActionId.of("BngIngress.forwarding.l3_switch_downstream");
    public static final PiActionId ACT_BNGINGRESS_FORWARDING_DROP_PACKET_ID =
            PiActionId.of("BngIngress.forwarding.drop_packet");
    public static final PiActionId ACT_NOACTION_ID = PiActionId.of("NoAction");
    public static final PiActionId ACT_BNGINGRESS_ONOS_SEND_TO_CONTROLLER_ID =
            PiActionId.of("BngIngress.onos.send_to_controller");
    public static final PiActionId ACT_BNGINGRESS_DATAPLANE_PROTOCOLS_ARP_REPLY_ID =
            PiActionId.of("BngIngress.dataplane_protocols.arp_reply");
    public static final PiActionId ACT_BNGINGRESS_FORWARDING_L3_SWITCH_UPSTREAM_ID =
            PiActionId.of("BngIngress.forwarding.l3_switch_upstream");

    // Action Param IDs
    public static final PiActionParamId ACT_PRM_NEW_MAC_DA_ID =
            PiActionParamId.of("new_mac_da");
    public static final PiActionParamId ACT_PRM_NEW_MAC_SA_ID =
            PiActionParamId.of("new_mac_sa");
    public static final PiActionParamId ACT_PRM_PORT_ID =
            PiActionParamId.of("port");
    public static final PiActionParamId ACT_PRM_FWD_TYPE_ID =
            PiActionParamId.of("fwd_type");

    // Action Profile IDs

    // Packet Metadata IDs
    public static final PiControlMetadataId CTRL_META__PADDING_ID =
            PiControlMetadataId.of("_padding");
    public static final PiControlMetadataId CTRL_META_INGRESS_PORT_ID =
            PiControlMetadataId.of("ingress_port");
    public static final PiControlMetadataId CTRL_META_EGRESS_PORT_ID =
            PiControlMetadataId.of("egress_port");
}

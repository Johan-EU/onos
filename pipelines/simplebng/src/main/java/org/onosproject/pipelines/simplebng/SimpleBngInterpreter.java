/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.pipelines.simplebng;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiControlMetadata;
import org.onosproject.net.pi.runtime.PiPacketOperation;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.onlab.util.ImmutableByteSequence.copyFrom;
import static org.onosproject.net.PortNumber.CONTROLLER;
import static org.onosproject.net.PortNumber.FLOOD;
import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import static org.onosproject.net.pi.model.PiPacketOperationType.PACKET_OUT;
import static org.onosproject.pipelines.simplebng.SimpleBngConstants.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Interpreter implementation for simplebng.p4.
 */
public class SimpleBngInterpreter extends AbstractHandlerBehaviour
        implements PiPipelineInterpreter {

    private final Logger log = getLogger(getClass());

    private static final ImmutableBiMap<Integer, PiTableId> TABLE_MAP =
            new ImmutableBiMap.Builder<Integer, PiTableId>()
                    .put(0, TBL_ONOS_ID)
                    .put(1, TBL_ARP_LOCAL_IP_ID)
                    .put(2, TBL_FWD_IPV4_UPSTREAM_ID)
                    //.put(3, TBL_HOST_METER_TABLE_ID)
                    .put(4, TBL_FWD_IPV4_DOWNSTREAM_ID)
                    .put(5, TBL_NETWORK_INGRESS_ID)
                    .put(6, TBL_HOST_INGRESS_ID)
                    .build();

    private static final ImmutableBiMap<Criterion.Type, PiMatchFieldId> CRITERION_MAP =
            new ImmutableBiMap.Builder<Criterion.Type, PiMatchFieldId>()
                    .put(Criterion.Type.IN_PORT, HF_STANDARD_METADATA_INGRESS_PORT_ID)
                    .put(Criterion.Type.ETH_TYPE, HF_ETHERNET_ETHER_TYPE_ID)
                    .put(Criterion.Type.IP_PROTO, HF_IPV4_PROTOCOL_ID)
                    .put(Criterion.Type.UDP_SRC, HF_LOCAL_METADATA_L4_SRC_PORT_ID)
                    .put(Criterion.Type.UDP_DST, HF_LOCAL_METADATA_L4_DST_PORT_ID)
                    .build();


    @Override
    public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId)
            throws PiInterpreterException {
        if (treatment.allInstructions().size() == 0) {
            // No actions means drop.
            throw new PiInterpreterException("Treatment has drop as action, is not supported");
        } else if (treatment.allInstructions().size() > 1) {
            // We understand treatments with only 1 instruction.
            throw new PiInterpreterException("Treatment has multiple instructions");
        }

        Instruction instruction = treatment.allInstructions().get(0);
        switch (instruction.type()) {
            case OUTPUT:
                if (piTableId == TBL_ONOS_ID) {
                    return PiAction.builder().withId(ACT_BNGINGRESS_ONOS_SEND_TO_CONTROLLER_ID).build();
                } else {
                    throw new PiInterpreterException("Output instruction not supported in table " + piTableId);
                }
            case NOACTION:
                return PiAction.builder().withId(ACT_NOACTION_ID).build();
            default:
                throw new PiInterpreterException(format(
                        "Instruction type '%s' not supported", instruction.type()));
        }
    }

    @Override
    public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet)
            throws PiInterpreterException {
        TrafficTreatment treatment = packet.treatment();

        // simplebng.p4 supports only OUTPUT instructions.
        List<Instructions.OutputInstruction> outInstructions = treatment
                .allInstructions()
                .stream()
                .filter(i -> i.type().equals(OUTPUT))
                .map(i -> (Instructions.OutputInstruction) i)
                .collect(toList());

        if (treatment.allInstructions().size() != outInstructions.size()) {
            // There are other instructions that are not of type OUTPUT.
            throw new PiInterpreterException("Treatment not supported: " + treatment);
        }

        ImmutableList.Builder<PiPacketOperation> builder = ImmutableList.builder();
        for (Instructions.OutputInstruction outInst : outInstructions) {
            if (outInst.port().isLogical() && !outInst.port().equals(FLOOD)) {
                throw new PiInterpreterException(format(
                        "Output on logical port '%s' not supported", outInst.port()));
            } else if (outInst.port().equals(FLOOD)) {
                // Since simplebng.p4 does not support flooding, we create a packet
                // operation for each switch port.
                final DeviceService deviceService = handler().get(DeviceService.class);
                for (Port port : deviceService.getPorts(packet.sendThrough())) {
                    builder.add(createPiPacketOperation(packet.data(), port.number().toLong()));
                }
            } else {
                builder.add(createPiPacketOperation(packet.data(), outInst.port().toLong()));
            }
        }
        return builder.build();
    }


    @Override
    public InboundPacket mapInboundPacket(PiPacketOperation packetIn)
            throws PiInterpreterException {
        // Assuming that the packet is ethernet, which is fine since simplebng.p4
        // can deparse only ethernet packets.
        Ethernet ethPkt;
        try {
            ethPkt = Ethernet.deserializer().deserialize(packetIn.data().asArray(), 0,
                                                         packetIn.data().size());
        } catch (DeserializationException dex) {
            throw new PiInterpreterException(dex.getMessage());
        }

        // Returns the ingress port packet metadata.
        Optional<PiControlMetadata> packetMetadata = packetIn.metadatas()
                .stream().filter(m -> m.id().equals(CTRL_META_INGRESS_PORT_ID))
                .findFirst();

        if (packetMetadata.isPresent()) {
            ImmutableByteSequence portByteSequence = packetMetadata.get().value();
            short s = portByteSequence.asReadOnlyBuffer().getShort();
            ConnectPoint receivedFrom = new ConnectPoint(packetIn.deviceId(), PortNumber.portNumber(s));
            ByteBuffer rawData = ByteBuffer.wrap(packetIn.data().asArray());
            return new DefaultInboundPacket(receivedFrom, ethPkt, rawData);
        } else {
            throw new PiInterpreterException(format(
                    "Missing metadata '%s' in packet-in received from '%s': %s",
                    CTRL_META_INGRESS_PORT_ID, packetIn.deviceId(), packetIn));
        }
    }

    private PiPacketOperation createPiPacketOperation(ByteBuffer data, long portNumber)
            throws PiInterpreterException {
        DeviceId deviceId = this.data().deviceId();
        PiControlMetadata metadata = createPacketMetadata(portNumber);
        return PiPacketOperation.builder()
                .forDevice(deviceId)
                .withType(PACKET_OUT)
                .withData(copyFrom(data))
                .withMetadatas(ImmutableList.of(metadata))
                .build();
    }

    private PiControlMetadata createPacketMetadata(long portNumber) throws PiInterpreterException {
        try {
            return PiControlMetadata.builder()
                    .withId(CTRL_META_EGRESS_PORT_ID)
                    .withValue(copyFrom(portNumber).fit(PORT_FIELD_BITWIDTH))
                    .build();
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            throw new PiInterpreterException(format(
                    "Port number %d too big, %s", portNumber, e.getMessage()));
        }
    }

    @Override
    public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
        return Optional.ofNullable(CRITERION_MAP.get(type));
    }

    @Override
    public Optional<Criterion.Type> mapPiMatchFieldId(PiMatchFieldId headerFieldId) {
        return Optional.ofNullable(CRITERION_MAP.inverse().get(headerFieldId));
    }

    @Override
    public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
        return Optional.ofNullable(TABLE_MAP.get(flowRuleTableId));
    }

    @Override
    public Optional<Integer> mapPiTableId(PiTableId piTableId) {
        return Optional.ofNullable(TABLE_MAP.inverse().get(piTableId));
    }
}

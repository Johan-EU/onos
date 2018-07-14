/*
 * Copyright 2018-present Johan Boer
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

import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.service.PiPipeconfConfig;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.onosproject.pipelines.simplebng.SimpleBngConstants.*;


/**
 * Simple BNG application which provides subscriber aggregation for clients using DHCP and PPP (future)
 * <p>
 * The app works by listening for link discovery and DHCP ACK events. NNI connections and subscriber connections
 * will be provisioned automatically.
 */
@Component(immediate = true)
public class SimpleBngApp {

    private static final String APP_NAME = "org.onosproject.pipelines.simplebng";

    // Default priority used for flow rules installed by this app.
    private static final int FLOW_RULE_PRIORITY = 100;

    // TODO decide if a link with dynamic IP address assignment is made via a host listener
    //private final HostListener hostListener = new InternalHostListener();
    private final LinkListener linkListener = new InternalLinkListener();
    private ApplicationId appId;

    private final Logger log = LoggerFactory.getLogger(getClass());

    //--------------------------------------------------------------------------
    // ONOS core services needed by this application.
    //--------------------------------------------------------------------------

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;

    //@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    //private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    //--------------------------------------------------------------------------
    // Initialize
    //--------------------------------------------------------------------------

    @Activate
    public void activate() {
        // Register app and event listeners.
        log.info("Starting...");
        appId = coreService.registerApplication(APP_NAME);
        //hostService.addListener(hostListener);
        linkService.addListener(linkListener);
        log.info("STARTED", appId.id());
    }

    @Deactivate
    public void deactivate() {
        // Remove listeners and clean-up flow rules.
        log.info("Stopping...");
        //hostService.removeListener(hostListener);
        linkService.removeListener(linkListener);
        flowRuleService.removeFlowRulesById(appId);
        log.info("STOPPED");
    }


    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            switch (event.type())  {
                case LINK_ADDED:
                    if (event.subject().type() == Link.Type.DIRECT) {
                        log.info("Suppressing configuring P4 tables as a result of ading a link");
                        provisionNetworkPort(event.subject().src());
                        provisionNetworkPort(event.subject().dst());
                    } else
                        log.info("Ignoring link event of type {} with link type {}", event.type(), event.subject().type());
                    break;
                case LINK_REMOVED:
                    // TODO deprovision port on each device of the link
                    break;
            }
        }
    }

    /**
     * Provisions the given port in the nni_ingress table of the given device if it is a device
     * that has the SimpleBng pipeline.
     *
     * @param cp device and port to be provisioned
     */
    private void provisionNetworkPort(ConnectPoint cp) {
        DeviceId deviceId = cp.deviceId();
        if (deviceId != DeviceId.NONE) {
            PiPipeconfConfig config = configService.getConfig(deviceId, PiPipeconfConfig.class);
            if (config != null && config.isValid() && config.piPipeconfId().id().equals("org.onosproject.pipelines.simplebng")) {
                PortNumber port = cp.port();

                // Insert an entry in the nni_ingress table
                PiCriterion match = PiCriterion.builder()
                                   .matchExact(HF_STANDARD_METADATA_INGRESS_PORT_ID, port.toLong())
                                   .build();
                PiAction action = PiAction.builder()
                                 .withId(ACT_BNGINGRESS_FILTER_FORWARD_TO_HOST_ID)
                                 .build();
                log.info("Inserting 'set NNI forwarding' rule on switch {}: table={}, match={}, action={}",
                         deviceId, TBL_NETWORK_INGRESS_ID, match, action);
                insertPiFlowRule(deviceId, TBL_NETWORK_INGRESS_ID, match, action);
            } else
                log.info("Device {} is not a SimpleBng pipeline, ignoring", deviceId);
        }
    }

    /**
     * Inserts a flow rule in the system that using a PI criterion and action.
     *
     * @param switchId    switch ID
     * @param tableId     table ID
     * @param piCriterion PI criterion
     * @param piAction    PI action
     */
    private void insertPiFlowRule(DeviceId switchId, PiTableId tableId,
                                  PiCriterion piCriterion, PiAction piAction) {
        FlowRule rule = DefaultFlowRule.builder()
                .forDevice(switchId)
                .forTable(tableId)
                .fromApp(appId)
                .withPriority(FLOW_RULE_PRIORITY)
                .makePermanent()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchPi(piCriterion).build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .piTableAction(piAction).build())
                .build();
        flowRuleService.applyFlowRules(rule);
    }

}


package org.onlab.onos.store.trivial.impl;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.onos.net.AnnotationsUtil;
import org.onlab.onos.net.DefaultAnnotations;
import org.onlab.onos.net.DefaultDevice;
import org.onlab.onos.net.DefaultPort;
import org.onlab.onos.net.Device;
import org.onlab.onos.net.Device.Type;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.Port;
import org.onlab.onos.net.PortNumber;
import org.onlab.onos.net.SparseAnnotations;
import org.onlab.onos.net.device.DefaultDeviceDescription;
import org.onlab.onos.net.device.DefaultPortDescription;
import org.onlab.onos.net.device.DeviceDescription;
import org.onlab.onos.net.device.DeviceEvent;
import org.onlab.onos.net.device.DeviceStore;
import org.onlab.onos.net.device.DeviceStoreDelegate;
import org.onlab.onos.net.device.PortDescription;
import org.onlab.onos.net.provider.ProviderId;
import org.onlab.onos.store.AbstractStore;
import org.onlab.util.NewConcurrentHashMap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.base.Verify.verify;
import static org.onlab.onos.net.device.DeviceEvent.Type.*;
import static org.slf4j.LoggerFactory.getLogger;
import static org.apache.commons.lang3.concurrent.ConcurrentUtils.createIfAbsentUnchecked;
import static org.onlab.onos.net.DefaultAnnotations.union;
import static org.onlab.onos.net.DefaultAnnotations.merge;

/**
 * Manages inventory of infrastructure devices using trivial in-memory
 * structures implementation.
 */
@Component(immediate = true)
@Service
public class SimpleDeviceStore
        extends AbstractStore<DeviceEvent, DeviceStoreDelegate>
        implements DeviceStore {

    private final Logger log = getLogger(getClass());

    public static final String DEVICE_NOT_FOUND = "Device with ID %s not found";

    // collection of Description given from various providers
    private final ConcurrentMap<DeviceId, Map<ProviderId, DeviceDescriptions>>
                                deviceDescs = Maps.newConcurrentMap();

    // cache of Device and Ports generated by compositing descriptions from providers
    private final ConcurrentMap<DeviceId, Device> devices = Maps.newConcurrentMap();
    private final ConcurrentMap<DeviceId, ConcurrentMap<PortNumber, Port>> devicePorts = Maps.newConcurrentMap();

    // available(=UP) devices
    private final Set<DeviceId> availableDevices = Sets.newConcurrentHashSet();


    @Activate
    public void activate() {
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        deviceDescs.clear();
        devices.clear();
        devicePorts.clear();
        availableDevices.clear();
        log.info("Stopped");
    }

    @Override
    public int getDeviceCount() {
        return devices.size();
    }

    @Override
    public Iterable<Device> getDevices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    @Override
    public Device getDevice(DeviceId deviceId) {
        return devices.get(deviceId);
    }

    @Override
    public DeviceEvent createOrUpdateDevice(ProviderId providerId,
                                     DeviceId deviceId,
                                     DeviceDescription deviceDescription) {

        Map<ProviderId, DeviceDescriptions> providerDescs
            = getOrCreateDeviceDescriptions(deviceId);

        synchronized (providerDescs) {
            // locking per device

            DeviceDescriptions descs
                = getOrCreateProviderDeviceDescriptions(providerDescs,
                                                        providerId,
                                                        deviceDescription);

            Device oldDevice = devices.get(deviceId);
            // update description
            descs.putDeviceDesc(deviceDescription);
            Device newDevice = composeDevice(deviceId, providerDescs);

            if (oldDevice == null) {
                // ADD
                return createDevice(providerId, newDevice);
            } else {
                // UPDATE or ignore (no change or stale)
                return updateDevice(providerId, oldDevice, newDevice);
            }
        }
    }

    // Creates the device and returns the appropriate event if necessary.
    // Guarded by deviceDescs value (=Device lock)
    private DeviceEvent createDevice(ProviderId providerId, Device newDevice) {

        // update composed device cache
        Device oldDevice = devices.putIfAbsent(newDevice.id(), newDevice);
        verify(oldDevice == null,
                "Unexpected Device in cache. PID:%s [old=%s, new=%s]",
                providerId, oldDevice, newDevice);

        if (!providerId.isAncillary()) {
            availableDevices.add(newDevice.id());
        }

        return new DeviceEvent(DeviceEvent.Type.DEVICE_ADDED, newDevice, null);
    }

    // Updates the device and returns the appropriate event if necessary.
    // Guarded by deviceDescs value (=Device lock)
    private DeviceEvent updateDevice(ProviderId providerId, Device oldDevice, Device newDevice) {

        // We allow only certain attributes to trigger update
        if (!Objects.equals(oldDevice.hwVersion(), newDevice.hwVersion()) ||
            !Objects.equals(oldDevice.swVersion(), newDevice.swVersion()) ||
            !AnnotationsUtil.isEqual(oldDevice.annotations(), newDevice.annotations())) {

            boolean replaced = devices.replace(newDevice.id(), oldDevice, newDevice);
            if (!replaced) {
                verify(replaced,
                        "Replacing devices cache failed. PID:%s [expected:%s, found:%s, new=%s]",
                        providerId, oldDevice, devices.get(newDevice.id())
                        , newDevice);
            }
            if (!providerId.isAncillary()) {
                availableDevices.add(newDevice.id());
            }
            return new DeviceEvent(DeviceEvent.Type.DEVICE_UPDATED, newDevice, null);
        }

        // Otherwise merely attempt to change availability if primary provider
        if (!providerId.isAncillary()) {
            boolean added = availableDevices.add(newDevice.id());
            return !added ? null :
                    new DeviceEvent(DEVICE_AVAILABILITY_CHANGED, newDevice, null);
        }
        return null;
    }

    @Override
    public DeviceEvent markOffline(DeviceId deviceId) {
        Map<ProviderId, DeviceDescriptions> providerDescs
            = getOrCreateDeviceDescriptions(deviceId);

        // locking device
        synchronized (providerDescs) {
            Device device = devices.get(deviceId);
            if (device == null) {
                return null;
            }
            boolean removed = availableDevices.remove(deviceId);
            if (removed) {
                // TODO: broadcast ... DOWN only?
                return new DeviceEvent(DEVICE_AVAILABILITY_CHANGED, device, null);
            }
            return null;
        }
    }

    @Override
    public List<DeviceEvent> updatePorts(ProviderId providerId,
                                      DeviceId deviceId,
                                      List<PortDescription> portDescriptions) {

        Device device = devices.get(deviceId);
        checkArgument(device != null, DEVICE_NOT_FOUND, deviceId);

        Map<ProviderId, DeviceDescriptions> descsMap = deviceDescs.get(deviceId);
        checkArgument(descsMap != null, DEVICE_NOT_FOUND, deviceId);

        List<DeviceEvent> events = new ArrayList<>();
        synchronized (descsMap) {
            DeviceDescriptions descs = descsMap.get(providerId);
            // every provider must provide DeviceDescription.
            checkArgument(descs != null,
                    "Device description for Device ID %s from Provider %s was not found",
                    deviceId, providerId);

            Map<PortNumber, Port> ports = getPortMap(deviceId);

            // Add new ports
            Set<PortNumber> processed = new HashSet<>();
            for (PortDescription portDescription : portDescriptions) {
                final PortNumber number = portDescription.portNumber();
                processed.add(portDescription.portNumber());

                final Port oldPort = ports.get(number);
                final Port newPort;

// event suppression hook?

                // update description
                descs.putPortDesc(portDescription);
                newPort = composePort(device, number, descsMap);

                events.add(oldPort == null ?
                        createPort(device, newPort, ports) :
                        updatePort(device, oldPort, newPort, ports));
            }

            events.addAll(pruneOldPorts(device, ports, processed));
        }
        return FluentIterable.from(events).filter(notNull()).toList();
    }

    // Creates a new port based on the port description adds it to the map and
    // Returns corresponding event.
    // Guarded by deviceDescs value (=Device lock)
    private DeviceEvent createPort(Device device, Port newPort,
                                   Map<PortNumber, Port> ports) {
        ports.put(newPort.number(), newPort);
        return new DeviceEvent(PORT_ADDED, device, newPort);
    }

    // Checks if the specified port requires update and if so, it replaces the
    // existing entry in the map and returns corresponding event.
    // Guarded by deviceDescs value (=Device lock)
    private DeviceEvent updatePort(Device device, Port oldPort,
                                   Port newPort,
                                   Map<PortNumber, Port> ports) {
        if (oldPort.isEnabled() != newPort.isEnabled() ||
            !AnnotationsUtil.isEqual(oldPort.annotations(), newPort.annotations())) {

            ports.put(oldPort.number(), newPort);
            return new DeviceEvent(PORT_UPDATED, device, newPort);
        }
        return null;
    }

    // Prunes the specified list of ports based on which ports are in the
    // processed list and returns list of corresponding events.
    // Guarded by deviceDescs value (=Device lock)
    private List<DeviceEvent> pruneOldPorts(Device device,
                                            Map<PortNumber, Port> ports,
                                            Set<PortNumber> processed) {
        List<DeviceEvent> events = new ArrayList<>();
        Iterator<Entry<PortNumber, Port>> iterator = ports.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<PortNumber, Port> e = iterator.next();
            PortNumber portNumber = e.getKey();
            if (!processed.contains(portNumber)) {
                events.add(new DeviceEvent(PORT_REMOVED, device, e.getValue()));
                iterator.remove();
            }
        }
        return events;
    }

    // Gets the map of ports for the specified device; if one does not already
    // exist, it creates and registers a new one.
    private ConcurrentMap<PortNumber, Port> getPortMap(DeviceId deviceId) {
        return createIfAbsentUnchecked(devicePorts, deviceId,
                NewConcurrentHashMap.<PortNumber, Port>ifNeeded());
    }

    private Map<ProviderId, DeviceDescriptions> getOrCreateDeviceDescriptions(
            DeviceId deviceId) {
        Map<ProviderId, DeviceDescriptions> r;
        r = deviceDescs.get(deviceId);
        if (r != null) {
            return r;
        }
        r = new HashMap<>();
        final Map<ProviderId, DeviceDescriptions> concurrentlyAdded;
        concurrentlyAdded = deviceDescs.putIfAbsent(deviceId, r);
        if (concurrentlyAdded != null) {
            return concurrentlyAdded;
        } else {
            return r;
        }
    }

    // Guarded by deviceDescs value (=Device lock)
    private DeviceDescriptions getOrCreateProviderDeviceDescriptions(
                            Map<ProviderId, DeviceDescriptions> device,
                            ProviderId providerId, DeviceDescription deltaDesc) {

        synchronized (device) {
            DeviceDescriptions r = device.get(providerId);
            if (r == null) {
                r = new DeviceDescriptions(deltaDesc);
                device.put(providerId, r);
            }
            return r;
        }
    }

    @Override
    public DeviceEvent updatePortStatus(ProviderId providerId, DeviceId deviceId,
                                 PortDescription portDescription) {
        Device device = devices.get(deviceId);
        checkArgument(device != null, DEVICE_NOT_FOUND, deviceId);

        Map<ProviderId, DeviceDescriptions> descsMap = deviceDescs.get(deviceId);
        checkArgument(descsMap != null, DEVICE_NOT_FOUND, deviceId);

        synchronized (descsMap) {
            DeviceDescriptions descs = descsMap.get(providerId);
            // assuming all providers must give DeviceDescription first
            checkArgument(descs != null,
                    "Device description for Device ID %s from Provider %s was not found",
                    deviceId, providerId);

            ConcurrentMap<PortNumber, Port> ports = getPortMap(deviceId);
            final PortNumber number = portDescription.portNumber();
            final Port oldPort = ports.get(number);
            final Port newPort;

            // update description
            descs.putPortDesc(portDescription);
            newPort = composePort(device, number, descsMap);

            if (oldPort == null) {
                return createPort(device, newPort, ports);
            } else {
                return updatePort(device, oldPort, newPort, ports);
            }
        }
    }

    @Override
    public List<Port> getPorts(DeviceId deviceId) {
        Map<PortNumber, Port> ports = devicePorts.get(deviceId);
        if (ports == null) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(ports.values());
    }

    @Override
    public Port getPort(DeviceId deviceId, PortNumber portNumber) {
        Map<PortNumber, Port> ports = devicePorts.get(deviceId);
        return ports == null ? null : ports.get(portNumber);
    }

    @Override
    public boolean isAvailable(DeviceId deviceId) {
        return availableDevices.contains(deviceId);
    }

    @Override
    public DeviceEvent removeDevice(DeviceId deviceId) {
        Map<ProviderId, DeviceDescriptions> descs = getOrCreateDeviceDescriptions(deviceId);
        synchronized (descs) {
            Device device = devices.remove(deviceId);
            // should DEVICE_REMOVED carry removed ports?
            Map<PortNumber, Port> ports = devicePorts.get(deviceId);
            if (ports != null) {
                ports.clear();
            }
            availableDevices.remove(deviceId);
            descs.clear();
            return device == null ? null :
                new DeviceEvent(DEVICE_REMOVED, device, null);
        }
    }

    /**
     * Returns a Device, merging description given from multiple Providers.
     *
     * @param deviceId device identifier
     * @param providerDescs Collection of Descriptions from multiple providers
     * @return Device instance
     */
    private Device composeDevice(DeviceId deviceId,
            Map<ProviderId, DeviceDescriptions> providerDescs) {

        checkArgument(!providerDescs.isEmpty(), "No Device descriptions supplied");

        ProviderId primary = pickPrimaryPID(providerDescs);

        DeviceDescriptions desc = providerDescs.get(primary);

        final DeviceDescription base = desc.getDeviceDesc();
        Type type = base.type();
        String manufacturer = base.manufacturer();
        String hwVersion = base.hwVersion();
        String swVersion = base.swVersion();
        String serialNumber = base.serialNumber();
        DefaultAnnotations annotations = DefaultAnnotations.builder().build();
        annotations = merge(annotations, base.annotations());

        for (Entry<ProviderId, DeviceDescriptions> e : providerDescs.entrySet()) {
            if (e.getKey().equals(primary)) {
                continue;
            }
            // TODO: should keep track of Description timestamp
            // and only merge conflicting keys when timestamp is newer
            // Currently assuming there will never be a key conflict between
            // providers

            // annotation merging. not so efficient, should revisit later
            annotations = merge(annotations, e.getValue().getDeviceDesc().annotations());
        }

        return new DefaultDevice(primary, deviceId , type, manufacturer,
                            hwVersion, swVersion, serialNumber, annotations);
    }

    /**
     * Returns a Port, merging description given from multiple Providers.
     *
     * @param device device the port is on
     * @param number port number
     * @param descsMap Collection of Descriptions from multiple providers
     * @return Port instance
     */
    private Port composePort(Device device, PortNumber number,
                Map<ProviderId, DeviceDescriptions> descsMap) {

        ProviderId primary = pickPrimaryPID(descsMap);
        DeviceDescriptions primDescs = descsMap.get(primary);
        // if no primary, assume not enabled
        // TODO: revisit this default port enabled/disabled behavior
        boolean isEnabled = false;
        DefaultAnnotations annotations = DefaultAnnotations.builder().build();

        final PortDescription portDesc = primDescs.getPortDesc(number);
        if (portDesc != null) {
            isEnabled = portDesc.isEnabled();
            annotations = merge(annotations, portDesc.annotations());
        }

        for (Entry<ProviderId, DeviceDescriptions> e : descsMap.entrySet()) {
            if (e.getKey().equals(primary)) {
                continue;
            }
            // TODO: should keep track of Description timestamp
            // and only merge conflicting keys when timestamp is newer
            // Currently assuming there will never be a key conflict between
            // providers

            // annotation merging. not so efficient, should revisit later
            final PortDescription otherPortDesc = e.getValue().getPortDesc(number);
            if (otherPortDesc != null) {
                annotations = merge(annotations, otherPortDesc.annotations());
            }
        }

        return new DefaultPort(device, number, isEnabled, annotations);
    }

    /**
     * @return primary ProviderID, or randomly chosen one if none exists
     */
    private ProviderId pickPrimaryPID(Map<ProviderId, DeviceDescriptions> descsMap) {
        ProviderId fallBackPrimary = null;
        for (Entry<ProviderId, DeviceDescriptions> e : descsMap.entrySet()) {
            if (!e.getKey().isAncillary()) {
                return e.getKey();
            } else if (fallBackPrimary == null) {
                // pick randomly as a fallback in case there is no primary
                fallBackPrimary = e.getKey();
            }
        }
        return fallBackPrimary;
    }

    /**
     * Collection of Description of a Device and it's Ports given from a Provider.
     */
    private static class DeviceDescriptions {

        private final AtomicReference<DeviceDescription> deviceDesc;
        private final ConcurrentMap<PortNumber, PortDescription> portDescs;

        public DeviceDescriptions(DeviceDescription desc) {
            this.deviceDesc = new AtomicReference<>(checkNotNull(desc));
            this.portDescs = new ConcurrentHashMap<>();
        }

        public DeviceDescription getDeviceDesc() {
            return deviceDesc.get();
        }

        public PortDescription getPortDesc(PortNumber number) {
            return portDescs.get(number);
        }

        /**
         * Puts DeviceDescription, merging annotations as necessary.
         *
         * @param newDesc new DeviceDescription
         * @return previous DeviceDescription
         */
        public synchronized DeviceDescription putDeviceDesc(DeviceDescription newDesc) {
            DeviceDescription oldOne = deviceDesc.get();
            DeviceDescription newOne = newDesc;
            if (oldOne != null) {
                SparseAnnotations merged = union(oldOne.annotations(),
                                                 newDesc.annotations());
                newOne = new DefaultDeviceDescription(newOne, merged);
            }
            return deviceDesc.getAndSet(newOne);
        }

        /**
         * Puts PortDescription, merging annotations as necessary.
         *
         * @param newDesc new PortDescription
         * @return previous PortDescription
         */
        public synchronized PortDescription putPortDesc(PortDescription newDesc) {
            PortDescription oldOne = portDescs.get(newDesc.portNumber());
            PortDescription newOne = newDesc;
            if (oldOne != null) {
                SparseAnnotations merged = union(oldOne.annotations(),
                                                 newDesc.annotations());
                newOne = new DefaultPortDescription(newOne, merged);
            }
            return portDescs.put(newOne.portNumber(), newOne);
        }
    }
}

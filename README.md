## SD-Access : software defined access aggregation

### About SD-Access
SD-Access will implement a SDN controlled Broadband Network Gateway (BNG)
with the use of P4 to implement a pipeline that offers controlled access for
hosts.

The current version is a proof of concept that focuses on normal IP over Ethernet with DHCP for host configuration.

### Why a ONOS fork?
ONOS uses a mono-repo approach and is built with BUCK. Developing a standalone application requires the use of Maven and publishing ONOS artifacts to a local
repository. For the SD-Access project it is chosen to use BUCK and put the
[SD-Access source](https://github.com/Johan-EU/sd-access/tree/master/pipelines/simplebng) in the ONOS source tree.  
Note that the source is in a directory named
simplebng.

### Getting started
SD-Access is based on a P4-defined pipeline and the [P4Runtime](https://wiki.onosproject.org/display/ONOS/P4Runtime+support+in+ONOS)
protocol is used to control the pipeline at runtime via ONOS.

Use this command to start ONOS with the SimpleBNG pipeline and BMV2:
```bash
$ ONOS_APPS=drivers.bmv2,pipelines.simplebng,lldpprovider,hostprovider,fwd,dhcp ok clean
```
Note that the information on this page is work in progress and therefore not
complete, more information will follow in due time ...

### License
SD-Access is published under [GPLv3](https://github.com/Johan-EU/sd-access/blob/master/LICENSE.txt) which is [compatible with the Apache 2.0 license](https://www.apache.org/licenses/GPL-compatibility.html) of ONOS.
The GPLv3 license only applies to files specific to SD-Access and not the forked
ONOS, the source files clearly indicate the applicable license.

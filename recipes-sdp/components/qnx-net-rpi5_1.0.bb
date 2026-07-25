SUMMARY = "Networking for the RPi5 hypervisor host: NIC drivers and guest peering"
DESCRIPTION = "The io-sock bus module and NIC drivers this board's stack loads, \
plus the vdevpeer machinery that turns a guest's virtio-net vdev into an \
interface on the host side, and the packet filter that routes for it."
LICENSE = "CLOSED"

inherit qnx-sdp-component

# The board's half of the io-sock command line. Every -m and -d flag in the
# host's startup script is a dlopen by name, so each needs its module here:
# -m pci for the bus, one devs-* per -d. The generic -m phy/-m fdt modules and
# io-sock itself come from qnx-io-sock.
#
# cgem is the Pi 5's own Ethernet -- the interface the startup script brings up
# as cgem0 and bridges for the guests -- so without it there is no host
# networking even when everything else loads.
#
# mods-vdevpeer-net is what makes guest networking possible at all: it is the
# module that turns a guest's virtio-net vdev into an interface on this side,
# and /dev/io-sock/mods-vdevpeer-net.so is what .vdev_net_start.sh waits for.
QNX_COMPONENT_FILES = "\
    mods-pci.so \
    mods-vdevpeer-net.so \
    devs-cgem.so \
    devs-igc.so \
    devs-em.so \
    devs-ix.so \
    devs-re.so \
    vpctl \
    pfctl \
    sysctl \
"

# if_up is not in every SDP layout.
QNX_COMPONENT_FILES += "if_up"
QNX_COMPONENT_OPTIONAL = "if_up"

DEPENDS += "qnx-io-sock"

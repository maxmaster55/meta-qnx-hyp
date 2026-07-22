SUMMARY = "QNX hypervisor host image for Raspberry Pi 5"
DESCRIPTION = "Reproduces qnx_host/images/rpi5-hypervisor.build from the QNX \
hypervisor project through meta-qnx. Exists as much to test meta-qnx's \
genericity as to be useful: a hypervisor host differs from the guest image in \
load address, image format, startup program and hardware, and none of that \
required a change to meta-qnx."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-host.build.in"

inherit qnx-ifs

S = "${WORKDIR}"
B = "${WORKDIR}/build"

QNX_IFS_NAME = "qnx-host"
QNX_IFS_TEMPLATE = "${S}/qnx-host.build.in"

QNX_IFS_INSTALL = "rpi-gpio shm-chunker frame-router motor-controller qnx-host-conf \
                   libepoxy virglrenderer vdev-virtio-gpu"

# ---------------------------------------------------------------------------
# Boot configuration -- the host, not a guest
# ---------------------------------------------------------------------------
# The Pi's firmware loads this image directly, so it is raw and compressed at a
# low address rather than ELF at 0x80000000 (meta-qnx's guest defaults).
#
#   -u reg          use the register-based startup interface
#   -W 5000         arm the hardware watchdog with a 5s timeout -- this is why
#                   wdtkick must run early in the startup script
#   -Q enable,el1-host  enable the hypervisor, host runs at EL1
QNX_IMAGE_ADDR = "0x80000"
QNX_IMAGE_VIRTUAL = "${QNX_PROCESSOR},raw -compress"
QNX_STARTUP = "startup-bcm2712-rpi5"
QNX_STARTUP_ARGS = "-v -u reg -a -W 5000 -Q enable,el1-host"
QNX_KERNEL_ARGS = "-v"
QNX_IFS_PATH = "/proc/boot:/sbin:/bin:/usr/bin:/usr/sbin:/usr/libexec"
QNX_IFS_LD_LIBRARY_PATH = "/proc/boot:/lib:/usr/lib:/lib/dll:/lib/dll/pci"

# ---------------------------------------------------------------------------
# Guest networking
# ---------------------------------------------------------------------------
# The peer path is /dev/qvm/<system>/<vdev name>, taken from the guest's
# .qvmconf: its "system" line and the name of its guest_to_host virtio-net vdev.
# If these disagree, vpctl binds nothing and the guest comes up with a dead
# interface -- so they are named here rather than buried in the boot script.
#
# A layer that adds a differently-named guest overrides these; a layer that adds
# a second guest also needs a vp1 stanza.
QNX_HOST_GUEST_PEER ?= "/dev/qvm/guest_1/guest_to_host"
QNX_HOST_GUEST_IP ?= "10.0.0.1"
QNX_HOST_GUEST_NET ?= "10.0.0.0/24"

# ---------------------------------------------------------------------------
# BSP binaries
# ---------------------------------------------------------------------------
# startup-bcm2712-rpi5, i2c-dwc-rpi5, devc-serpl011-rpi5, gpio-rp1, msix-rp1 and
# wdtkick are built from the BSP sources in qnx_host/src and are not part of the
# SDP, so the BSP install tree is added as a second mkifs search root. Building
# that BSP through Yocto is a separate job; for now this consumes its output.
QNX_IFS_EXTRA_ROOTS = "${QNX_PROJECT_SRC}/qnx_host/install"

python () {
    if not d.getVar('QNX_PROJECT_SRC'):
        raise bb.parse.SkipRecipe(
            "QNX_PROJECT_SRC is not set; it is needed for the RPi5 BSP install "
            "tree that provides startup-bcm2712-rpi5 and the board drivers.")
}

do_configure[noexec] = "1"
do_compile[noexec] = "1"

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

# What the project's own rpi5-hypervisor.build stages: frame_router,
# giga_spi_8adc, rpi-gpio and the GPU stack. shm_sender is deliberately absent
# -- the project stages it in neither the host nor a guest, and it was only ever
# here because it was the first application ported.
#
# packagegroup-qnx-hyp-common is frame-router and rpi-gpio, the components that
# by construction exist on both sides of the hypervisor. The guest image
# installs the same group, so the two cannot drift apart.
#
# The qnx-* entries on the first two lines are the SDP itself, each a recipe
# naming every file that component consists of. That is what keeps ~90 lines of
# pci_cap-*.so and devs-*.so out of this image's template, and more to the point
# what makes "did I list all of them?" a question asked once in one file rather
# than per image and answered on the board -- see qnx-sdp-component.bbclass.
QNX_IFS_INSTALL = "qnx-base-runtime qnx-block qnx-io-sock \
                   qnx-pci-rpi5 qnx-net-rpi5 qnx-storage-sdmmc-rpi5 \
                   packagegroup-qnx-hyp-common motor-controller qnx-host-conf \
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

# Real hardware, not a virtio console: /dev/console follows the board's UART,
# which devc-serpl011-rpi5 creates in the startup script. Consumed by the shared
# qnx-base.build.inc fragment, whose default is the guest's /dev/vcon1.
QNX_CONSOLE_DEV = "/dev/ser10"

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
# SD card and the data partition
# ---------------------------------------------------------------------------
# The IFS is RAM-resident and carries none of the bulk: guests, Qt and writable
# state all live on the disk's second partition, built by qnx-host-data and
# union-mounted on / by .storage-server.sh. Without that mount the board boots
# to a shell where /guests does not exist -- which looks like the data partition
# was never built, and is really just that nothing mounted it.
#
# The SDMMC controller address and IRQ are board data, taken from the reference
# BSP build file. They are here rather than in qnx-storage-sdmmc-rpi5 because
# they belong to the startup *sequence*, which is this template's business: the
# component owns the driver and the mount script, the image decides where in the
# boot order they go. Which partition gets mounted, and how long to wait for it,
# are the component's (QNX_STORAGE_PART, QNX_STORAGE_WAIT).
QNX_HOST_SDMMC_ADDR ?= "0x1000fff000"
QNX_HOST_SDMMC_IRQ ?= "305"

# ---------------------------------------------------------------------------
# BSP binaries
# ---------------------------------------------------------------------------
# startup-bcm2712-rpi5, i2c-dwc-rpi5, devc-serpl011-rpi5, gpio-rp1, msix-rp1 and
# wdtkick. These are not in ${QNX_TARGET}: the Software Center delivers a BSP as
# a zip under ${QNX_SDP_ROOT}/bsp, and qnx-rpi5-bsp unpacks its prebuilt tree
# into the stage tree -- which is already an mkifs search root, so this image's
# build file names them by bare name and nothing here knows where they came
# from. Get the zip with the bsp-rpi5 SDP feature.
#
# Conditional because QNX_BSP_ROOT below answers the same question a different
# way: a build pointed at its own BSP tree does not want this recipe, and an SDP
# predating the BSP package has no zip for it to unpack.
DEPENDS += "${@'qnx-rpi5-bsp' if not (d.getVar('QNX_BSP_ROOT') or '').strip() else ''}"

# For a BSP built outside the SDP, or a locally modified one: an additional
# mkifs search root, searched before $QNX_TARGET. Empty by default, and a path
# rather than a reference to anybody's project tree.
QNX_BSP_ROOT ?= ""
QNX_IFS_EXTRA_ROOTS = "${QNX_BSP_ROOT}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

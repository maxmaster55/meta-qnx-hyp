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
                   qnx-pci qnx-pci-rpi5 qnx-net-rpi5 qnx-storage-sdmmc-rpi5 \
                   qnx-net-tools qnx-diag-tools qnx-fs-tools qnx-login \
                   qnx-usb qnx-hid qnx-screen qnx-gfx-demos qnx-gfx-demos-rpi5 qnx-ssh \
                   packagegroup-qnx-hyp-common motor-data-producer qnx-host-conf \
                   wifi-service hms mosquitto \
                   libepoxy virglrenderer vdev-virtio-gpu"

# hms manages the guests -- it reads /guests, execs qvm and takes commands over
# MQTT -- so it belongs on the host by definition; inside a guest there would be
# nothing to manage. mosquitto comes with it: hms links libmosquitto.so.1, and
# an image with the binary and not the library gets a process that dies at
# startup with ELIBACC rather than anything that names the missing library.
#
# It is installed, not started. It reaches a broker over the network and can
# stop and start guests, so when it runs is a decision rather than a default.
# It also needs two private ssh keys that no layer supplies -- see the recipe.
#
# wifi-service is here rather than in the guest because bcm0 is this board's own
# radio: the host owns it, the driver comes up on the io-sock boot line above,
# and its firmware is the SDP's bcm43455_firmware_pkg. A guest under qvm has
# virtio interfaces and no radio to configure.
#
# Installing it does not start it -- nothing in the startup script runs it, and
# that is deliberate. .wifi-start.sh already associates with the network
# qnx-host-conf configures; wifi_service takes bcm0 down to join the phone's
# provisioning hotspot instead, which would drop the link the board is being
# administered over. It is a tool to run from the console when the configured
# network is not available.

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

# libwfdcfg.so.0 IS in this image, and the dependency closure cannot see that.
#
# libWFDrpi5.so has DT_NEEDED libwfdcfg.so.0, and the closure resolves sonames by
# bare name across the mkifs search directories -- lib, lib/dll, usr/lib and so
# on. This one lives in usr/lib/graphics/drm-rpi5/, a nested driver directory
# that is on no search path, so the closure reports it missing and warns that
# "anything linking them will fail at startup with ELIBACC".
#
# It will not. The build file names the whole drm-rpi5 tree explicitly, including
# libwfdcfg-rpi5.so -- which is a symlink to libwfdcfg.so.0, and [+keeplinked]
# brings the target along with it. The image has
#
#     lib/graphics/drm-rpi5/libwfdcfg-rpi5.so -> libwfdcfg.so.0
#     lib/graphics/drm-rpi5/libwfdcfg.so.0
#
# and Screen dlopens it from that directory by the path in its own config, never
# through the library search path. Excluding it silences a warning that is wrong,
# rather than papering over one that is right.
QNX_IFS_DEP_EXCLUDE = "libwfdcfg.so.0"

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

# The Linux guest's peer, bound as vp1. Same shape, different system name --
# it has to match `system` in linux.qvmconf (QNX_LINUX_GUEST_NAME) and the vdev
# named guest_to_host in it.
#
# A different subnet from vp0 on purpose: both are point-to-point links to this
# host, and putting them on one /24 would make the host's routing table
# ambiguous about which interface reaches which guest.
QNX_HOST_LINUX_PEER ?= "/dev/qvm/guest_2/guest_to_host"
QNX_HOST_LINUX_IP ?= "10.0.1.1"
QNX_HOST_LINUX_NET ?= "10.0.1.0/24"

# ---------------------------------------------------------------------------
# The board's own address on the LAN
# ---------------------------------------------------------------------------
# bridge0 carries the physical NIC and is how the board reaches the outside
# world -- and how NAT for the guest networks gets anywhere, since pf.conf
# translates to this interface's address.
#
# Static, not DHCP, matching the reference BSP: the board is the gateway for two
# guest networks, and a lease that changes would silently break their routing.
# The reference has dhcpcd commented out for the same reason.
#
# Creating the bridge without addressing it, which is the state this drifted
# into, gives a board that boots clean, answers on neither address, and reports
# nothing about it.
QNX_HOST_BRIDGE_IP ?= "192.168.2.2"
QNX_HOST_BRIDGE_MASK ?= "255.255.255.0"
QNX_HOST_GATEWAY ?= "192.168.2.1"

# Name servers, written to /etc/resolv.conf.
#
# Needed because the wired path is static. dhcpcd's 20-resolv.conf hook is what
# normally writes this file, and dhcpcd only runs for wifi -- so a board on the
# wired NIC came up with a working route and no resolver at all:
#
#     # ping 8.8.8.8            -> replies
#     # ping www.google.com     -> ping: UDP connect: No route to host
#
# which reads as a routing fault and is not one. Nothing else in the image
# supplies the file: the SDP contributes /etc/hosts, services, protocols and
# netconfig, and stops there.
#
# The gateway first, since on this network it is the router and answers DNS;
# 8.8.8.8 behind it so that name resolution still works on a network whose
# gateway does not. Space separated, one `nameserver` line each.
QNX_HOST_DNS ?= "${QNX_HOST_GATEWAY} 8.8.8.8"

# One `nameserver` line per entry, which is what resolv.conf wants. chr(10)
# rather than a backslash escape so there is no question of which parser eats it.
QNX_HOST_RESOLV = "${@chr(10).join('nameserver %s' % s for s in (d.getVar('QNX_HOST_DNS') or '').split())}"

# Template markers are expanded at task time from a file, so bitbake cannot see
# which variables a build file depends on -- changing one would leave the image
# unrebuilt. The class names its own; these are this image's.
#
# Only the new ones are listed rather than every address in the template: adding
# the rest is right, but it changes the signature of an image that is currently
# known-good on the board, and that is a separate change from fixing DNS.
do_generate_buildfile[vardeps] += "QNX_HOST_DNS QNX_HOST_RESOLV"

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

# ---------------------------------------------------------------------------
# The identity hms logs into guests with
# ---------------------------------------------------------------------------
# The private half of the pair the guests authorise. It is a secret, so it is
# not in this layer: QNX_SSH_IDENTITY is a path on the build host, set in
# local.conf beside QNX_SDP_ROOT, and qnx-ssh installs whatever it points at as
# /root/.ssh/id_ed25519 at 0600.
#
# Left unset the image still builds and hms still runs -- it simply cannot log
# into a guest, and reports that per connection rather than at build time. The
# reference build file does the same thing from a path that is gitignored there.
require conf/hms-ssh-key.inc

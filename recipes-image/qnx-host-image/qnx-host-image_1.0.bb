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
                   wifi-service mosquitto \
                   libepoxy virglrenderer vdev-virtio-gpu"

# hms is NOT in the list above any more -- it comes from qnx-host-data's
# QNX_ROOTFS_INSTALL instead (see qnx-host-data_1.0.bb), so the binary lands on
# the writable data partition rather than this read-only IFS.
#
# It used to be here, and every fix to it meant rebuilding and reflashing this
# whole image to change one binary -- the same cost as fixing a driver, for a
# management agent that changes far more often. Nothing about hms needs the IFS
# specifically: it is not needed before the data partition is mounted (it reads
# /guests, which does not exist before then), and it is not on anyone's boot
# critical path the way a filesystem or network driver is. On the data
# partition, a new hms is `scp build/hms root@host:/bin/hms` -- no image
# rebuild, no reflash, and .hms-start.sh finds it at the same /bin/hms either
# way, since it runs after the data partition is already mounted over /.
#
# mosquitto stays here, in the IFS: hms links libmosquitto.so.1, and an image
# with the binary and not the library gets a process that dies at startup with
# ELIBACC rather than anything that names the missing library. It costs nothing
# to leave it where it always was -- it is the library, not the thing being
# iterated on -- and QNX's pathname space resolves it the same way regardless
# of which filesystem hms's own binary happens to load from.
#
# hms is started by the boot script, after the data partition is mounted, since
# it reads /guests to discover what it can manage and that directory does not
# exist before then. It still needs the private key from QNX_SSH_IDENTITY to
# reach a guest -- without it hms runs, answers the broker, and fails at the
# ssh on every guest operation.
#
# wifi-service is here rather than in the guest because bcm0 is this board's own
# radio: the host owns it, the driver comes up on the io-sock boot line above,
# and its firmware is the SDP's bcm43455_firmware_pkg. A guest under qvm has
# virtio interfaces and no radio to configure.
#
# It is started after .wifi-start.sh, which associates with the network
# qnx-host-conf configured -- wifi_service only has something to do when that
# failed, and its own first step re-reads the same configuration.
#
# It does take bcm0 down to join the phone's hotspot when it gets that far, so a
# board administered over WiFi loses that link. The wired bridge0 is unaffected,
# which is what makes running it by default survivable.

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

# Which machine this shell is on. Three consoles on this board look identical --
# this one, the QNX guest's, and an ssh session into either -- and the stock
# prompt is a bare "# " everywhere. A command meant for a guest is not always
# harmless run here.
QNX_IFS_PROMPT = "(HOST)# "

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

# The QNX guest's third link, bound as vp2 and bridged onto the physical NIC so
# SOME/IP multicast reaches the head unit. Must match the `name` of the
# guest_to_lan vdev in qnx-guest.qvmconf.
#
# Bridged, and therefore has no address on this side -- see .vdev_net_start.sh.
QNX_HOST_GUEST_LAN_PEER ?= "/dev/qvm/guest_1/guest_to_lan"

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

# ---------------------------------------------------------------------------
# NAT for the guest networks
# ---------------------------------------------------------------------------
# The interfaces guest traffic may be translated onto, and the networks that
# get translated. One `nat` rule is generated per pair.
#
# Both uplinks are listed, not one. The board has two ways off itself and it is
# not a build-time fact which is in use: bridge0 for the wired LAN, bcm0 when
# the wifi supplicant associates -- and dhcpcd then installs a default route
# through it, replacing the static one this image sets. A guest whose packets
# leave through bcm0 while the only nat rule says bridge0 is not translated at
# all, so it sends 10.0.0.2 onto a network that has never heard of it and the
# replies go nowhere. Naming both costs one unused rule; pf applies nat only on
# the interface a packet actually leaves through, and tolerates a rule naming an
# interface that does not exist -- which bcm0 does not, at the point in the boot
# script where these are loaded.
# ---------------------------------------------------------------------------
# hms priority
# ---------------------------------------------------------------------------
# What hms is spawned at, rather than procnto's default 10.
#
# Must not sit below the guest vCPUs, or the manager is preempted by the thing
# it manages -- and hms's Monitor path is the one that notices, because it SSHes
# into the guest and the handshake is CPU on both ends. Matches
# QNX_GUEST_VCPU_AP_SCHED in meta-qnx-guest so the two timeslice rather than one
# starving the other, and stays below io-sock's 21.
QNX_HOST_HMS_PRIORITY ?= "20"

# How long .hms-start.sh waits for the wifi to have an address before starting
# hms regardless. The broker is on the public internet and the route to it comes
# from the wifi lease, so starting earlier just means hms logs connect failures
# over the top of the wifi's own boot output.
#
# Bounded rather than indefinite on purpose: a board whose wifi never associates
# is exactly the board someone needs to reach, and hms retries the broker on its
# own. Set it to 0 to start hms immediately.
QNX_HOST_HMS_WAIT ?= "60"

# How big each guest's recording volume is made on the card, in bytes for dd.
# Sparse, so this is a ceiling rather than an allocation -- the card gives up
# only what the recordings use. 8 GiB.
#
# It is deliberately not in the image: a gigabyte inside rootfs.img costs about
# 100s of build time every time, a gigabyte here costs one seek on the card,
# once. Must agree with QNX_GUEST_RECORD_SIZE in meta-qnx-guest.
QNX_HOST_RECORD_SIZE ?= "8589934592"

# Which guests get one. Not every guest under /guests wants an 8 GiB volume --
# the Linux guest has its own storage -- and creating one for each would need
# the data partition to reserve space for all of them.
QNX_HOST_RECORD_GUESTS ?= "guest-1"

QNX_HOST_NAT_IFS ?= "bridge0 bcm0"
QNX_HOST_NAT_NETS ?= "${QNX_HOST_GUEST_NET} ${QNX_HOST_LINUX_NET}"

# -> (iface), parenthesised, is a lazy lookup: pf reads the address when a
# packet matches rather than when the rule loads. That is what lets these load
# before the wifi has associated or the bridge has been addressed.
#
# "to !<net>" rather than "to any", and that exclusion is load-bearing now that
# the QNX guest is bridged onto the LAN. With "to any" the rule also matched
# guest-to-head-unit traffic -- two hosts on the same wire, one segment, no
# routing involved -- and translated a purely local conversation. NAT belongs
# on traffic leaving the board, not on traffic that never does.
QNX_HOST_PF_NAT = "${@chr(10).join('nat on %s inet from %s to !%s -> (%s)' % (i, n, n, i) \
                     for i in (d.getVar('QNX_HOST_NAT_IFS') or '').split() \
                     for n in (d.getVar('QNX_HOST_NAT_NETS') or '').split())}"

# Template markers are expanded at task time from a file, so bitbake cannot see
# which variables a build file depends on -- changing one would leave the image
# unrebuilt. The class names its own; these are this image's.
#
# Only the new ones are listed rather than every address in the template: adding
# the rest is right, but it changes the signature of an image that is currently
# known-good on the board, and that is a separate change from fixing DNS.
do_generate_buildfile[vardeps] += "QNX_HOST_DNS QNX_HOST_RESOLV \
                                   QNX_HOST_HMS_PRIORITY QNX_HOST_HMS_WAIT \
                                   QNX_HOST_RECORD_SIZE QNX_HOST_RECORD_GUESTS QNX_HOST_GUEST_LAN_PEER \
                                   QNX_HOST_NAT_IFS QNX_HOST_NAT_NETS \
                                   QNX_HOST_PF_NAT"

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

# hms asks for the same key by two different names, so it is installed once and
# linked:
#
#     ssh_key=/root/.ssh/id_ed25519      to reach the guests
#     ota_server_key=/.ssh/id_ed25519    to reach the OTA server
#
# /.ssh is ~/.ssh here -- qnx-base.build.inc sets HOME=/ in /etc/profile, even
# though /etc/passwd gives root /root. A link rather than a second copy: it is
# one key, and two files that can drift apart is what this must not become.
QNX_SSH_IDENTITY_LINKS = "/.ssh/id_ed25519"

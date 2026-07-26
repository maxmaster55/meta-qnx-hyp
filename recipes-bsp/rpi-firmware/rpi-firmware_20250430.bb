SUMMARY = "Raspberry Pi boot firmware, device tree and overlays"
DESCRIPTION = "The Broadcom/RPi files the Pi's own bootloader reads before any \
QNX code runs: start4.elf, fixup4.dat, the board device tree and the overlays \
directory. These are not QNX components and are not in the SDP -- they come \
from raspberrypi/firmware, where the Pi Foundation publishes them, replacing \
the copies that used to be taken out of the hypervisor monorepo."
HOMEPAGE = "https://github.com/raspberrypi/firmware"

# Proprietary rather than meta-raspberrypi's "Broadcom-RPi": that name is a
# custom licence carried in that layer's files/custom-licenses, and this layer
# does not depend on it. The checksum is still taken over the real Broadcom
# text, so the licence that applies is recorded either way.
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://LICENCE.broadcom;md5=c403841ff2837657b2ed8e5bb474ac8d"

inherit deploy nopackages

# Pinned, not tracking a branch: this is boot firmware, and "whatever was on
# master the day you built" is the last thing you want to bisect when a board
# stops coming up. This is the revision meta-raspberrypi pins for scarthgap, so
# it is a combination somebody else is testing too.
#
# To move it: bump SRCREV and PV, run `bitbake -c fetch rpi-firmware`, and paste
# the sha256 it prints.
SRCREV = "bc7f439c234e19371115e07b57c366df59cc1bc7"
PV = "20250430"

# GitHub names a tarball's top directory <owner>-<repo>-<short sha>.
SHORTREV = "${@d.getVar('SRCREV', False).__str__()[:7]}"

# The tarball endpoint, because the firmware repo publishes no release artifacts
# and there is no way to fetch a subdirectory. That is the one real cost here:
# the archive is most of a gigabyte -- it carries kernel modules and a hardfp
# userland -- for the ~3MB actually used. It is fetched once into DL_DIR.
SRC_URI = "https://api.github.com/repos/raspberrypi/firmware/tarball/${SRCREV};downloadfilename=raspberrypi-firmware-${SHORTREV}.tar.gz"
SRC_URI[sha256sum] = "2c027debbef53c86c9ff9197d056d501b95f6ad214ad4db00a8a59b947574eb1"

# Everything wanted is in boot/, so S points there directly and every path below
# -- including LIC_FILES_CHKSUM -- is relative to it.
S = "${WORKDIR}/raspberrypi-firmware-${SHORTREV}/boot"

INHIBIT_DEFAULT_DEPS = "1"
EXCLUDE_FROM_WORLD = "1"
COMPATIBLE_MACHINE = "qnx-aarch64le"
PACKAGE_ARCH = "${MACHINE_ARCH}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
do_install[noexec] = "1"

# The device tree is the only per-board file; a Pi 4 host overrides this and
# takes the same firmware otherwise.
RPI_FIRMWARE_DTB ?= "bcm2712-rpi-5-b.dtb"

# Named rather than globbed: boot/ holds every model's start*.elf and
# fixup*.dat, and taking all of them would put ~20MB of other boards' firmware
# on a partition that is sized from its contents.
RPI_FIRMWARE_FILES ?= "start4.elf fixup4.dat"

# Deployed into a subdirectory of its own so qnx-host-disk can point at one
# path and get exactly the boot partition's contents -- the same shape the
# monorepo's qnx_host/images had.
RPI_FIRMWARE_DEPLOY_SUBDIR ?= "rpi-firmware"

do_deploy() {
	install -d ${DEPLOYDIR}/${RPI_FIRMWARE_DEPLOY_SUBDIR}

	for f in ${RPI_FIRMWARE_FILES} ${RPI_FIRMWARE_DTB}; do
		if [ ! -f "${S}/$f" ]; then
			bbfatal "$f is not in the firmware archive at ${SRCREV} -- check RPI_FIRMWARE_FILES and RPI_FIRMWARE_DTB against raspberrypi/firmware"
		fi
		install -m 0644 "${S}/$f" ${DEPLOYDIR}/${RPI_FIRMWARE_DEPLOY_SUBDIR}/
	done

	# The overlays directory goes across whole. The Pi's firmware looks for
	# overlay_map.dtb in it and falls back to loading a .dtbo directly when it
	# is absent -- which it is, upstream -- so `overlay_map.dtb not found` on the
	# console is expected rather than a sign of a missing file.
	if [ ! -d "${S}/overlays" ]; then
		bbfatal "no overlays/ in the firmware archive at ${SRCREV}"
	fi
	cp -a "${S}/overlays" ${DEPLOYDIR}/${RPI_FIRMWARE_DEPLOY_SUBDIR}/
}
addtask deploy after do_install before do_build

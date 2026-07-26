SUMMARY = "Flashable SD card image for the QNX hypervisor host on Raspberry Pi 5"
DESCRIPTION = "Assembles a FAT boot partition (Pi firmware, device tree, IFS) and \
a QNX6 data partition into a single MBR disk image that can be written straight \
to an SD card."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-host-disk-boot.build.in \
           file://qnx-host-disk-disk.cfg.in"

inherit qnx-disk

S = "${WORKDIR}"

# The IFS that goes on the boot partition. Also makes this recipe wait for that
# image to be deployed before reading it.
QNX_DISK_INSTALL = "qnx-host-image"
do_generate_diskfiles[depends] += "qnx-host-image:do_deploy"

# The data partition is a pre-built QNX6 filesystem image from qnx-host-data.
QNX_DISK_DATA_IMG = "${DEPLOY_DIR_IMAGE}/qnx-host-data.img"
do_compile[depends] += "qnx-host-data:do_deploy"

# Raspberry Pi firmware: start4.elf, fixup4.dat, the device tree and the overlays
# directory. Broadcom/RPi artifacts, not QNX ones, and not in the SDP -- they now
# come from the rpi-firmware recipe, which fetches them pinned and checksummed
# from raspberrypi/firmware. They used to be copied out of the hypervisor
# monorepo, which is why this recipe no longer needs QNX_PROJECT_SRC at all.
QNX_RPI_FIRMWARE = "${DEPLOY_DIR_IMAGE}/rpi-firmware"
do_generate_diskfiles[depends] += "rpi-firmware:do_deploy"

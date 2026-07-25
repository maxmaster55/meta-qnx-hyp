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
# directory. These are Broadcom/RPi artifacts, not QNX ones, and are not in the
# SDP -- they come from the project tree alongside the BSP.
QNX_RPI_FIRMWARE = "${QNX_PROJECT_SRC}/qnx_host/images"

python () {
    if not d.getVar('QNX_PROJECT_SRC'):
        raise bb.parse.SkipRecipe(
            "QNX_PROJECT_SRC is not set; it is needed for the Raspberry Pi "
            "firmware files that go on the boot partition.")
}

SUMMARY = "Flashable SD card image for the QNX hypervisor host on Raspberry Pi 5"
DESCRIPTION = "Assembles a FAT boot partition (Pi firmware, device tree, IFS) and \
a QNX6 data partition into a single MBR disk image that can be written straight \
to an SD card."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-host-disk-boot.build.in \
           file://qnx-host-disk-data.build.in \
           file://qnx-host-disk-disk.cfg.in"

inherit qnx-disk

S = "${WORKDIR}"

# The boot template and disk config are found by the class's naming convention
# (${QNX_DISK_NAME}-boot.build.in, ${QNX_DISK_NAME}-disk.cfg.in). A data
# partition is optional, so it has to be named explicitly.
QNX_DISK_DATA_TEMPLATE = "${S}/qnx-host-disk-data.build.in"

# The IFS that goes on the boot partition. Also makes this recipe wait for that
# image to be deployed before reading it.
QNX_DISK_INSTALL = "qnx-host-image"
do_generate_diskfiles[depends] += "qnx-host-image:do_deploy"

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

# ---------------------------------------------------------------------------
# Sizing
# ---------------------------------------------------------------------------
# Everything defaults to "auto": the boot partition is measured from the
# firmware and IFS that go into it, the data partition from its contents, and
# the disk from the partition images that were actually produced.
#
# Override any of them with a size when you want room to grow -- the data
# partition in particular is where you would do that, since "auto" sizes it to
# its initial contents and it is meant to be written to at runtime:
#
#   QNX_DISK_DATA_SIZE = "2G"
#   QNX_DISK_SIZE = "4G"
QNX_DISK_DATA_SIZE = "512M"

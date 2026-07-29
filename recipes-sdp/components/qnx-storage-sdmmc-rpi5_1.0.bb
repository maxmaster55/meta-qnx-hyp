SUMMARY = "SD card driver and the mount of the QNX6 data partition"
DESCRIPTION = "devb-sdmmc-bcm2712 plus the storage script that union-mounts the \
disk's second partition on /. This is what makes guests, Qt and writable state \
reachable: the IFS is RAM-resident and carries none of them."
LICENSE = "CLOSED"

inherit qnx-sdp-component

SRC_URI = "file://storage-server.sh"

# umount and sync are standalone binaries in QNX 8 rather than toybox links, and
# both are in the SDP, so the component resolves them the usual way.
QNX_BSP_ROOT ?= ""
QNX_COMPONENT_ROOTS = "${QNX_BSP_ROOT} ${QNX_TARGET}"

# Nothing here: the driver is a raw record below (it is unpacked during the
# build, not present at parse time) and umount/sync moved to qnx-fs-tools, where
# a guest can have them without pulling in an SD card driver.
QNX_COMPONENT_FILES = ""

# The block stack this driver needs, and the base runtime whose ksh the storage
# script is written in. DEPENDS gets their files into the installing image's
# sysroot; the image still lists them in QNX_IFS_INSTALL, because qnx-ifs
# walks that list deliberately rather than globbing the drop-in directory.
DEPENDS += "qnx-block qnx-base-runtime qnx-fs-tools"

# devb-sdmmc-bcm2712 is a raw record rather than a QNX_COMPONENT_FILES entry,
# and the distinction is worth stating because it is not obvious.
#
# A component resolves its files at *parse* time, which is what lets a wrong
# name fail the build naming the file. That only works for files that are
# already on disk when parsing happens -- the SDP, or a BSP tree named by
# QNX_BSP_ROOT. This driver comes from the BSP zip, which qnx-rpi5-bsp unpacks
# into the stage tree during the *build*: it does not exist yet when this recipe
# is parsed, and insisting on it here would make the component skip itself every
# time. Named bare instead, and left to mkifs, which resolves against the stage
# tree at image-build time and by then it is there.
#
# The mount cannot go in the startup script itself: that script is interpreted
# by procnto, which has no `if`, `while` or `$(...)`, and the retry below is not
# expressible there. devb-sdmmc is backgrounded and the card takes a moment to
# enumerate, so the partition does not exist when the script first looks.
QNX_IFS_EXTRA_ENTRIES = "\
/sbin/devb-sdmmc-bcm2712=devb-sdmmc-bcm2712\n\
[uid=0 gid=0 perms=0744] /proc/boot/.storage-server.sh=@QNX_IFS_SYSROOT@${QNX_STAGE_DIR}/storage/storage-server.sh\
"

# Deliberately no QNX_IFS_STARTUP_CMD. A component owns its *files*; the image
# owns the *sequence*. Startup fragments are appended at @QNX_IFS_STARTUP@,
# which on this board is after graphics and wifi -- far too late to mount the
# filesystem the applications live on. Where the SDMMC driver goes in the boot
# order is board wiring and belongs in the host image's template, next to the
# PCI and serial bring-up it has to sit between.
#
# sd0t179 is partition type 179 (QNX6) on the first disk, which is what
# qnx-disk.bbclass writes as the second partition.
QNX_STORAGE_PART ?= "/dev/sd0t179"
QNX_STORAGE_WAIT ?= "10"

# scarthgap has no UNPACKDIR, so file:// sources land directly in WORKDIR.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}/storage
	sed -e 's|@QNX_STORAGE_PART@|${QNX_STORAGE_PART}|g' \
	    -e 's|@QNX_STORAGE_WAIT@|${QNX_STORAGE_WAIT}|g' \
	    ${WORKDIR}/storage-server.sh > ${D}${QNX_STAGE_DIR}/storage/storage-server.sh
	chmod 0744 ${D}${QNX_STAGE_DIR}/storage/storage-server.sh
}


SUMMARY = "SD card driver and the mount of the QNX6 data partition"
DESCRIPTION = "devb-sdmmc-bcm2712 plus the storage script that union-mounts the \
disk's second partition on /. This is what makes guests, Qt and writable state \
reachable: the IFS is RAM-resident and carries none of them."
LICENSE = "CLOSED"

inherit qnx-sdp-component

SRC_URI = "file://storage-server.sh"

# devb-sdmmc-bcm2712 comes from the Raspberry Pi 5 BSP, which is an SDP package
# (the bsp-rpi5 feature), so ${QNX_TARGET} is where it normally is. QNX_BSP_ROOT
# is searched first and is empty by default -- it exists for a BSP built outside
# the SDP, and is a path rather than a reference to anybody's project tree.
#
# umount and sync are standalone binaries in QNX 8 rather than toybox links.
QNX_BSP_ROOT ?= ""
QNX_COMPONENT_ROOTS = "${QNX_BSP_ROOT} ${QNX_TARGET}"

QNX_COMPONENT_FILES = "\
    devb-sdmmc-bcm2712 \
    umount \
    sync \
"

# The block stack this driver needs, and the base runtime whose ksh the storage
# script is written in. DEPENDS gets their files into the installing image's
# sysroot; the image still lists them in QNX_IFS_INSTALL, because qnx-ifs
# walks that list deliberately rather than globbing the drop-in directory.
DEPENDS += "qnx-block qnx-base-runtime"

# The mount cannot go in the startup script itself: that script is interpreted
# by procnto, which has no `if`, `while` or `$(...)`, and the retry below is not
# expressible there. devb-sdmmc is backgrounded and the card takes a moment to
# enumerate, so the partition does not exist when the script first looks.
QNX_IFS_EXTRA_ENTRIES = "\
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


SUMMARY = "QNX6 data partition for the hypervisor host disk"
DESCRIPTION = "A bare QNX6 filesystem image that qnx-host-disk wraps as the \
disk's data partition. Carries writable state (/etc/ssh, /var, /scripts) and, \
when the guest layer is present, guest images and configuration injected via \
QNX_ROOTFS_EXTRA. Built by qnx-rootfs -- the single code path for every \
QNX6 filesystem image."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-host-data.build.in"

inherit qnx-rootfs

S = "${WORKDIR}"

QNX_ROOTFS_NAME = "qnx-host-data"
QNX_ROOTFS_TEMPLATE = "${S}/qnx-host-data.build.in"

QNX_ROOTFS_SIZE = "512M"
QNX_ROOTFS_MIN = "64M"
QNX_ROOTFS_INODES = "50000"

do_configure[noexec] = "1"

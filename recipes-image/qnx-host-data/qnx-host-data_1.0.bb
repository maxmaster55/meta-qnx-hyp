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

# wifi-service, for its two wpa_supplicant configurations only -- the binary
# itself goes in the IFS with the rest of the host image. They are here because
# the service rewrites both at runtime and an IFS is read-only, and this is what
# puts them in this recipe's sysroot for the template to name.
#
# hms is different: the whole binary is here, not just writable state -- see
# the comment on QNX_IFS_INSTALL in qnx-host-image_1.0.bb for why. This is what
# puts it in this recipe's sysroot for the template to place at /bin/hms.
QNX_ROOTFS_INSTALL = "wifi-service ssh-hostkeys hms"

QNX_ROOTFS_SIZE = "512M"
QNX_ROOTFS_MIN = "64M"
QNX_ROOTFS_INODES = "50000"

do_configure[noexec] = "1"

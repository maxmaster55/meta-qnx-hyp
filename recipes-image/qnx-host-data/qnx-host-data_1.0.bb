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

# The host's applications, whole -- not just their writable state. Every one of
# them was in qnx-host-image's QNX_IFS_INSTALL, where changing one binary cost a
# full image rebuild and a reflash of the card; see the comment on that variable
# for why the IFS is the wrong place for code that is iterated on. This is what
# puts them in this recipe's sysroot for the template below to place.
#
#   hms             /bin/hms
#   wifi-service    /bin/wifi_service, plus the two wpa_supplicant
#                   configurations it rewrites at runtime -- those were always
#                   here, because an IFS is read-only
#   motor-data-producer  /usr/bin/motor_data_producer and /etc/motor/config.json
#
# And the configuration, which is the other half of the same argument. A binary
# that can be replaced with an scp is not much use if the file that points it at
# a broker, a network or a set of guests still costs a reflash:
#
#   hms             /etc/hms.conf -- the broker address
#   qnx-host-conf   /etc/wpa_supplicant.conf -- the network PSK. Only that file;
#                   the component's two display files stay in the IFS, because
#                   host-graphics-start.sh runs before this partition is mounted
#   guest-launch    /scripts/start-guests.sh -- the launch policy. The recipe is
#                   still in QNX_IFS_INSTALL, because QNX_IFS_STARTUP_CMD only
#                   emits its boot-script line for members of that list; only
#                   the file moved
#
# ssh-hostkeys is not an application: it supplies the guests' pre-generated host
# keys for /var/ssh/known_hosts.
QNX_ROOTFS_INSTALL = "wifi-service ssh-hostkeys hms motor-data-producer \
                      qnx-host-conf guest-launch"

QNX_ROOTFS_SIZE = "512M"
QNX_ROOTFS_MIN = "64M"
QNX_ROOTFS_INODES = "50000"

do_configure[noexec] = "1"

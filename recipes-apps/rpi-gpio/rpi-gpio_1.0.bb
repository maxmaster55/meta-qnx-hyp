SUMMARY = "GPIO resource manager for Raspberry Pi (QNX)"
DESCRIPTION = "Second real application ported from the QNX hypervisor project \
(src/rpi-gpio), and the one that exercises the sysroot half of the staging \
contract: it installs a binary for the image AND a public header for other \
recipes to compile against."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-project-src

QNX_APP_SUBDIR = "src/rpi-gpio"

# Out-of-tree build -- cmake does not need to write into the working tree.
EXTERNALSRC_BUILD = "${WORKDIR}/build"

# The project's own install() rules already follow the QNX convention:
#   install(TARGETS rpi_gpio DESTINATION ${CMAKE_SYSTEM_PROCESSOR}/sbin)
#   install(FILES public/sys/rpi_gpio.h DESTINATION usr/include/sys)
# With the prefix set to the stage tree, that lands as
#   ${QNX_STAGE_DIR}/aarch64le/sbin/rpi_gpio   -> goes into images
#   ${QNX_STAGE_DIR}/usr/include/sys/rpi_gpio.h -> sysroot only
# and needs no install override here at all. Note that those rules are guarded
# by `if(DEFINED ENV{QNX_TARGET})`, which qnx-sdp.bbclass exports.

# A resource manager: starts early, and everything after it can rely on the
# device node being there.
#
# The waitfor is the part that actually matters. rpi_gpio is started with '&',
# so the startup script continues the instant it forks -- long before
# resmgr_attach() has registered /dev/gpio (see resmgr/main.c). Without this,
# an application at a later priority can still lose the race and fail to open
# the device. Priority orders the commands; waitfor is what makes the ordering
# mean something.
QNX_IFS_STARTUP_CMD = "rpi_gpio &"
QNX_IFS_STARTUP_PRIORITY = "300"
QNX_IFS_STARTUP_WAITFOR = "/dev/gpio"

# It links against login and secpol and drives hardware, so it runs as root and
# stays launchable by an unprivileged user -- the same treatment the project's
# guest build files give ping and traceroute.
QNX_IFS_ATTR[rpi_gpio] = "uid=0 gid=0 perms=4755"

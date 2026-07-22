SUMMARY = "GPIO resource manager for Raspberry Pi (QNX)"
DESCRIPTION = "Second real application ported from the QNX hypervisor project \
(src/rpi-gpio), and the one that exercises the sysroot half of the staging \
contract: it installs a binary for the image AND a public header for other \
recipes to compile against."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

# NO STANDALONE REPOSITORY YET.
#
# This application lives inside the QNX hypervisor monorepo, so there is nothing
# of its own to clone; it is built from a local checkout instead. Split it into
# its own repository and this becomes one line:
#
#     QNX_SRC_REPO = "git://github.com/you/rpi-gpio.git;protocol=https;branch=main"
#
# ...and QNX_SRC_LOCAL goes away. Until then QNX_PROJECT_SRC in local.conf says
# where the checkout is, and this recipe has no sstate (a working tree has no
# revision to hash) so it rebuilds every time.
QNX_SRC_LOCAL = "${QNX_PROJECT_SRC}"
QNX_SRC_SUBDIR = "src/rpi-gpio"

# cmake builds out of tree, so nothing is written into the checkout.
EXTERNALSRC_BUILD = "${WORKDIR}/build"


# The project's own install() rules already follow the QNX convention:
#   install(TARGETS rpi_gpio DESTINATION ${CMAKE_SYSTEM_PROCESSOR}/sbin)
#   install(FILES public/sys/rpi_gpio.h DESTINATION usr/include/sys)
# With the prefix set to the stage tree, that lands as
#   ${QNX_STAGE_DIR}/aarch64le/sbin/rpi_gpio   -> goes into images
#   ${QNX_STAGE_DIR}/usr/include/sys/rpi_gpio.h -> sysroot only
# and needs no install override here at all. Note that those rules are guarded
# by `if(DEFINED ENV{QNX_TARGET})`, which qnx-sdp.bbclass exports.

# Deliberately NOT started from the boot script: the project's own host image
# stages this and launches it by hand, and the boot script already pokes GPIO
# directly (gpio-rp1) before networking comes up. Auto-starting a resource
# manager that claims the same hardware is not a change to make without a board
# to test it on.
#
# When you do want it started at boot, this is the shape -- and both lines
# matter. rpi_gpio backgrounds itself, so the script would continue long before
# resmgr_attach() has registered /dev/gpio (resmgr/main.c); the priority orders
# the command, the waitfor is what makes the ordering mean anything:
#
#   QNX_IFS_STARTUP_CMD = "rpi-gpio &"
#   QNX_IFS_STARTUP_PRIORITY = "300"
#   QNX_IFS_STARTUP_WAITFOR = "/dev/gpio"

# The project's build file installs the binary under a dash, not the underscore
# the source tree uses. Matched here so anything invoking it by path still works.
QNX_IFS_DEST[rpi_gpio] = "/sbin/rpi-gpio"

# It links against login and secpol and drives hardware, so it runs as root and
# stays launchable by an unprivileged user -- the same treatment the project's
# guest build files give ping and traceroute.
QNX_IFS_ATTR[rpi_gpio] = "uid=0 gid=0 perms=4755"

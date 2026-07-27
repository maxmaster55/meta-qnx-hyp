SUMMARY = "SPI/ADC motor controller for the Raspberry Pi"
DESCRIPTION = "Reads eight ADC channels over SPI and drives the motor. Compiles \
against rpi-gpio's public header, which is why this recipe needs nothing more \
than a DEPENDS to find it -- the header arrives in the sysroot."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# Its own repository, split out of the hypervisor monorepo. Fetched rather than
# built in place, so this recipe has a revision to hash and therefore sstate --
# the working-tree build it replaced had neither and rebuilt every time.
#
# The repository root is the application: what used to be src/giga_spi_8adc inside the
# monorepo. If the split kept that nesting instead, add it back with
# QNX_SRC_SUBDIR = "src/giga_spi_8adc".
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time --
# every bitbake invocation, not just a fetch. Pin it for reproducible and offline
# builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor-controller.git;protocol=ssh;branch=main"

# sys/rpi_gpio.h, which arrives in the sysroot. The Makefile used to reach for
# it with a relative -I../rpi-gpio/resmgr/public -- a sibling directory in the
# monorepo, which worked only because the working tree was built in place. It
# now takes the path from EXTRA_CFLAGS below, so the DEPENDS is what makes this
# correct rather than lucky.
DEPENDS = "rpi-gpio"

# The Makefile writes into a build/ directory beside itself. That used to be
# implicit: building the working tree in place made EXTERNALSRC_BUILD default to
# <source>/build, so ${B} already pointed there. Fetching leaves B equal to S,
# and do_install then looked for the binaries one directory too high.
B = "${S}/build"

# The Makefile assigns CC and bakes -V into its own CFLAGS, so steer CC only --
# overriding CFLAGS wholesale would drop its -V and -O flags.
#
# EXTRA_CFLAGS carries the one include path it cannot resolve itself:
# rpi_gpio.c includes <sys/rpi_gpio.h>, which belongs to the rpi-gpio
# repository. That used to be a hardcoded -I../rpi-gpio/resmgr/public, which
# only worked while both lived in one source tree; now the header arrives in the
# sysroot via DEPENDS and the path is passed in.
EXTRA_OEMAKE = "CC='${CC}' EXTRA_CFLAGS='-I${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}'"

do_compile() {
	oe_runmake -C ${S} all
}

do_install() {
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin
	install -m 0755 ${B}/motor_controller \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/motor_controller

	# Read from the same directory as the binary, matching the layout the
	# project's own build file produces (/usr/bin/config.json).
	install -m 0644 ${S}/config.json \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/config.json
}

# Not started at boot, matching the project's own host image, which stages it
# and leaves it to be run by hand. A priority without a QNX_IFS_STARTUP_CMD
# would do nothing anyway.

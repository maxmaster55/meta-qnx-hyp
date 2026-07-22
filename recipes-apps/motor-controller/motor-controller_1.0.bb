SUMMARY = "SPI/ADC motor controller for the Raspberry Pi"
DESCRIPTION = "Reads eight ADC channels over SPI and drives the motor. Compiles \
against rpi-gpio's public header, which is why this recipe needs nothing more \
than a DEPENDS to find it -- the header arrives in the sysroot."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# NO STANDALONE REPOSITORY YET.
#
# This application lives inside the QNX hypervisor monorepo, so there is nothing
# of its own to clone; it is built from a local checkout instead. Split it into
# its own repository and this becomes one line:
#
#     QNX_SRC_REPO = "git://github.com/you/giga_spi_8adc.git;protocol=https;branch=main"
#
# ...and QNX_SRC_LOCAL goes away. Until then QNX_PROJECT_SRC in local.conf says
# where the checkout is, and this recipe has no sstate (a working tree has no
# revision to hash) so it rebuilds every time.
QNX_SRC_LOCAL = "${QNX_PROJECT_SRC}"
QNX_SRC_SUBDIR = "src/giga_spi_8adc"

# sys/rpi_gpio.h. The upstream Makefile also reaches for it with a relative
# -I../rpi-gpio/resmgr/public, which happens to work when building the working
# tree in place; the DEPENDS is what makes it correct rather than lucky.
DEPENDS = "rpi-gpio"

# The Makefile assigns CC and bakes -V into its own CFLAGS, so steer CC only.
EXTRA_OEMAKE = "CC='${CC}'"

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

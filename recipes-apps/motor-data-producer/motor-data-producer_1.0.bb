SUMMARY = "Motor data producer: drives the SPI/ADC motor hardware and publishes to shared memory"
DESCRIPTION = "Reads the eight ADC channels over SPI, runs the control loop, and \
writes the result into the shared-memory ring that motor_ai_client, motor_recorder \
and shm_chunker consume. Its two headers are staged as well: they define the wire \
format and the shared-memory layout, and are the contract between all of them."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

# This is what used to be the motor-controller recipe, and before that
# src/giga_spi_8adc in the monorepo. Same application: renamed upstream, and
# switched from a hand-written Makefile to CMake.
#
# It also takes over the name of the recipe that built Mintharah/SPI-Stm32-QNX,
# which is retired -- that was a different program reading an STM32 over SPI,
# and this replaces it. The two motor_wire.h/motor_shm.h copies were identical,
# so nothing that compiles against them changes.
QNX_SRC_REPO = "git://github.com/PM-Maestro-ITI-GP-Org/motor-data-producer.git;protocol=https;branch=main"

# sys/rpi_gpio.h, which arrives in the sysroot.
#
# The CMakeLists reaches for it with
#   include_directories(../../rpi-gpio/resmgr/public)
# -- a sibling directory that only exists inside the monorepo, and not even at
# that depth. It is left alone rather than patched: a non-existent include
# directory is not an error to cmake, just a -I that matches nothing. The real
# path is added through CFLAGS below, which is what makes this correct rather
# than lucky.
DEPENDS = "rpi-gpio"

# Folded into CMAKE_C_FLAGS_INIT by qnx-cmake's generated toolchain file, which
# is what makes it survive a reconfigure.
CFLAGS:append = " -I${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}"

# The CMakeLists declares the executable and nothing else -- no install() rules
# -- so there is nothing for OECMAKE_INSTALL_PREFIX to act on and the binary is
# placed by hand.
#
# The binary follows the CMake target name, so what used to be motor_controller
# is now motor_data_producer -- anything on a board still calling the old name
# has to be updated.
#
# config.json goes to /etc/motor/, not beside the binary. That is a change from
# the old build, and it comes from the source rather than from preference:
#
#     #define DEFAULT_CONFIG_PATH "/etc/motor/config.json"
#
# so shipping it there is what makes `motor_data_producer` work with no
# arguments. The old recipe put it at /usr/bin/config.json, which this binary
# would not find.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin
	install -m 0755 ${B}/motor_data_producer \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/usr/bin/motor_data_producer

	install -d ${D}${QNX_STAGE_DIR}/motor-data-producer
	install -m 0644 ${S}/config.json ${D}${QNX_STAGE_DIR}/motor-data-producer/

	# Headers for motor_ai_client and motor_recorder, which compile against
	# them. Sysroot only -- they are a build-time contract and have no business
	# in an image.
	install -d ${D}${QNX_STAGE_INCLUDEDIR}
	install -m 0644 ${S}/motor_wire.h ${D}${QNX_STAGE_INCLUDEDIR}/
	install -m 0644 ${S}/motor_shm.h  ${D}${QNX_STAGE_INCLUDEDIR}/
}

# The binary is harvested automatically -- it is in the processor tree's
# usr/bin, which mkifs searches. config.json is not: /etc/motor is on no search
# path, so it is staged outside that tree and placed by an explicit record.
QNX_IFS_EXTRA_ENTRIES = "\
/etc/motor/config.json=@QNX_IFS_ROOT@/motor-data-producer/config.json\
"

# Not started from either boot script. It wants the SPI bus, and which of the
# host and the guest owns SPI0 is a runtime decision -- the RP1's SPI and GPIO
# windows are passed through to the guest by the .qvmconf, so only one side may
# drive them at a time.

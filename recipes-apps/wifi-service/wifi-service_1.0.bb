SUMMARY = "Gets WiFi credentials onto a board with no keyboard, using a phone"
DESCRIPTION = "A board with no screen has no way to be told a WiFi password. \
This joins a hotspot the phone is running, under a name and password compiled \
in on both sides, connects back to the phone and asks it for the real network's \
credentials -- then writes a wpa_supplicant configuration from the answer and \
associates with that network instead. It is a WiFi client throughout: it never \
becomes an access point."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# Its own repository, split out of the hypervisor monorepo (src/wifi/wifi_service).
# The repository root is the application: what used to be the wifi_service/
# subdirectory. If the split keeps the parent instead, add it back with
#
#     QNX_SRC_SUBDIR = "wifi_service"
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time.
# Pin it for reproducible and offline builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/wifi-service.git;protocol=ssh;branch=main"

QNX_SRC_REV = "f042874764e4d940a977b9d9b9e69bac2da87b66"

# This is a HOST recipe, not a guest one, and that is not a packaging
# preference. bcm0 is the Pi's own CYW43455 radio: the host owns it, the driver
# is loaded on the host's io-sock boot line, and its firmware comes from the
# SDP's etc/firmware/bcm43455_firmware_pkg. A guest under qvm has virtio
# interfaces and no radio at all, so this would start there, find no bcm0 and
# have nothing to configure.

# The upstream Makefile assigns CC := qcc and bakes -Vgcc_ntoaarch64le into its
# own CFLAGS, which is the right variant. Passing CC on the command line
# overrides the assignment -- make gives command-line variables precedence --
# and routes the build through the compiler this class configured.
#
# CFLAGS is deliberately not passed: the Makefile uses simple assignment, so
# overriding it would take -Vgcc_ntoaarch64le with it and the build would target
# the host. Same reasoning as shm-chunker.
EXTRA_OEMAKE = "CC='${CC}'"

do_compile() {
	oe_runmake -C ${S}
}

do_install() {
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${S}/wifi_service ${D}${QNX_STAGE_BINDIR}/wifi_service
}

# Not started at boot, and not by accident. The host image already associates
# with a configured network from .wifi-start.sh, using the wpa_supplicant
# configuration that qnx-host-conf carries. Starting this as well would have two
# things competing for one radio: this one takes bcm0 down to raise a hotspot,
# which would drop the link the board is being administered over.
#
# It is a provisioning tool -- run it from the console on a board whose network
# is not yet configured, or whose configured network has moved:
#
#     wifi_service
#
# then start a hotspot on the phone named QNX_wifi with the password 123456789,
# and have the companion app listening on TCP 9999. Both the name and the
# password are compiled in (DEFAULT_SSID/DEFAULT_PASS), so both ends have to
# agree and neither can be changed without a rebuild. That is also a
# well-known password on an open-by-design network -- it is only up for the
# seconds it takes to hand over the real credentials, but it is up.
#
# The state machine, because the direction of every connection here is the
# opposite of what the source tree's README describes:
#
#   TRY_REAL      wpa_supplicant -D qwdi with wpa_supplicant_real.conf if it
#                 exists; 25s to associate. Success lights an LED on GPIO 17.
#   TRY_DEFAULT   no real config, or it failed -- write the default one and
#                 join the PHONE's hotspot, as a client; 15s.
#   ON_DEFAULT    dhcpcd -b bcm0, take the phone's address from the gateway,
#                 connect OUT to it on TCP 9999, send {"type":"rpi_ready"},
#                 read one line of {"ssid","password"}, write
#                 wpa_supplicant_real.conf, and go back to TRY_REAL.
#
# Neither of those is what the README says. It describes hostapd, an access
# point called QNX_Config, a built-in DHCP server on UDP/67 and a listener on
# TCP 8888. The code contains no hostapd, no bind/listen/accept at all, and
# runs dhcpcd -- a DHCP *client*. Read the code, not the README.
#
# It ships no configuration, and that is correct rather than an omission: it
# writes both wpa_supplicant configurations itself with fopen(..., "w"). The
# wifi_conf/ directory next to it in the monorepo is sample output, not input.
#
# What it does need is for /etc/wifi to exist and be WRITABLE. On the host that
# rules out the IFS, which is read-only -- so the directory comes from the data
# partition, created by qnx-host-data.build.in for exactly this. Without it both
# fopen calls fail silently (write_default_conf just returns on a NULL FILE*)
# and the service cycles between states forever without ever associating.
#
# It drives wpa_supplicant -D qwdi, which is in the SDP and is the same driver
# .wifi-start.sh uses.

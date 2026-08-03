SUMMARY = "WiFi provisioning service: a config access point and a TCP config port"
DESCRIPTION = "Brings the board up on its own access point so a phone can hand \
it credentials for the real network. It serves DHCP itself on UDP/67 rather \
than pulling in dnsmasq, and listens on TCP 8888 for a {\"ssid\",\"password\"} \
document -- at which point it writes a new wpa_supplicant configuration and \
re-associates with that network."
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

# PLACEHOLDER -- replace with the repository's first commit.
#
# It is pinned rather than left at the ${AUTOREV} default for a reason that
# matters before the repository exists: AUTOREV makes bitbake `git ls-remote`
# the repository at *parse* time, on every invocation. Against a repository that
# is not there yet that is a parse error, and a parse error in one recipe halts
# parsing for the whole tree -- so an unfetchable recipe sitting in a layer would
# stop `bitbake qnx-host-disk` from building anything at all.
#
# With a fixed revision nothing is fetched until this recipe is actually built,
# and the failure is then this recipe's alone and says exactly what is wrong.
QNX_SRC_REV = "0000000000000000000000000000000000000000"

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
# is not yet configured, or whose configured network is not present:
#
#     wifi_service
#
# then connect a phone to the config access point and send the credentials to
# port 8888.
#
# It ships no configuration, and that is correct rather than an omission: it
# writes both wpa_supplicant configurations itself, with fopen(..., "w") --
# /etc/wifi/wpa_supplicant_default.conf for the provisioning AP and
# /etc/wifi/wpa_supplicant_real.conf from what the phone sends. The wifi_conf/
# directory next to it in the monorepo is sample output, not input.
#
# What it does need is for /etc/wifi to exist and be WRITABLE. On the host that
# rules out the IFS, which is read-only -- so the directory comes from the data
# partition, created by qnx-host-data.build.in for exactly this. Without it both
# fopen calls fail silently (write_default_conf returns on a NULL FILE*) and the
# service runs, accepts a connection and never associates.
#
# It drives wpa_supplicant -D qwdi, which is in the SDP and is the same driver
# .wifi-start.sh uses. The README in the source tree describes a hostapd-based
# design; the code does not use hostapd at all.

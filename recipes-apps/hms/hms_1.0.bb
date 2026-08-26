SUMMARY = "Hypervisor Management System: guest lifecycle and OTA, driven over MQTT"
DESCRIPTION = "Runs on the QNX host. Discovers the guests under /guests, starts \
and stops them through qvm, and takes commands from an MQTT broker so a GUI \
client elsewhere can drive the board. OTA packages move by scp, and per-guest \
work is done over ssh -- both as commands rather than linked libraries."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/hms.git;protocol=ssh;branch=main"

# libmosquitto, built by this tree. It is the only library this links beyond
# libc -- ssh and scp are exec'd, not linked, so there is no libssh here, and
# nothing calls cJSON despite what the monorepo Makefile used to pass.
DEPENDS = "mosquitto"

# A host recipe, and not by preference. It manages guests: it reads /guests,
# execs qvm, and talks to /dev/qvm. Inside a guest there is nothing for it to
# manage.

# The upstream Makefile takes the compiler from CC and needs only to be told
# where libmosquitto is. Those two paths resolve to the same directory here --
# mosquitto stages its headers into QNX_STAGE_INCLUDEDIR and its library into
# QNX_STAGE_LIBDIR -- but they stay separate variables because upstream has no
# reason to assume that.
EXTRA_OEMAKE = "\
    CC='${CC}' \
    MQTT_INCDIR='${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}' \
    MQTT_LIBDIR='${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}' \
"
EXTRA_OEMAKE[vardepsexclude] = "RECIPE_SYSROOT"

# The Makefile builds into build/ beside the sources.
B = "${S}/build"

do_compile() {
	oe_runmake -C ${S}
}

do_install() {
	# The binary is staged outside the processor tree (${QNX_STAGE_DIR}/hms,
	# not ${QNX_STAGE_BINDIR}) so the automatic IFS pass does not pick it up --
	# see qnx-host-image_1.0.bb, which reads it from here into the data
	# partition instead. hms used to be QNX_IFS_INSTALL: every fix to it meant
	# rebuilding and reflashing the whole read-only boot image to change one
	# binary. On the writable partition, a new hms is `scp build/hms
	# root@host:/bin/hms` -- no image rebuild, no reflash.
	install -d ${D}${QNX_STAGE_DIR}/hms
	install -m 0755 ${B}/hms ${D}${QNX_STAGE_DIR}/hms/hms
	install -m 0644 ${S}/config/hms.conf ${D}${QNX_STAGE_DIR}/hms/
}

# No QNX_IFS_EXTRA_ENTRIES. hms.conf goes on the data partition beside the
# binary, placed by qnx-host-data.build.in -- it used to be the one part of hms
# that still cost an image rebuild and a reflash to change, which is exactly
# backwards for the file holding the broker address. It is read by hms itself,
# long after .storage-server.sh has union-mounted that partition over /, so
# /etc/hms.conf resolves to this copy either way.

# Not started at boot. It is a management agent that reaches an MQTT broker over
# the network and can start and stop guests, so when it runs is a decision, not
# a default -- run it from the console or over ssh once the board is up.
#
# Two things it needs that this recipe cannot supply, both deliberately:
#
#   The private ssh keys hms.conf names -- /root/.ssh/id_ed25519 for guests and
#   /.ssh/id_ed25519 for the OTA server. Keys belong on a board, not in a layer;
#   the repository carries only the public half.
#
#   A broker it can reach. The address is in /etc/hms.conf, on the data
#   partition -- so changing it on a board that is already flashed is an edit in
#   place, or `scp hms.conf root@host:/etc/hms.conf`, rather than a rebuild.

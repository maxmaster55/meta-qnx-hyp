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
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${B}/hms ${D}${QNX_STAGE_BINDIR}/hms

	# /etc/hms.conf is where the program looks. Staged outside the processor
	# tree so the automatic IFS pass leaves it alone -- /etc is on no mkifs
	# search path, so it is placed by the record below instead.
	install -d ${D}${QNX_STAGE_DIR}/hms
	install -m 0644 ${S}/config/hms.conf ${D}${QNX_STAGE_DIR}/hms/
}

QNX_IFS_EXTRA_ENTRIES = "\
/etc/hms.conf=@QNX_IFS_ROOT@/hms/hms.conf\
"

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
#   A broker it can reach. The address is in /etc/hms.conf, which is in the IFS
#   and therefore read-only -- edit it here and rebuild, or point the config at
#   a copy on the data partition if it needs to change on a running board.

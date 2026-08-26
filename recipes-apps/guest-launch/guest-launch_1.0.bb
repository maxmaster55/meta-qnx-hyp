SUMMARY = "Start every discovered guest under qvm, at host boot"
DESCRIPTION = "A /scripts script that walks /guests, launches one qvm per \
directory holding a .qvmconf -- the same cwd, stdio and log layout hms's \
guest_start() uses -- and skips guests that are already up. This is what makes \
the guests start at host startup rather than waiting for a Start command over \
MQTT; hms starts later in the boot and adopts what it finds running."
LICENSE = "CLOSED"

inherit qnx-sdp

# A script this layer owns, not an application fetched from anywhere: no
# qnx-src, nothing to configure, compile or fetch. ssh-hostkeys is the precedent
# for a recipe whose whole output is made here.
SRC_URI = "file://start-guests.sh"

S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# ---------------------------------------------------------------------------
# /scripts is not on any mkifs search path (see adding-a-recipe.md), so the
# automatic pass cannot place this and would warn about it forever. Staged
# privately instead and named explicitly below -- the same shape
# qnx-host-conf uses for host-graphics-start.sh.
# ---------------------------------------------------------------------------
QNX_IFS_AUTO_ENTRIES = "0"

QNX_GUEST_LAUNCH_DIR = "${QNX_STAGE_DIR}/guest-launch"

do_install() {
	install -d ${D}${QNX_GUEST_LAUNCH_DIR}
	install -m 0755 ${WORKDIR}/start-guests.sh ${D}${QNX_GUEST_LAUNCH_DIR}/start-guests.sh
}

# No QNX_IFS_EXTRA_ENTRIES: the script itself is on the DATA PARTITION, placed
# at /scripts/start-guests.sh by qnx-host-data.build.in.
#
# This recipe still belongs to QNX_IFS_INSTALL all the same, and the split is
# the point. QNX_IFS_STARTUP_CMD below is a LINE IN THE BOOT SCRIPT, and that
# line is only emitted for recipes the image installs -- so the wiring has to
# stay in the IFS. The file it names does not: the boot script reaches this
# marker after .storage-server.sh has union-mounted the partition over /, which
# is what makes /scripts/start-guests.sh resolve to the copy on the disk.
#
# Which is the copy worth having. Guest launch policy -- what qvm is given, how
# stdio is cut off, what is skipped -- is the kind of thing that gets changed
# while a board is on the bench, and in the IFS every change was a rebuild and a
# reflash. Here it is an edit in place.

# ---------------------------------------------------------------------------
# Started from the boot script, which puts this line at @QNX_IFS_STARTUP@ --
#
#   after the data partition mount  /guests only exists once .storage-server.sh
#                                   has run; earlier, there is nothing to launch
#   after graphics                   the template's customize_startup.sh ends in
#                                   `waitfor /dev/screen` and is not backgrounded,
#                                   so anything at this marker follows Screen by
#                                   construction -- see the comment block there
#                                   for why a guest must never beat it
#   before hms                       hms's discoverer adopts running guests it did
#                                   not start ("started outside HMS -- adopted"),
#                                   so the manager comes up to a board already
#                                   doing its job rather than to an empty list
#
# No QNX_IFS_STARTUP_WAITFOR: the script backgrounds one qvm per guest and
# returns, and there is no single device whose appearance means "done" -- each
# guest publishes its own /dev/qvm/<system> when it is up, named by its own
# config. Waiting on those from the boot script would hardcode guest names into
# the image.
#
# Not gated on QNX_HOST_RECORD_GUESTS-style configuration: which guests exist is
# decided by which directories the data partition carries, and discovery is the
# same rule hms applies. To keep a guest out of autostart, remove it from the
# disk -- not from this script.
# ---------------------------------------------------------------------------
QNX_IFS_STARTUP_CMD = "/scripts/start-guests.sh"

# Only ever a host thing: it reads /guests and execs /sbin/qvm, both of which
# are the hypervisor side of the board. In a guest there is nothing to manage
# and no qvm to run.

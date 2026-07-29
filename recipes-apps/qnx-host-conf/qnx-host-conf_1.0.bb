SUMMARY = "Host configuration files: display and wifi"
DESCRIPTION = "Stages the repo-versioned Screen display configuration and wifi \
configuration for the hypervisor host, along with the scripts that start them. \
These are configuration rather than code, so they are staged verbatim."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# Its own repository, split out of the hypervisor monorepo. Fetched rather than
# built in place, so this recipe has a revision to hash and therefore sstate --
# the working-tree build it replaced had neither and rebuilt every time.
#
# The repository root is the application: what used to be conf inside the
# monorepo. If the split kept that nesting instead, add it back with
# QNX_SRC_SUBDIR = "conf".
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time --
# every bitbake invocation, not just a fetch. Pin it for reproducible and offline
# builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/qnx-host-conf.git;protocol=ssh;branch=main"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# ---------------------------------------------------------------------------
# These land outside the mkifs search path (/lib/graphics, /scripts, /etc are
# not searched by bare name), so they are staged into a private directory and
# placed explicitly. QNX_IFS_AUTO_ENTRIES is off because the automatic pass
# would warn about every one of them.
# ---------------------------------------------------------------------------
QNX_IFS_AUTO_ENTRIES = "0"

QNX_HOST_CONF_DIR = "${QNX_STAGE_DIR}/host-conf"

# The wifi supplicant configuration is the one file the repository does not
# carry: it holds the network's PSK, and a key committed once stays in git
# history even after the file is deleted. The repository ships
# wpa_supplicant.conf.sample instead, with psk="CHANGEME".
#
# Point this at a real wpa_supplicant.conf to build an image that associates:
#
#     QNX_HOST_CONF_WIFI = "/path/to/wpa_supplicant.conf"
#
# The output of `wpa_passphrase "SSID" "pass"` is enough. That command emits a
# network block and nothing else, and the missing globals are added below --
# see QNX_HOST_CONF_WIFI_GLOBALS for why that is not a convenience.
#
# Left unset, the sample is installed and the image builds and boots -- wifi
# simply does not associate, which the build warns about rather than failing,
# because plenty of work on this image has nothing to do with wifi.
QNX_HOST_CONF_WIFI ?= ""

# The settings that have to be in the file but are not in what wpa_passphrase
# writes, taken from QNX's own reference (boot/wpa_supplicant.conf in the CTI
# repository).
#
# ctrl_interface is the one that matters. wpa_supplicant only creates its control
# socket if this is set, and without the socket wpa_cli cannot attach at all:
#
#     Failed to connect to non-global ctrl_ifname: bcm0  error: No such file or directory
#
# which reads like the supplicant is not running when it is running fine -- you
# simply have no way to ask it anything, including whether it associated.
#
# Prepended only when the supplied file has no ctrl_interface of its own, so a
# hand-written complete configuration is still used exactly as written.
QNX_HOST_CONF_WIFI_GLOBALS ?= "\
ctrl_interface=/var/run/wpa_supplicant\n\
eapol_version=1\n\
ap_scan=1\n\
fast_reauth=1\n"

# An absolute path outside the recipe is invisible to task signatures, so a
# change to the file it names would not rebuild anything without this.
do_install[vardeps] += "QNX_HOST_CONF_WIFI QNX_HOST_CONF_WIFI_GLOBALS"
do_install[file-checksums] += "${@'%s:%s' % (d.getVar('QNX_HOST_CONF_WIFI'), os.path.exists(d.getVar('QNX_HOST_CONF_WIFI') or '/nonexistent')) if d.getVar('QNX_HOST_CONF_WIFI') else ''}"

do_install() {
	install -d ${D}${QNX_HOST_CONF_DIR}
	install -m 0644 ${S}/display/graphics-host-rpi5.conf ${D}${QNX_HOST_CONF_DIR}/
	install -m 0755 ${S}/display/host-graphics-start.sh  ${D}${QNX_HOST_CONF_DIR}/

	# Two things this recipe used to stage and no longer does.
	#
	# The guest-side Screen configurations are qnx-guest-conf's now
	# (meta-qnx-guest): a guest that wanted them had to install this whole
	# component to reach them, and this component carries wpa_supplicant.conf --
	# so the guest image ended up with the board's wifi PSK inside it.
	#
	# qwdi_wifi.conf and wifi-start.sh are the host image's now, written inline
	# in qnx-host.build.in. The driver configuration is board data -- the SDIO
	# controller's base address and IRQ -- and belongs beside the SPI and SDMMC
	# addresses rather than in a repository of things a user might edit. The
	# copy that lived here had neither value, which is why the radio never came
	# up; the script here also mounted the driver by hand and did not wait for
	# it, both of which the SDP's own start_net.sh does differently.
	#
	# What stays is the one file that genuinely is configuration: the network
	# credentials below.

	local wpa_src="${S}/wifi/wpa_supplicant.conf.sample"

	if [ -n "${QNX_HOST_CONF_WIFI}" ]; then
		if [ ! -f "${QNX_HOST_CONF_WIFI}" ]; then
			bbfatal "QNX_HOST_CONF_WIFI points at ${QNX_HOST_CONF_WIFI}, which does not exist"
		fi
		wpa_src="${QNX_HOST_CONF_WIFI}"
	else
		bbwarn "QNX_HOST_CONF_WIFI is not set, so the placeholder wpa_supplicant.conf.sample is being installed -- the board will not join a network. Set it to a real wpa_supplicant.conf in local.conf."
	fi

	# Composed rather than copied, so that `wpa_passphrase "SSID" "pass" > file`
	# is a complete answer. That command writes a network block and no globals,
	# and a supplicant started against it runs, associates or does not, and
	# offers no control socket to ask which -- so the obvious way to produce this
	# file produces one that cannot be debugged.
	#
	# A file that already sets ctrl_interface is taken exactly as written; the
	# assumption is that whoever wrote one knew what the rest should be too.
	if grep -q '^[[:space:]]*ctrl_interface=' "$wpa_src"; then
		install -m 0600 "$wpa_src" ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
	else
		bbnote "adding the standard globals to wpa_supplicant.conf; $wpa_src sets no ctrl_interface"
		printf '%b\n' "${QNX_HOST_CONF_WIFI_GLOBALS}" > ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
		cat "$wpa_src" >> ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
		chmod 0600 ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
	fi
}

# Absolute sources, because these have no bare-name search path to be found on.
#
# @QNX_IFS_ROOT@ (not ${...}) is deliberate: it is expanded by the *image*
# recipe when the fragment is merged, since the path depends on which image
# installs this and is unknowable here. A ${QNX_IFS_ROOT} would expand to
# nothing at parse time.
#
# perms=0600 on wpa_supplicant.conf: it holds the network PSK. That is thin
# protection on an image anyone holding the SD card can read, but there is no
# reason to make it worse than the file it came from.
QNX_IFS_EXTRA_ENTRIES = "\
/lib/graphics/drm-rpi5/graphics-host-rpi5.conf=@QNX_IFS_ROOT@/host-conf/graphics-host-rpi5.conf\n\
[perms=0755] /scripts/host-graphics-start.sh=@QNX_IFS_ROOT@/host-conf/host-graphics-start.sh\n\
[perms=0600] /etc/wpa_supplicant.conf=@QNX_IFS_ROOT@/host-conf/wpa_supplicant.conf\
"

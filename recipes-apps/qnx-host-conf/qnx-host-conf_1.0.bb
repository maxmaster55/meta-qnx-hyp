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
# Left unset, the sample is installed and the image builds and boots -- wifi
# simply does not associate, which the build warns about rather than failing,
# because plenty of work on this image has nothing to do with wifi.
QNX_HOST_CONF_WIFI ?= ""

# An absolute path outside the recipe is invisible to task signatures, so a
# change to the file it names would not rebuild anything without this.
do_install[vardeps] += "QNX_HOST_CONF_WIFI"
do_install[file-checksums] += "${@'%s:%s' % (d.getVar('QNX_HOST_CONF_WIFI'), os.path.exists(d.getVar('QNX_HOST_CONF_WIFI') or '/nonexistent')) if d.getVar('QNX_HOST_CONF_WIFI') else ''}"

do_install() {
	install -d ${D}${QNX_HOST_CONF_DIR}
	install -m 0644 ${S}/display/graphics-host-rpi5.conf ${D}${QNX_HOST_CONF_DIR}/
	install -m 0755 ${S}/display/host-graphics-start.sh  ${D}${QNX_HOST_CONF_DIR}/
	install -m 0644 ${S}/wifi/qwdi_wifi.conf             ${D}${QNX_HOST_CONF_DIR}/
	install -m 0755 ${S}/wifi/wifi-start.sh              ${D}${QNX_HOST_CONF_DIR}/

	if [ -n "${QNX_HOST_CONF_WIFI}" ]; then
		if [ ! -f "${QNX_HOST_CONF_WIFI}" ]; then
			bbfatal "QNX_HOST_CONF_WIFI points at ${QNX_HOST_CONF_WIFI}, which does not exist"
		fi
		install -m 0600 "${QNX_HOST_CONF_WIFI}" ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
	else
		bbwarn "QNX_HOST_CONF_WIFI is not set, so the placeholder wpa_supplicant.conf.sample is being installed -- the board will not join a network. Set it to a real wpa_supplicant.conf in local.conf."
		install -m 0600 ${S}/wifi/wpa_supplicant.conf.sample ${D}${QNX_HOST_CONF_DIR}/wpa_supplicant.conf
	fi
}

# Absolute sources, because these have no bare-name search path to be found on.
#
# @QNX_IFS_ROOT@ (not ${...}) is deliberate: it is expanded by the *image*
# recipe when the fragment is merged, since the path depends on which image
# installs this and is unknowable here. A ${QNX_IFS_ROOT} would expand to
# nothing at parse time.
QNX_IFS_EXTRA_ENTRIES = "\
/lib/graphics/drm-rpi5/graphics-host-rpi5.conf=@QNX_IFS_ROOT@/host-conf/graphics-host-rpi5.conf\n\
[perms=0755] /scripts/host-graphics-start.sh=@QNX_IFS_ROOT@/host-conf/host-graphics-start.sh\n\
/etc/qwdi_wifi.conf=@QNX_IFS_ROOT@/host-conf/qwdi_wifi.conf\n\
/etc/wpa_supplicant.conf=@QNX_IFS_ROOT@/host-conf/wpa_supplicant.conf\n\
[perms=0755] /scripts/wifi-start.sh=@QNX_IFS_ROOT@/host-conf/wifi-start.sh\
"

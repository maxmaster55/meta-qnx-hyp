SUMMARY = "Host configuration files: display and wifi"
DESCRIPTION = "Stages the repo-versioned Screen display configuration and wifi \
configuration for the hypervisor host, along with the scripts that start them. \
These are configuration rather than code, so they are staged verbatim."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-project-src

QNX_APP_SUBDIR = "conf"

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

do_install() {
	install -d ${D}${QNX_HOST_CONF_DIR}
	install -m 0644 ${S}/display/graphics-host-rpi5.conf ${D}${QNX_HOST_CONF_DIR}/
	install -m 0755 ${S}/display/host-graphics-start.sh  ${D}${QNX_HOST_CONF_DIR}/
	install -m 0644 ${S}/wifi/qwdi_wifi.conf             ${D}${QNX_HOST_CONF_DIR}/
	install -m 0644 ${S}/wifi/wpa_supplicant.conf        ${D}${QNX_HOST_CONF_DIR}/
	install -m 0755 ${S}/wifi/wifi-start.sh              ${D}${QNX_HOST_CONF_DIR}/
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

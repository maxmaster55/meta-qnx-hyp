SUMMARY = "Frame router: shared-memory framebuffer bridge between host and guest"
DESCRIPTION = "Builds fb_host (runs on the hypervisor host, links -lhyp -lscreen) and \
fb_guest/fb_test (run inside a guest). The guest side is built here too so a guest \
image recipe can install the same recipe."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-project-src

QNX_APP_SUBDIR = "src/frame_router"

# Its Makefile hardcodes QCC = qcc and builds the -V variant into each rule, so
# only the variant needs steering. CFLAGS is left alone deliberately: it uses
# simple assignment, so overriding it would drop the flags each rule adds.
EXTRA_OEMAKE = "QCC='${CC}' GUEST_VARIANT='${QNX_VARIANT}' HOST_VARIANT='${QNX_VARIANT}'"

do_compile() {
	oe_runmake -C ${S} all
}

do_install() {
	install -d ${D}${QNX_STAGE_BINDIR}
	install -m 0755 ${B}/fb_host  ${D}${QNX_STAGE_BINDIR}/fb_host
	install -m 0755 ${B}/fb_guest ${D}${QNX_STAGE_BINDIR}/fb_guest
	install -m 0755 ${B}/fb_test  ${D}${QNX_STAGE_BINDIR}/fb_test
}

# Started by the host's graphics script rather than directly from the boot
# script, since it needs Screen up first.

SUMMARY = "virtio-gpu virtual device for the QNX hypervisor"
DESCRIPTION = "The host-side vdev qvm loads to give a guest a GPU: it hands the \
guest's virtio-gpu command stream to virglrenderer and presents the result \
through Screen."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-project-src

QNX_APP_SUBDIR = "src/gpu/vdev-virtio-gpu"

DEPENDS = "virglrenderer libepoxy"

# Upstream reads VIRGL and STAGE from a paths.txt that build-deps.sh generates,
# pointing into the source tree. Passing them on the command line instead means
# nothing has to be written into the working tree, and the values come from what
# DEPENDS actually staged.
#
# VIRGL is used for -I$(VIRGL)/src -I$(VIRGL)/build-qnx/src and to find the
# library, so it still points at the virglrenderer source; STAGE supplies
# libscreen and libhypS.
QNX_VIRGL_SRC = "${QNX_PROJECT_SRC}/src/gpu/virglrenderer"

EXTRA_OEMAKE = "VIRGL='${QNX_VIRGL_SRC}' STAGE='${WORKDIR}/stage'"

do_compile:prepend() {
	# libscreen and libhypS come from the SDP; the virglrenderer import library
	# comes from the sysroot rather than the source tree's build-qnx.
	install -d ${WORKDIR}/stage/lib
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so   ${WORKDIR}/stage/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so.1 ${WORKDIR}/stage/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/lib/libhypS.a          ${WORKDIR}/stage/lib/
	cp -Pf ${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}/libvirglrenderer.so* ${WORKDIR}/stage/lib/
}

do_compile() {
	oe_runmake -C ${S}
}

do_install() {
	# qvm loads vdevs from /lib/dll, like the SDP's own vdev-*.so.
	install -d ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/lib/dll
	install -m 0755 ${S}/vdev-virtio-gpu.so \
		${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/lib/dll/vdev-virtio-gpu.so
}

SUMMARY = "virtio-gpu virtual device for the QNX hypervisor"
DESCRIPTION = "The host-side vdev qvm loads to give a guest a GPU: it hands the \
guest's virtio-gpu command stream to virglrenderer and presents the result \
through Screen."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

QNX_SRC_REPO = "git://github.com/maxmaster55/QNX_vdev-Virtio-GPU.git;protocol=https;branch=main"

DEPENDS = "virglrenderer libepoxy"

# Upstream reads VIRGL and STAGE from a paths.txt that the project's
# build-deps.sh generates, both pointing into one tree that held every GPU
# component. Each component now clones its own repository, so no such tree
# exists: the headers and library come from what DEPENDS staged instead.
#
# The Makefile hardcodes -I$(VIRGL)/src, -I$(VIRGL)/build-qnx/src and
# -L$(VIRGL)/build-qnx/src, so VIRGL points at a small directory assembled below
# with that shape. Patching the Makefile would be tidier, but it belongs to
# another repository and this keeps the two independent.
QNX_VIRGL_SHIM = "${WORKDIR}/virgl-shim"

EXTRA_OEMAKE = "VIRGL='${QNX_VIRGL_SHIM}' STAGE='${WORKDIR}/stage'"

do_compile:prepend() {
	# The two include paths the Makefile expects, both fed from the staged
	# headers.
	install -d ${QNX_VIRGL_SHIM}/src ${QNX_VIRGL_SHIM}/build-qnx/src
	cp -f ${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}/virglrenderer.h \
	      ${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}/virgl_hw.h \
	      ${QNX_VIRGL_SHIM}/src/
	cp -f ${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}/virgl-version.h ${QNX_VIRGL_SHIM}/build-qnx/src/

	# ...and the library, where -L expects to find it.
	cp -Pf ${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}/libvirglrenderer.so* ${QNX_VIRGL_SHIM}/build-qnx/src/

	# libscreen and libhypS come from the SDP. They live in different
	# directories under $QNX_TARGET, and libhypS is a static archive.
	install -d ${WORKDIR}/stage/lib
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so   ${WORKDIR}/stage/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so.1 ${WORKDIR}/stage/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/lib/libhypS.a          ${WORKDIR}/stage/lib/
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

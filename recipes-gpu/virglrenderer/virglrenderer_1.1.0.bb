SUMMARY = "virglrenderer, QNX aarch64le port"
DESCRIPTION = "Renders virtio-gpu command streams from a guest onto the host's \
GPU. Built from the project's QNX fork; the QNX source changes are baked into \
that tree, so there is no patch step here."
LICENSE = "MIT"

inherit qnx-meson qnx-project-src

QNX_APP_SUBDIR = "src/gpu/virglrenderer"

DEPENDS = "libepoxy"

EXTERNALSRC_BUILD = "${WORKDIR}/build"

# patchelf rewrites the libdrm soname after linking (the SDP ships libdrm.so.2
# where virglrenderer expects .so.1).
HOSTTOOLS_NONFATAL += "patchelf"

# The fork's Makefile carries the meson options its QNX port needs
# (-Dplatforms=egl, the render-server thread worker, -leventfd), so use it.
do_configure[noexec] = "1"

# Its meson build resolves epoxy and the SDP graphics libraries out of a single
# STAGE directory, so assemble one from what DEPENDS has already staged rather
# than from a hand-maintained tree.
do_compile:prepend() {
	install -d ${QNX_MESON_STAGE}/lib ${QNX_MESON_STAGE}/include

	cp -Pf ${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}/libepoxy.so* ${QNX_MESON_STAGE}/lib/
	cp -rf ${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR}/epoxy ${QNX_MESON_STAGE}/include/

	# From the SDP: libscreen is a shared object, libhypS a static archive, and
	# they live in different directories under $QNX_TARGET.
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so   ${QNX_MESON_STAGE}/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/usr/lib/libscreen.so.1 ${QNX_MESON_STAGE}/lib/
	ln -sf ${QNX_TARGET}/${QNX_PROCESSOR}/lib/libhypS.a          ${QNX_MESON_STAGE}/lib/

	# epoxy has no .pc in the SDP or in libepoxy's own install, and meson's
	# dependency('epoxy') needs one.
	cat > ${QNX_MESON_STAGE}/lib/pkgconfig/epoxy.pc <<PC
prefix=${QNX_MESON_STAGE}
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: epoxy
Description: libepoxy (QNX port)
Version: 1.5.11
Libs: -L\${libdir} -lepoxy
Cflags: -I\${includedir}
PC
}

do_compile() {
	oe_runmake -C ${S} \
		STAGE=${QNX_MESON_STAGE} \
		MESON_CROSS=${QNX_MESON_CROSS}
}

do_install() {
	install -d ${D}${QNX_STAGE_LIBDIR} ${D}${QNX_STAGE_INCLUDEDIR}

	install -m 0755 ${S}/build-qnx/src/libvirglrenderer.so.1.11.0 ${D}${QNX_STAGE_LIBDIR}/
	ln -sf libvirglrenderer.so.1.11.0 ${D}${QNX_STAGE_LIBDIR}/libvirglrenderer.so.1
	ln -sf libvirglrenderer.so.1      ${D}${QNX_STAGE_LIBDIR}/libvirglrenderer.so

	install -m 0644 ${S}/build-qnx/src/virgl-version.h ${D}${QNX_STAGE_INCLUDEDIR}/ || true
}

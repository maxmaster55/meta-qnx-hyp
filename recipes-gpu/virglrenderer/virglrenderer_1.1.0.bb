SUMMARY = "virglrenderer, QNX aarch64le port"
DESCRIPTION = "Renders virtio-gpu command streams from a guest onto the host's \
GPU. Built from the project's QNX fork; the QNX source changes are baked into \
that tree, so there is no patch step here."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=c81c08eeefd9418fca8f88309a76db10"

inherit qnx-meson qnx-src

QNX_SRC_REPO = "git://github.com/maxmaster55/QNX_virglrenderer.git;protocol=https;branch=main"

DEPENDS = "libepoxy"

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
	#
	# Absolute paths, and no pkg-config ${...} variables, on purpose: bitbake
	# expands ${...} in a shell function before the shell ever sees it, and it
	# does not honour backslash escaping. A "\${prefix}/lib" here came out as
	# "\/usr/lib" -- `prefix` is a real OE variable -- producing a .pc whose
	# include path resolved to nothing, and an epoxy whose headers "could not be
	# found" even though the dependency itself was located.
	#
	# The SDP include directory is needed too, since epoxy/egl.h includes
	# EGL/egl.h.
	# The epoxy_has_* variables are not decoration: virglrenderer gates its EGL
	# support on epoxy_has_egl == '1' (meson.build:297), and a .pc without them
	# fails with "Could not get pkg-config variable". The values mirror how the
	# libepoxy recipe's build is configured -- its Makefile passes
	# -Degl=yes -Dglx=no -Dx11=false.
	cat > ${QNX_MESON_STAGE}/lib/pkgconfig/epoxy.pc <<PC
epoxy_has_glx=0
epoxy_has_egl=1
epoxy_has_wgl=0

Name: epoxy
Description: libepoxy (QNX port)
Version: 1.5.11
Libs: -L${QNX_MESON_STAGE}/lib -lepoxy
Cflags: -I${QNX_MESON_STAGE}/include -I${QNX_TARGET}/usr/include
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

	# Both headers the virtio-gpu vdev compiles against. It used to reach into
	# this recipe's source tree for them; now that each recipe clones its own
	# repository there is no shared tree to reach into, so they are staged.
	# virglrenderer.h and virgl_hw.h both: the vdev needs the full struct
	# virgl_box, which virglrenderer.h only forward-declares.
	install -m 0644 ${S}/src/virglrenderer.h ${S}/src/virgl_hw.h \
		${D}${QNX_STAGE_INCLUDEDIR}/
	install -m 0644 ${S}/build-qnx/src/virgl-version.h ${D}${QNX_STAGE_INCLUDEDIR}/
}

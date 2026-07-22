SUMMARY = "libepoxy, QNX aarch64le port"
DESCRIPTION = "GL function pointer management library. Built from the project's \
QNX fork, whose Makefile wraps meson with the options that port needs; this \
recipe supplies the cross file and the synthesised pkg-config metadata the SDP \
does not ship."
LICENSE = "MIT"

inherit qnx-meson qnx-project-src

QNX_APP_SUBDIR = "src/gpu/libepoxy"

# Out of tree: meson refuses to configure into a dirty source directory, and the
# fork's Makefile puts its build in build-qnx/ relative to the source anyway.
EXTERNALSRC_BUILD = "${WORKDIR}/build"

# The fork already knows which meson options its QNX port needs (-Degl=yes
# -Dglx=no -Dx11=false), so drive its wrapper rather than re-deriving them.
do_configure[noexec] = "1"

do_compile() {
	oe_runmake -C ${S} \
		STAGE=${QNX_MESON_STAGE} \
		MESON_CROSS=${QNX_MESON_CROSS}
}

do_install() {
	install -d ${D}${QNX_STAGE_LIBDIR} ${D}${QNX_STAGE_INCLUDEDIR}/epoxy

	# A versioned chain: one real file plus two links, which the automatic
	# entry pass turns into [type=link] records rather than three copies.
	install -m 0755 ${S}/build-qnx/src/libepoxy.so.0.0.0 ${D}${QNX_STAGE_LIBDIR}/
	ln -sf libepoxy.so.0.0.0 ${D}${QNX_STAGE_LIBDIR}/libepoxy.so.0
	ln -sf libepoxy.so.0     ${D}${QNX_STAGE_LIBDIR}/libepoxy.so

	install -m 0644 ${S}/include/epoxy/*.h ${D}${QNX_STAGE_INCLUDEDIR}/epoxy/
}

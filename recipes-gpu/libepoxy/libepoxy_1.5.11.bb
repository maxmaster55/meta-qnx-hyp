SUMMARY = "libepoxy, QNX aarch64le port"
DESCRIPTION = "GL function pointer management library. Built from the project's \
QNX fork, whose Makefile wraps meson with the options that port needs; this \
recipe supplies the cross file and the synthesised pkg-config metadata the SDP \
does not ship."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=58ef4c80d401e07bd9ee8b6b58cf464b"

inherit qnx-meson qnx-src

# Cloned from its own repository, tracking the branch head. Pin QNX_SRC_REV to a
# commit for a reproducible build.
QNX_SRC_REPO = "git://github.com/maxmaster55/QNX_libepoxy.git;protocol=https;branch=main"

# The fork already knows which meson options its QNX port needs (-Degl=yes
# -Dglx=no -Dx11=false), so drive its wrapper rather than re-deriving them.
do_configure[noexec] = "1"

do_compile() {
	oe_runmake -C ${S} \
		STAGE=${QNX_MESON_STAGE} \
		MESON_CROSS=${QNX_MESON_CROSS}
}

# /lib/dll, which is where the reference host image puts it, rather than the
# /lib the stage layout would imply. Both are on LD_LIBRARY_PATH -- this is about
# the image matching the one it was modelled on.
#
# Two names, not three. The harvesting pass emits a record for the real file and
# one for the unversioned symlink, and skips the intermediate libepoxy.so.0
# because that name IS the soname -- a link from a name to itself. mkifs then
# stores the payload under the soname, which is why the image ends up with
# lib/dll/libepoxy.so.0 even though the record names libepoxy.so.0.0.0.
#
# A QNX_IFS_DEST for the intermediate name matches no record and warns.
QNX_IFS_DEST[libepoxy.so.0.0.0] = "/lib/dll/libepoxy.so.0.0.0"
QNX_IFS_DEST[libepoxy.so] = "/lib/dll/libepoxy.so"

do_install() {
	install -d ${D}${QNX_STAGE_LIBDIR} ${D}${QNX_STAGE_INCLUDEDIR}/epoxy

	# A versioned chain: one real file plus two links, which the automatic
	# entry pass turns into [type=link] records rather than three copies.
	install -m 0755 ${S}/build-qnx/src/libepoxy.so.0.0.0 ${D}${QNX_STAGE_LIBDIR}/
	ln -sf libepoxy.so.0.0.0 ${D}${QNX_STAGE_LIBDIR}/libepoxy.so.0
	ln -sf libepoxy.so.0     ${D}${QNX_STAGE_LIBDIR}/libepoxy.so

	install -m 0644 ${S}/include/epoxy/*.h ${D}${QNX_STAGE_INCLUDEDIR}/epoxy/

	# gl_generated.h and egl_generated.h are produced by the build, not shipped
	# in the source tree, and epoxy/gl.h includes them. Without these a consumer
	# compiles until the first #include and then stops with "No such file".
	install -m 0644 ${S}/build-qnx/include/epoxy/*_generated.h \
		${D}${QNX_STAGE_INCLUDEDIR}/epoxy/
}

SUMMARY = "CommonAPI code generators (host tools)"
DESCRIPTION = "Turn .fidl/.fdepl interface definitions into C++ bindings. These \
are prebuilt x86_64 Eclipse applications that run on the build host, not on the \
target, which is why this is a native recipe -- nothing here is cross-compiled \
and nothing ends up in an image."
HOMEPAGE = "https://github.com/COVESA/capicxx-core-tools"
# The tools themselves are MPL-2.0; the Eclipse and Franca components they are
# packaged with are EPL-1.0.
LICENSE = "MPL-2.0 & EPL-1.0"

# These release zips ship no top-level licence file -- the only licence text in
# the archive is the Eclipse about.html carried by each bundled plugin, so that
# is what is checksummed. It covers the launcher rather than the CommonAPI
# tools, which is worth knowing if this ever has to satisfy a real licence
# audit: the authoritative statement is upstream, not in the archive.
LIC_FILES_CHKSUM = "file://core/plugins/org.eclipse.equinox.launcher.gtk.linux.x86_64_1.1.800.v20180827-1352/about.html;md5=6a662193d36153b613d4b8c23a8a1921"

SRC_URI = "\
    https://github.com/COVESA/capicxx-core-tools/releases/download/${PV}/commonapi_core_generator.zip;name=core;subdir=core;unpack=1 \
    https://github.com/COVESA/capicxx-someip-tools/releases/download/${PV}/commonapi_someip_generator.zip;name=someip;subdir=someip;unpack=1 \
"
SRC_URI[core.sha256sum] = "ffe593c94f98078edcddcfd322f2cda34c0433f978c912c8e5db803a4a118702"
SRC_URI[someip.sha256sum] = "587123eaf7d4b95fab83fd6667076a71195f04364e066d53124ba7ebec69f859"

inherit native


S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

QNX_GENERATOR_DIR = "${datadir}/commonapi-generators"

do_install() {
	install -d ${D}${QNX_GENERATOR_DIR} ${D}${bindir}

	cp -rf ${S}/core   ${D}${QNX_GENERATOR_DIR}/
	cp -rf ${S}/someip ${D}${QNX_GENERATOR_DIR}/

	# The launchers are shipped without the execute bit in the zips.
	chmod +x ${D}${QNX_GENERATOR_DIR}/core/commonapi-core-generator-linux-x86_64
	chmod +x ${D}${QNX_GENERATOR_DIR}/someip/commonapi-someip-generator-linux-x86_64

	# Stable names on PATH, so a recipe generating bindings does not have to
	# know where the zips unpacked or which architecture suffix they carry.
	#
	# Relative, not absolute: an absolute symlink bakes this build directory
	# into the sysroot, and sstate refuses to package it because the path is
	# wrong for anyone who later restores it elsewhere.
	ln -sf ../share/commonapi-generators/core/commonapi-core-generator-linux-x86_64 \
		${D}${bindir}/commonapi-core-generator
	ln -sf ../share/commonapi-generators/someip/commonapi-someip-generator-linux-x86_64 \
		${D}${bindir}/commonapi-someip-generator

	# ...and under their full upstream names too. Application CMakeLists use
	# find_program(commonapi-core-generator-linux-x86_64), so the short name
	# alone would not be found.
	ln -sf ../share/commonapi-generators/core/commonapi-core-generator-linux-x86_64 \
		${D}${bindir}/commonapi-core-generator-linux-x86_64
	ln -sf ../share/commonapi-generators/someip/commonapi-someip-generator-linux-x86_64 \
		${D}${bindir}/commonapi-someip-generator-linux-x86_64
}

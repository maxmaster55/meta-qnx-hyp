SUMMARY = "Boost C++ libraries, cross-built for QNX"
DESCRIPTION = "Only the four libraries vsomeip links against -- system, thread, \
filesystem and log. Built with Boost.Build rather than cmake, because that is \
what Boost itself uses. \
\
This exists because neither available source of Boost fits: the QNX SDP ships \
none, and poky's boost recipe builds against Yocto's own cross-toolchain, which \
would produce a Linux ELF and drag in the whole cross-gcc that meta-qnx exists \
to avoid."
HOMEPAGE = "https://www.boost.org"
LICENSE = "BSL-1.0"
LIC_FILES_CHKSUM = "file://LICENSE_1_0.txt;md5=e4224ccaecb14d942c71d31bef20d78c"

BOOST_UNDERSCORE = "${@d.getVar('PV').replace('.', '_')}"

SRC_URI = "https://archives.boost.io/release/${PV}/source/boost_${BOOST_UNDERSCORE}.tar.bz2"
SRC_URI[sha256sum] = "cc4b893acf645c9d4b698e9a0f08ca8846aa5d6c68275c14c3e7949c24109454"

inherit qnx-sdp

S = "${WORKDIR}/boost_${BOOST_UNDERSCORE}"
B = "${S}"

# Boost.Build drives the compiler itself, so this names the GNU-style driver
# rather than qcc: b2 constructs its own command lines and does not understand
# qcc's -V argument.
QNX_BOOST_CXX ?= "ntoaarch64-g++"

# Only what vsomeip needs. Building all of Boost would take far longer and
# produce a great deal that nothing here links against.
QNX_BOOST_LIBS ?= "system thread filesystem log"

do_configure() {
	cd ${S}

	# Boost.Build's config: introduce the QNX compiler as a gcc variant named
	# "qnx", which is what toolset=gcc-qnx below then selects.
	cat > ${WORKDIR}/user-config.jam <<JAM
using gcc : qnx : ${QNX_BOOST_CXX} ;
JAM

	# bootstrap builds b2 itself with the *host* compiler -- b2 is a build tool
	# that runs here, not on the target.
	if [ ! -x ./b2 ]; then
		./bootstrap.sh --with-libraries=$(echo ${QNX_BOOST_LIBS} | tr ' ' ',')
	fi
}

do_compile() {
	cd ${S}

	withs=""
	for lib in ${QNX_BOOST_LIBS}; do
		withs="$withs --with-$lib"
	done

	./b2 ${PARALLEL_MAKE} \
		--user-config=${WORKDIR}/user-config.jam \
		toolset=gcc-qnx \
		target-os=qnx \
		--prefix=${WORKDIR}/boost-install \
		--layout=system \
		link=shared runtime-link=shared threading=multi \
		$withs \
		install
}

do_install() {
	install -d ${D}${QNX_STAGE_LIBDIR} ${D}${QNX_STAGE_INCLUDEDIR}
	cp -Pf ${WORKDIR}/boost-install/lib/*.so* ${D}${QNX_STAGE_LIBDIR}/
	cp -rf ${WORKDIR}/boost-install/include/* ${D}${QNX_STAGE_INCLUDEDIR}/
}

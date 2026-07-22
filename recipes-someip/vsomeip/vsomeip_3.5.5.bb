SUMMARY = "vsomeip, the COVESA SOME/IP implementation, ported to QNX"
DESCRIPTION = "Cross-built with qcc and carrying the QNX routing fix. Upstream \
assumes a netlink connector to learn when the multicast route is up; QNX has \
none, so external routing never became ready and service discovery never \
completed."
HOMEPAGE = "https://github.com/COVESA/vsomeip"
LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=9741c346eef56131163e13b9db1241b3"

SRC_URI = "git://github.com/COVESA/vsomeip.git;protocol=https;branch=master;tag=${PV} \
           file://vsomeip-qnx-pending-services.patch \
           file://compat-include/sys/eventfd.h \
           file://compat-include/sys/sockmsg.h"

inherit qnx-cmake

DEPENDS = "boost"

S = "${WORKDIR}/git"

require ../someip-qnx-flags.inc

# On top of the shared flags: <sys/eventfd.h> and <sys/sockmsg.h> do not exist
# on QNX, so the port supplies small shims (files/compat-include).
CFLAGS:append = " -I${WORKDIR}/compat-include"
CXXFLAGS:append = " -I${WORKDIR}/compat-include"

OECMAKE_EXTRA_ARGS = "\
    -DENABLE_SIGNAL_HANDLING=1 \
    -DVSOMEIP_ENABLE_MULTIPLE_ROUTING_MANAGERS=1 \
    -DBoost_NO_BOOST_CMAKE=ON \
    -DBOOST_ROOT=${RECIPE_SYSROOT}${QNX_STAGE_DIR} \
    -DBOOST_INCLUDEDIR=${RECIPE_SYSROOT}${QNX_STAGE_INCLUDEDIR} \
    -DBOOST_LIBRARYDIR=${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR} \
    -DINSTALL_LIB_DIR:PATH=${QNX_PROCESSOR}/lib \
    -DINSTALL_INCLUDE_DIR:PATH=usr/include \
    -DINSTALL_CMAKE_DIR:PATH=${QNX_PROCESSOR}/lib/cmake/vsomeip3 \
"

# vsomeip installs where its own INSTALL_*_DIR variables point, not
# CMAKE_INSTALL_LIBDIR -- which is why the Linux recipe in meta-bmo passes
# INSTALL_LIB_DIR too. Without this the libraries land in <prefix>/lib, outside
# the ${QNX_PROCESSOR}/ tree that mkifs -r searches and that the sysroot -L
# points at, so nothing would find them.

# The examples and test binaries are built by upstream but are not wanted in an
# image; only the libraries and the routing manager daemon are staged.
do_install:append() {
	rm -rf ${D}${QNX_STAGE_DIR}/bin/vsomeip_tests
}

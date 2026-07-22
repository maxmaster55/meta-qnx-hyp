SUMMARY = "CommonAPI C++ SOME/IP runtime, cross-built for QNX"
DESCRIPTION = "Binds CommonAPI to vsomeip. Needs both the core runtime and \
vsomeip, and finds each through the CMake package config the other installed."
HOMEPAGE = "https://github.com/COVESA/capicxx-someip-runtime"
LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=815ca599c9df247a0c7f619bab123dad"

SRC_URI = "git://github.com/COVESA/capicxx-someip-runtime.git;protocol=https;branch=master;tag=${PV}"

inherit qnx-cmake

require ../someip-qnx-flags.inc

DEPENDS = "commonapi-core vsomeip boost"

S = "${WORKDIR}/git"

CXXFLAGS:append = " -include string"

# Both dependencies are found through the CMake package configs they installed
# into the sysroot -- the same mechanism the Linux recipe uses, pointed at the
# QNX stage tree instead of ${STAGING_LIBDIR}.
OECMAKE_EXTRA_ARGS = "\
    -DBUILD_SHARED_LIBS=ON \
    -DCommonAPI_DIR=${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}/cmake/CommonAPI \
    -Dvsomeip3_DIR=${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR}/cmake/vsomeip3 \
    -DINSTALL_LIB_DIR:PATH=${QNX_PROCESSOR}/lib \
    -DINSTALL_INCLUDE_DIR:PATH=usr/include \
    -DINSTALL_CMAKE_DIR:PATH=${QNX_PROCESSOR}/lib/cmake/CommonAPI-SomeIP \
"

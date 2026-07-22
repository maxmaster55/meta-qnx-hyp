SUMMARY = "CommonAPI C++ core runtime, cross-built for QNX"
DESCRIPTION = "The transport-independent half of CommonAPI. Version-matched to \
the SOME/IP runtime and to the code generators the applications use."
HOMEPAGE = "https://github.com/COVESA/capicxx-core-runtime"
LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=815ca599c9df247a0c7f619bab123dad"

SRC_URI = "git://github.com/COVESA/capicxx-core-runtime.git;protocol=https;branch=master;tag=${PV}"

inherit qnx-cmake

S = "${WORKDIR}/git"

# Upstream reaches for std::string without including it in one header, which
# older host compilers tolerated. Same workaround as the Linux recipe.
CXXFLAGS:append = " -include string"

OECMAKE_EXTRA_ARGS = "\
    -DBUILD_SHARED_LIBS=ON \
    -DINSTALL_LIB_DIR:PATH=${QNX_PROCESSOR}/lib \
    -DINSTALL_INCLUDE_DIR:PATH=usr/include \
    -DINSTALL_CMAKE_DIR:PATH=${QNX_PROCESSOR}/lib/cmake/CommonAPI \
"

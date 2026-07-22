# Compiler settings every part of the SOME/IP stack needs on QNX.
#
# vsomeip and CommonAPI both assume a glibc/BSD environment in places. The
# project's own commonapi-qnx port puts these in one CMake toolchain file shared
# by all its builds; this is the same set, in the form Yocto recipes use.
#
#   __BYTE_ORDER and friends  QNX defines BYTE_ORDER but not the double
#                             underscore spellings these headers reach for
#                             (CommonAPI/SomeIP/OutputStream.hpp)
#   SA_RESTART                not defined by QNX's signal.h
#   -Wno-narrowing            ClientId.cpp assigns 0xFFFFFFFF to a signed uid_t;
#                             a warning on Linux, an error here
#
# Kept in one place rather than repeated per recipe: they are a property of
# building this stack on QNX, not of any one component.

QNX_SOMEIP_DEFINES = "-D_QNX_SOURCE -DSA_RESTART=0x0040 \
                      -DBYTE_ORDER=1234 -DLITTLE_ENDIAN=1234 -DBIG_ENDIAN=4321 \
                      -D__BYTE_ORDER=1234 -D__LITTLE_ENDIAN=1234 -D__BIG_ENDIAN=4321"

CFLAGS:append = " ${QNX_SOMEIP_DEFINES}"
CXXFLAGS:append = " ${QNX_SOMEIP_DEFINES} -Wno-narrowing"

# eventfd() is not in QNX's libc; the SDP ships it as a separate library. Without
# this, everything compiles and the link fails with an undefined reference to
# eventfd from libCommonAPI-SomeIP.
LDFLAGS:append = " -leventfd"

SUMMARY = "Graphics demos and the Vulkan stack for the Raspberry Pi 5"
DESCRIPTION = "gles2-gears, vkcubepp and vulkaninfo, plus the Vulkan loader and \
the board's Broadcom ICD they need. These are how you find out whether the GPU \
is actually working, rather than whether Screen started -- which is a different \
question and the one everything else answers."
LICENSE = "CLOSED"

inherit qnx-sdp-component

# gles2-gears runs on GLES2, which Screen provides; the vk* pair need the Vulkan
# loader and an ICD pointing at the board's driver.
# The demos themselves are qnx-gfx-demos; this adds the board's Vulkan driver
# and the manifests the loader reads to find it.
QNX_COMPONENT_FILES = ""

DEPENDS += "qnx-gfx-demos"

# libimg and the image codecs come from qnx-screen, which every image carrying
# Screen already installs.
# The ICD and validation-layer manifests are nested under usr/lib/graphics,
# which mkifs's search path covers but the component resolver does not descend
# into -- and they have to land in /lib/graphics where the loader looks, not
# where the SDP keeps them. So they are raw records with a destination of their
# own.
#
# Sources are relative, resolved by mkifs against usr/lib -- the same form the
# reference build file and the rest of this image's graphics entries use.
#
# Note the single-backslash \n: qnx-image-contract turns a literal backslash-n
# into a newline, so \\n leaves a stray backslash at the end of every record and
# mkifs rejects the lot with "Improper filename specification" pointing at the
# first one.
QNX_IFS_EXTRA_ENTRIES = "\
[type=file uid=0 gid=0 perms=0444] /lib/graphics/drm-rpi5/vulkan_broadcom.so=graphics/drm-rpi5/vulkan_broadcom.so\n\
[type=file uid=0 gid=0 perms=0555] /lib/graphics/drm-rpi5/broadcom_icd.json=graphics/drm-rpi5/broadcom_icd.json\n\
[type=file uid=0 gid=0 perms=0444] /lib/graphics/vulkan/VkLayer_khronos_validation.json=graphics/vulkan/VkLayer_khronos_validation.json\
"

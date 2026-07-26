SUMMARY = "Components that run on both sides of the hypervisor"
DESCRIPTION = "The applications the hypervisor host and its guests both carry. \
They are listed here once so the two images cannot drift apart: adding a shared \
component otherwise means remembering to edit two image recipes in two layers. \
Installing this group is one word in each image."
LICENSE = "CLOSED"

inherit qnx-packagegroup

# rpi-gpio is the GPIO resource manager: the host drives the real pins, and a
# guest talks to the same interface through the hypervisor, so both images need
# the binary.
#
# Down to one member since frame-router was dropped. The group stays: it is the
# seam that keeps host and guest from drifting, and a one-member group is not a
# reason to unpick that -- the next shared component is one word here rather
# than an edit to two images in two layers.
QNX_PACKAGEGROUP_INSTALL = "rpi-gpio"

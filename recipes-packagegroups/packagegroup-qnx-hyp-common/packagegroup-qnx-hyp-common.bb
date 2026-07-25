SUMMARY = "Components that run on both sides of the hypervisor"
DESCRIPTION = "The applications the hypervisor host and its guests both carry. \
They are listed here once so the two images cannot drift apart: before this \
group existed, both image recipes named frame-router and rpi-gpio individually, \
and adding a third shared component meant remembering to edit two files in two \
layers. Installing this group is one word in each image."
LICENSE = "CLOSED"

inherit qnx-packagegroup

# frame-router carries frames over shared memory between host and guest, so by
# construction it exists on both. rpi-gpio is the GPIO resource manager: the
# host drives the real pins, and a guest talks to the same interface through the
# hypervisor, so both images need the binary.
QNX_PACKAGEGROUP_INSTALL = "frame-router rpi-gpio"

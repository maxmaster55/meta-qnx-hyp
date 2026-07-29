# Put the Linux guest on the host's data partition, beside the QNX one:
#
#   /guests/guest-2/image.bin       the kernel
#   /guests/guest-2/fs.img          the rootfs, a raw ext4
#   /guests/guest-2/linux.qvmconf   its vdev configuration
#
# A bbappend rather than an edit to the base recipe, for the same reason
# meta-qnx-guest appends for guest-1: which guests exist is policy, and a build
# with no Linux image to hand should produce a data partition carrying one guest
# rather than fail.

QNX_LINUX_GUEST_DEPLOY ?= ""
QNX_LINUX_GUEST_DIR ?= "guest-2"
QNX_LINUX_GUEST_STAGE ?= "${QNX_STAGE_DIR}/linux-guest"

# ~730MB of Linux on top of the QNX guest, so the partition cannot carry a fixed
# size -- a number nobody would remember to update. meta-qnx-guest's bbappend
# already sets this; stated again because this layer's contribution is what
# makes it unavoidable.
QNX_ROOTFS_SIZE = "auto"

# Everything else is conditional on there being a Linux image at all.
# qnx-linux-guest skips itself when QNX_LINUX_GUEST_DEPLOY is unset, and
# depending on a skipped recipe is a hard error -- so this has to make the same
# decision rather than assume it was made.
#
# In anonymous python rather than inline ${@...}: the value is three lines, and
# bitbake will not parse a multi-line expansion in a variable assignment.
python () {
    if not (d.getVar('QNX_LINUX_GUEST_DEPLOY') or '').strip():
        return

    name = d.getVar('QNX_LINUX_GUEST_DIR')
    # Read out of this recipe's sysroot, where qnx-linux-guest staged them --
    # the same place QNX_ROOTFS_INSTALL members land.
    stage = d.getVar('RECIPE_SYSROOT') + d.getVar('QNX_LINUX_GUEST_STAGE')

    # Leading separator, not just separators between: another layer may have
    # appended already (meta-qnx-guest does, for guest-1) and none of them end
    # with one. Without it the previous layer's last record and this layer's
    # first run together on one line, and mkqnx6fsimg reports the merged result
    # as "Improper filename specification" pointing at a line that looks fine
    # until you notice its length.
    d.appendVar('QNX_ROOTFS_EXTRA', '\\n' + '\\n'.join([
        '/guests/%s/image.bin = %s/image.bin' % (name, stage),
        '/guests/%s/fs.img = %s/fs.img' % (name, stage),
        '/guests/%s/linux.qvmconf = %s/linux.qvmconf' % (name, stage),
    ]))

    d.appendVar('DEPENDS', ' qnx-linux-guest')
}

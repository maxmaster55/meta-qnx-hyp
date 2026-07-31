SUMMARY = "Linux guest for the QNX hypervisor, from a Yocto qemuarm64 build"
DESCRIPTION = "Stages an ordinary Linux kernel and rootfs, plus the qvmconf that \
runs them under qvm. Nothing here builds Linux: the images come from a separate \
Yocto build whose MACHINE is qemuarm64, which is what makes them work as a guest \
-- qvm presents the same virtio-mmio and pl011 layout qemu's `virt` machine \
does, so a kernel built for qemu needs no changes."
LICENSE = "CLOSED"

inherit qnx-sdp

SRC_URI = "file://linux.qvmconf"

S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# ---------------------------------------------------------------------------
# Where the Linux images come from
# ---------------------------------------------------------------------------
# The deploy directory of a separate Yocto build. Not fetched and not built
# here: a Linux distro image is a whole second build, with its own layers,
# machine and package feed, and pretending otherwise would put a multi-hour
# qemuarm64 build in the dependency graph of a QNX image.
#
#     QNX_LINUX_GUEST_DEPLOY = "/path/to/build/tmp/deploy/images/qemuarm64"
#
# Unset, this recipe skips and the host image simply has no Linux guest.
QNX_LINUX_GUEST_DEPLOY ?= ""

# Names within that directory. Both are the unversioned symlinks Yocto keeps
# pointing at the newest build, so a rebuild of the Linux side is picked up
# without editing anything here.
QNX_LINUX_GUEST_KERNEL ?= "Image-qemuarm64.bin"
QNX_LINUX_GUEST_ROOTFS ?= "bmo-image-ai-qemuarm64.rootfs.ext4"

# ---------------------------------------------------------------------------
# Guest identity
# ---------------------------------------------------------------------------
# The system name is what the guest's namespace appears under on the host
# (/dev/qvm/<name>), so the QNX guest's guest_to_guest peer path has to match it.
QNX_LINUX_GUEST_NAME ?= "guest_2"
QNX_LINUX_GUEST_DIR ?= "guest-2"

# The QNX guest's system name, for the direct guest-to-guest link.
QNX_QNX_GUEST_NAME ?= "guest_1"

QNX_LINUX_GUEST_RAM_ADDR ?= "0x80000000"
QNX_LINUX_GUEST_RAM ?= "512M"

QNX_LINUX_GUEST_MAC ?= "52:54:00:00:01:02"
QNX_LINUX_GUEST_MAC2 ?= "52:54:00:00:02:02"

# ---------------------------------------------------------------------------
# Guest interrupt controller
# ---------------------------------------------------------------------------
# Empty, which emits no `vdev gic` stanza at all. qvm builds a GIC for the guest
# regardless -- every other vdev here refers to it as `intr gic:NN` and would
# have nothing to attach to otherwise.
#
# This was not always empty. The template arrived carrying
#
#     vdev gic
#             version 2
#
# and this SDP's qvm (hypervisor.core 3.0.0.00465, "hypervisor_be-800_B465")
# refuses to parse it:
#
#     [linux.qvmconf:25] Unsupported GIC version: 2
#
# Those two lines came from the vdev-virtio-gpu upstream example
# (src/gpu/vdev-virtio-gpu/example/host/guest.qvmconf in the hypervisor project),
# which targets a different board -- not from anything RPi5-specific. Note that
# the value is not wrong about the hardware: the RPi5 startup reports a GICv2
# host ("GICv2: routing SPIs to gic cpu %d"). qvm rejects being told explicitly
# all the same.
#
# The evidence for leaving it out is the QNX guest. qnx-guest.qvmconf declares
# eleven vdevs and no `vdev gic` among them, and that guest boots on this board.
#
# Set to 2 or 3 to emit the stanza again -- worth trying if a later SDP wants it
# stated, or while narrowing down a GIC problem:
#
#     QNX_LINUX_GUEST_GIC_VERSION = "3"
#
# ...which is where this ended up. Leaving it out is *not* right for Linux: with
# no stanza qvm defaults to a GICv3 at the ARM foundation-model addresses, and
# the guest kernel dies bringing it up --
#
#     GICv3: CPU0: found redistributor 0 region 0:0x000000002f100000
#     Internal error: Oops - Undefined instruction
#     pc : gic_cpu_sys_reg_init+0x5c/0x2b8
#
# The faulting instruction (0xd538cca0) is `MRS x0, ICC_SRE_EL1`, the GICv3 CPU
# interface system register. Linux's gic-v3 driver requires it; this board has
# GICv2 hardware and qvm's GICv3 emulation is memory-mapped only ("GICv3: using
# memory-mapped interface, full software emulation not supported"). QNX guests
# cope with that, which is why guest-1 boots with no stanza at all. Linux does
# not.
#
# QNX's own documentation is explicit: the default is 3, "but if the underlying
# hardware uses GICv2 then 2 must be specified". The RPi5 startup reports GICv2.
QNX_LINUX_GUEST_GIC_VERSION ?= "2"

# ---------------------------------------------------------------------------
# Host GIC virtualisation registers
# ---------------------------------------------------------------------------
# Asking for `version 2` alone is not enough on this board:
#
#     [linux.qvmconf:25] Unsupported GIC version: 2
#
# To present a GICv2 to a guest, qvm needs the host's GIC virtual CPU interface
# (GICV) and hypervisor control (GICH) registers. It finds them through the
# syspage asinfo entries named "gicv" and "gich", and startup-bcm2712-rpi5
# publishes neither -- it emits "gicd" and "gicc" and stops there.
#
# THIS IS A HYPOTHESIS, NOT A CONFIRMED FIX. It has not been tested on hardware.
# What is established: the startup binary carries no "gicv"/"gich" string, and
# qvm documents these two variables for precisely this case ("Needed on boards
# where the Startup bootstrap program does not automatically supply the address
# via syspage"). What is *not* established is that this is why version 2 is
# rejected. Note in particular that the Pi 4 startup does not publish them
# either, so this cannot be the whole story.
#
# What was ruled out: any difference from the hypervisor project. Its
# startup-bcm2712-rpi5 is byte-identical to this one (md5 93ad3951...), its
# startup arguments are the same, and it pins the same hypervisor package. Its
# linux.qvmconf would fail here identically. The Linux guest that worked there
# was almost certainly the Pi *4* target -- that project carries both
# rpi4-hypervisor.build and rpi5-hypervisor.build, and the only board in its
# startup source tree is boards/bcm2711, a Pi 4.
#
# The addresses are the Pi 5's own, read out of
# bcm2712-rpi-5-b.dtb: the arm,gic-400 node lists GICD/GICC/GICH/GICV at bus
# 0x7fff9000/0x7fffa000/0x7fffc000/0x7fffe000, and the soc `ranges` maps bus 0
# to physical 0x10_0000_0000.
#
# Both empty skips the lines, for a board whose startup does publish them.
QNX_HOST_PADDR_GICV ?= "0x107fffe000"
QNX_HOST_PADDR_GICH ?= "0x107fffc000"

# Absolute paths outside the recipe are invisible to task signatures, so
# without help a rebuild of the Linux side would not rebuild this. Two
# mechanisms, and both are needed -- they catch different changes:
#
#   vardeps         catches a change to *which* files are read: a different
#                   deploy directory, or different image names.
#
#   file-checksums  catches a change to the files *themselves*, and is the one
#                   that matters in practice. The two names below are the
#                   unversioned symlinks Yocto keeps pointing at its newest
#                   build, so rebuilding the Linux image leaves every variable
#                   here identical and moves only the content. With vardeps
#                   alone bitbake computes the same signature, restores
#                   do_install from sstate, and the host disk goes out carrying
#                   the *previous* rootfs -- no error, no warning, and an image
#                   that looks freshly built.
#
# Cost is not what it appears. bitbake stats each path (following the symlink to
# the timestamped file behind it) and re-hashes only when mtime or size has
# moved, caching the result in local_file_checksum_cache.dat -- so the 750MB
# rootfs is read once per Linux rebuild, not once per parse.
#
# The checksums propagate downstream on their own: qnx-host-data DEPENDS on this
# recipe, so a changed do_install signature changes its populate_sysroot, which
# changes qnx-host-data's tasks, which changes the disk. Nothing else needs
# annotating.
do_install[vardeps] += "QNX_LINUX_GUEST_DEPLOY QNX_LINUX_GUEST_KERNEL QNX_LINUX_GUEST_ROOTFS"
do_install[file-checksums] += "\
    ${QNX_LINUX_GUEST_DEPLOY}/${QNX_LINUX_GUEST_KERNEL}:True \
    ${QNX_LINUX_GUEST_DEPLOY}/${QNX_LINUX_GUEST_ROOTFS}:True \
"

QNX_LINUX_GUEST_STAGE = "${QNX_STAGE_DIR}/linux-guest"

do_install() {
	install -d ${D}${QNX_LINUX_GUEST_STAGE}

	for f in ${QNX_LINUX_GUEST_KERNEL} ${QNX_LINUX_GUEST_ROOTFS}; do
		if [ ! -f "${QNX_LINUX_GUEST_DEPLOY}/$f" ]; then
			bbfatal "${QNX_LINUX_GUEST_DEPLOY}/$f does not exist. QNX_LINUX_GUEST_DEPLOY should be the deploy/images/<machine> directory of a Yocto build, and the two file names should be what that build produced."
		fi
	done

	# Renamed to what the qvmconf loads. qvm takes the kernel by the name in
	# `load` and the disk by the name in `hostdev`, both relative to the
	# directory qvm is started from.
	install -m 0644 ${QNX_LINUX_GUEST_DEPLOY}/${QNX_LINUX_GUEST_KERNEL} \
		${D}${QNX_LINUX_GUEST_STAGE}/image.bin
	install -m 0644 ${QNX_LINUX_GUEST_DEPLOY}/${QNX_LINUX_GUEST_ROOTFS} \
		${D}${QNX_LINUX_GUEST_STAGE}/fs.img

	# The GIC stanza is a block rather than a value: empty when no version is
	# configured, several lines when one is. Built here because sed substitutes
	# a string and this has to be able to substitute nothing at all.
	#
	# The `set` lines come first: they are VM-wide configuration variables and
	# have to be in effect before the vdev that depends on them is parsed.
	gic_block=''
	if [ -n "${QNX_LINUX_GUEST_GIC_VERSION}" ]; then
		if [ -n "${QNX_HOST_PADDR_GICV}" ]; then
			gic_block="${gic_block}set host-paddr-gicv ${QNX_HOST_PADDR_GICV}\n"
		fi
		if [ -n "${QNX_HOST_PADDR_GICH}" ]; then
			gic_block="${gic_block}set host-paddr-gich ${QNX_HOST_PADDR_GICH}\n"
		fi
		gic_block="${gic_block}\nvdev gic\n        version ${QNX_LINUX_GUEST_GIC_VERSION}\n"
	fi

	# Same @MARKER@ convention the .build templates use, expanded here rather
	# than by qnx-ifs: a qvmconf is read by qvm on the target, not by mkifs, so
	# it never passes through the image template machinery.
	sed -e "s|@QNX_LINUX_GUEST_GIC@|$gic_block|g" \
	    -e 's|@QNX_LINUX_GUEST_NAME@|${QNX_LINUX_GUEST_NAME}|g' \
	    -e 's|@QNX_LINUX_GUEST_RAM_ADDR@|${QNX_LINUX_GUEST_RAM_ADDR}|g' \
	    -e 's|@QNX_LINUX_GUEST_RAM@|${QNX_LINUX_GUEST_RAM}|g' \
	    -e 's|@QNX_LINUX_GUEST_MAC@|${QNX_LINUX_GUEST_MAC}|g' \
	    -e 's|@QNX_LINUX_GUEST_MAC2@|${QNX_LINUX_GUEST_MAC2}|g' \
	    -e 's|@QNX_QNX_GUEST_NAME@|${QNX_QNX_GUEST_NAME}|g' \
	    ${WORKDIR}/linux.qvmconf > ${D}${QNX_LINUX_GUEST_STAGE}/linux.qvmconf
	chmod 0644 ${D}${QNX_LINUX_GUEST_STAGE}/linux.qvmconf

	if grep -q '@[A-Z_]*@' ${D}${QNX_LINUX_GUEST_STAGE}/linux.qvmconf; then
		bbfatal "unexpanded markers left in linux.qvmconf: $(grep -o '@[A-Z_]*@' ${D}${QNX_LINUX_GUEST_STAGE}/linux.qvmconf | sort -u | tr '\n' ' ')"
	fi
}

# Bound for the host's data partition, not an IFS -- a 700MB rootfs in a
# RAM-resident image is not a possibility.
QNX_IFS_AUTO_ENTRIES = "0"

# The rootfs is a Linux ext4 and the kernel an arm64 Image; neither is a QNX ELF
# and the machine check would reject both.
QNX_ELF_CHECK = "0"

python () {
    if not d.getVar('QNX_LINUX_GUEST_DEPLOY'):
        raise bb.parse.SkipRecipe(
            "QNX_LINUX_GUEST_DEPLOY is not set. Point it at the "
            "deploy/images/<machine> directory of a Yocto build for qemuarm64 "
            "to carry a Linux guest.")
}

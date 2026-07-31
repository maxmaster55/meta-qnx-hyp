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
# Empty, which emits no `vdev gic` stanza and leaves qvm at its default of
# version 3. That is not a preference: 3 is the only value this qvm accepts.
#
# The template arrived carrying `vdev gic / version 2`, copied from the
# vdev-virtio-gpu upstream example, and qvm refuses it:
#
#     [linux.qvmconf:34] Unsupported GIC version: 2
#
# QNX's published documentation says the option takes 2 or 3, and that 2 "must
# be specified if the underlying hardware uses GICv2" -- which describes this
# board, whose startup publishes gicd/gicc and reports GICv2. All of that is
# true and none of it matters. Disassembling vdgic_control in this SDP's qvm
# (hypervisor.core 3.0.0.00465, "hypervisor_be-800_B465") gives:
#
#     bl   qvm_parse_num          ; parse the version value
#     ldr  x2, [sp, #200]
#     cmp  x2, #0x3               ; compare against 3
#     b.ne 1c274                  ; anything else -> "Unsupported GIC version"
#     str  w2, [x25, #11680]      ; only 3 is ever stored
#
# A literal comparison against 3, with no reference to the host GIC at all. This
# build implements GICv3 guests only, whatever the documentation says.
#
# Two dead ends recorded so nobody repeats them:
#
#   - host-paddr-gicv / host-paddr-gich. The host publishes no "gicv" or "gich"
#     asinfo entry (confirmed with `pidin syspage=asinfo`: gicd at
#     0x107fff9000 and gicc at 0x107fffa000, nothing else), and qvm documents
#     those variables for exactly that gap. Supplying them changes nothing --
#     the version check never consults them. They also cannot be set before the
#     VM exists, because `system` must be the first option specified.
#
#   - the hypervisor project's configuration. Its startup-bcm2712-rpi5 is
#     byte-identical to this one (md5 93ad3951...), same arguments, same
#     hypervisor package. Its linux.qvmconf would fail here identically.
#
# Where this leaves a Linux guest on this board: nowhere good. qvm will only
# emulate a GICv3, and on a GICv2 host it does so through the memory-mapped
# interface ("GICv3: using memory-mapped interface, full software emulation not
# supported"). Linux's gic-v3 driver requires the CPU system-register
# interface, so it dies in gic_cpu_sys_reg_init on `MRS x0, ICC_SRE_EL1`
# (0xd538cca0) with an undefined instruction. QNX guests cope with the
# memory-mapped interface, which is why guest-1 boots and this does not.
#
# Nothing in this recipe can fix that -- it is a property of qvm and the board.
# Set to 3 to state the default explicitly; there is no other accepted value.
QNX_LINUX_GUEST_GIC_VERSION ?= ""

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
	# configured, two lines when one is. Built here because sed substitutes a
	# string and this has to be able to substitute nothing at all.
	gic_block=''
	if [ -n "${QNX_LINUX_GUEST_GIC_VERSION}" ]; then
		gic_block="vdev gic\n        version ${QNX_LINUX_GUEST_GIC_VERSION}\n"
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

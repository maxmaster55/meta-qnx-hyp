SUMMARY = "Raspberry Pi 5 board support package, from the SDP"
DESCRIPTION = "startup-bcm2712-rpi5, devb-sdmmc-bcm2712, i2c-dwc-rpi5, \
devc-serpl011-rpi5, gpio-rp1, msix-rp1 and wdtkick -- the board binaries a \
hypervisor host image cannot boot without. They are not in ${QNX_TARGET}: the \
Software Center delivers a BSP as a zip under ${QNX_SDP_ROOT}/bsp, carrying \
prebuilt binaries alongside the source they were built from. This recipe \
unpacks the prebuilt half into the stage tree, where mkifs already looks."
LICENSE = "CLOSED"

inherit qnx-sdp

# Installed by the bsp-rpi5 SDP feature (conf/qnx-sdp-features.inc), which
# resolves to com.qnx.qnx800.bsp.hw.raspberrypi_bcm2712_rpi5. check_sdp reports
# a named, actionable error if it is absent rather than leaving mkifs to fail on
# a missing file.
QNX_SDP_REQUIRES = "com.qnx.qnx800.bsp.hw.raspberrypi_bcm2712_rpi5"

# The zip's name carries an SVN revision and a build number that change with
# every BSP release, so it is globbed rather than named. PV tracks the package
# version for readability; the glob is what actually finds the file.
QNX_BSP_ZIP_GLOB ?= "BSP_raspberrypi-bcm2712-rpi5_*.zip"
QNX_BSP_ZIP_DIR ?= "${QNX_SDP_ROOT}/bsp"

# Which tree inside the zip to take. "prebuilt" is what QNX ships built; the
# sibling "install" directory is an empty placeholder that a from-source build
# populates. Building the BSP here instead is a separate job -- src/ is in the
# same zip, so nothing stops it, and this variable is where that would plug in.
QNX_BSP_TREE ?= "prebuilt"

S = "${WORKDIR}/bsp"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# Nothing to fetch: the zip is already on disk, put there by install_sdp.
SRC_URI = ""

python do_unpack() {
    import glob
    import os
    import shutil
    import zipfile

    zipdir = d.getVar('QNX_BSP_ZIP_DIR')
    pattern = os.path.join(zipdir, d.getVar('QNX_BSP_ZIP_GLOB'))
    found = sorted(glob.glob(pattern))

    if not found:
        bb.fatal("no BSP archive matching %s (checked at parse time too, so "
                 "reaching here means it vanished mid-build)" % pattern)

    # Newest by name: the trailing SVN/build numbers sort in release order, so
    # an SDP carrying two BSP generations uses the later one.
    archive = found[-1]
    if len(found) > 1:
        bb.note("%s: %d BSP archives present, using %s"
                % (d.getVar('PN'), len(found), os.path.basename(archive)))

    workdir = d.getVar('S')
    if os.path.isdir(workdir):
        shutil.rmtree(workdir)
    bb.utils.mkdirhier(workdir)

    tree = d.getVar('QNX_BSP_TREE')
    prefix = tree + '/'

    # Python's zipfile rather than the unzip command, which is not in bitbake's
    # sanitized PATH and would have to be added to HOSTTOOLS -- a fatal
    # requirement on every build, for one recipe in one board layer.
    #
    # The catch is that ZipFile.extract() applies the process umask and drops
    # the executable bit, which for a tree of drivers is silently wrong: they
    # install, they land in the image, and the board reports an exec failure.
    # The mode is in the archive, in the top 16 bits of external_attr, so it is
    # restored explicitly.
    extracted = 0
    with zipfile.ZipFile(archive) as zf:
        for info in zf.infolist():
            # Only the tree wanted, not the ~24MB of source and .sym files
            # beside it.
            if not info.filename.startswith(prefix):
                continue
            zf.extract(info, workdir)
            if info.is_dir():
                continue
            mode = info.external_attr >> 16
            if mode:
                os.chmod(os.path.join(workdir, info.filename), mode & 0o7777)
            extracted += 1

    unpacked = os.path.join(workdir, tree, d.getVar('QNX_PROCESSOR'))
    if not os.path.isdir(unpacked):
        bb.fatal("%s carries no %s/%s -- the BSP layout changed"
                 % (archive, tree, d.getVar('QNX_PROCESSOR')))

    bb.note("%s: unpacked %d files from %s"
            % (d.getVar('PN'), extracted, os.path.basename(archive)))
}

# Straight into the stage tree, which is laid out exactly as `mkifs -r` wants
# (<root>/aarch64le/{bin,sbin,boot/sys}). An image that DEPENDS on this recipe
# can then name these binaries by bare name, which is what the host template
# already does -- so nothing there had to change when they stopped coming from a
# project checkout.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}
	cp -a ${S}/${QNX_BSP_TREE}/${QNX_PROCESSOR} ${D}${QNX_STAGE_DIR}/
}

# The image names what it wants from these by hand, in its own build file: a
# board image uses a handful of the binaries here and a startup that has to be
# named in the boot line, not a harvested list of everything the BSP ships.
QNX_IFS_AUTO_ENTRIES = "0"

# These are QNX's own aarch64 binaries; there is no build here whose ${CC} could
# have gone wrong, and the check would only walk them for nothing.
QNX_ELF_CHECK = "0"


# An SDP without the BSP package is a legitimate state -- an older install, or a
# build that points QNX_BSP_ROOT at a BSP tree of its own and never needs this
# recipe. Skip with something actionable rather than failing whatever asked for
# it with a glob that matched nothing.
python () {
    import glob
    import os

    pattern = os.path.join(d.getVar('QNX_BSP_ZIP_DIR') or '',
                           d.getVar('QNX_BSP_ZIP_GLOB') or '')
    if not glob.glob(pattern):
        raise bb.parse.SkipRecipe(
            "no BSP archive matching %s. It comes from the SDP package %s: add "
            "'bsp-rpi5' to QNX_SDP_FEATURES (or name the package in "
            "QNX_SDP_EXTRA_PACKAGES) and run 'bitbake -c install_sdp qnx-sdp'. "
            "A build that supplies its own BSP via QNX_BSP_ROOT does not need "
            "this recipe at all."
            % (pattern, d.getVar('QNX_SDP_REQUIRES')))
}

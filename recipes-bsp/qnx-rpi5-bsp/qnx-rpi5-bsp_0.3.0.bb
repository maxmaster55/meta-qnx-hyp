SUMMARY = "Raspberry Pi 5 board support package, from the SDP"
DESCRIPTION = "startup-bcm2712-rpi5, devb-sdmmc-bcm2712, i2c-dwc-rpi5, \
devc-serpl011-rpi5, gpio-rp1, msix-rp1, spi-dwc and wdtkick -- the board \
binaries a hypervisor host image cannot boot without. They are not in \
${QNX_TARGET}: the Software Center delivers a BSP as a zip under \
${QNX_SDP_ROOT}/bsp, carrying prebuilt binaries alongside the source they were \
built from. The prebuilt half is unpacked into the stage tree, where mkifs \
already looks."
LICENSE = "CLOSED"

# The unpack, the staging and the skip-if-absent guard are all in the class,
# which the hypervisor guest BSP uses as well -- the two recipes differ only in
# which archive they name. Behaviour is unchanged from when this recipe carried
# that code itself.
inherit qnx-bsp

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

# What lands in the stage tree, and what an image names by bare name:
#
#   bin/   gpio-aon-bcm gpio-bcm gpio-rp1 mbox-bcm msix-rp1 wdtkick
#   sbin/  devb-sdmmc-bcm2712 devc-serpl011-rpi5 fan-rpi5 i2c-dwc-rpi5
#          rpi_gpio spi-dwc
#   boot/sys/startup-bcm2712-rpi5
#
# The guest image installs this too, which reads oddly for a board BSP and is
# right: guest-1 is handed the RP1's SPI and GPIO register windows straight
# through by its .qvmconf, so it drives the same silicon with the same gpio-rp1
# and spi-dwc the host would use.

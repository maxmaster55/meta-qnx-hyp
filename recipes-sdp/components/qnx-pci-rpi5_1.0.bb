SUMMARY = "pci-server for the Raspberry Pi 5, with every module it dlopens"
DESCRIPTION = "The PCI server, its bus-configuration and namespace modules, one \
capability handler per PCI and PCIe capability ID, the BCM2712 host-bridge \
module and the two config files it reads at startup. This is the component that \
motivated qnx-sdp-component: it is 33 files that are useless in any subset."
LICENSE = "CLOSED"

inherit qnx-sdp-component

# Nothing here is reachable from DT_NEEDED -- pci-server is built almost
# entirely out of dlopen'd modules -- so the shared-library closure in
# qnx-ifs.bbclass cannot supply any of it.
#
# Listing only the hw module, which is the part that looks board-specific and
# therefore the part you think about, produces a pci-server that starts, finds
# no way to enumerate a bus and exits before creating /dev/pci. The startup
# script's `waitfor /dev/pci` then times out and every driver behind it fails in
# terms that say nothing about PCI: devc-serpl011 "Could not get capability",
# io-sock never starts, gpio-rp1 "Can't set pin value" (it reaches the RP1 over
# PCI), and every later ifconfig "Address family not supported by protocol
# family". One missing module, five unrelated-looking symptoms.
#
#   pci_server-*  how the server enumerates buses and builds its namespace
#   pci_cap-*     one module per PCI capability ID, dlopen'd by ID as each is
#                 found -- these are what "Could not get capability" means
#   pcie_xcap-*   the same for PCIe extended capabilities
#   pci_hw-*      the board's host-bridge module
#   pci_debug*/pci_slog*  diagnostics, named by PCI_DEBUG_MODULE and
#                 PCI_SLOG_MODULE in the startup script
#
# The nested lib/dll/pci directory is not on mkifs's search path, so those are
# named by subpath.
QNX_COMPONENT_FILES = "\
    pci-server \
    pci-connector \
    pci-tool \
    libpci.so \
    pci/pci_hw-bcm2712-rpi5.so \
    pci/pci_bkwd_compat.so \
    pci/pci_strings.so \
    pci/pci_server-buscfg-generic.so \
    pci/pci_server-buscfg-hotplug.so \
    pci/pci_server-buscfg2-generic.so \
    pci/pci_server-buscfg2-hotplug.so \
    pci/pci_server-enable_features.so \
    pci/pci_server-event_handler.so \
    pci/pci_server-namespace.so \
    pci/pci_cap-0x01.so \
    pci/pci_cap-0x04.so \
    pci/pci_cap-0x05.so \
    pci/pci_cap-0x07.so \
    pci/pci_cap-0x09-ffffffff.so \
    pci/pci_cap-0x0d.so \
    pci/pci_cap-0x10.so \
    pci/pci_cap-0x10-16c3abcd.so \
    pci/pci_cap-0x10-19570400.so \
    pci/pci_cap-0x11.so \
    pci/pci_cap-0x11-ffffffff.so \
    pci/pci_cap-0x12.so \
    pci/pci_cap-0x13.so \
    pci/pcie_xcap-0x0001.so \
    pci/pcie_xcap-0x0003.so \
    pci/pcie_xcap-0x000b-ffffffff.so \
    pci/pcie_xcap-0x0015.so \
    pci/pci_debug.so \
    pci/pci_debug2.so \
    pci/pci_slog.so \
    pci/pci_slog2.so \
"

# pci-server reads these at startup. The .cfg carries the host bridge's PCIe gen
# speed, which is board data the hw module cannot infer.
QNX_IFS_EXTRA_ENTRIES = "\
/etc/system/config/pci/pci_server.cfg = {\n\
[buscfg]\n\
\n\
[runtime]\n\
BUS_SCAN_LIMIT=${QNX_PCI_BUS_SCAN_LIMIT}\n\
\n\
[envars]\n\
PCI_HW_MODULE=/lib/dll/pci/pci_hw-bcm2712-rpi5.so\n\
PCI_DEBUG_MODULE=pci_debug2.so\n\
\n\
}\n\
[uid=0 gid=0 perms=0444] /etc/system/config/pci/pci_hw-bcm2712-rpi5.cfg = {\n\
[PI5]\n\
MAX_GEN_SPEED=${QNX_PCI_MAX_GEN_SPEED}\n\
}\
"

QNX_PCI_BUS_SCAN_LIMIT ?= "3"
QNX_PCI_MAX_GEN_SPEED ?= "3"

# Started by the image, not here: the environment variables in front of it and
# the pci-connector/msix-rp1 sequence after it are board wiring that belongs
# with the board's startup script. What this component guarantees is that when
# that script runs, everything it needs exists.

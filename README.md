# meta-qnx-hyp

Project layer for the QNX hypervisor / Raspberry Pi 5 system. Contains **policy only** —
which applications exist and what the images look like. Every QNX mechanism comes from
[meta-qnx](../meta-qnx).

It also serves as meta-qnx's genericity test. If something board-specific has to be added
to meta-qnx to make this layer work, meta-qnx is not generic enough yet.

## What it builds

| Target | Result |
| --- | --- |
| `bitbake qnx-host-disk` | Flashable SD card image: FAT boot partition (Pi firmware + IFS) and a QNX6 data partition (built by `qnx-host-data`). With meta-qnx-guest in the build, the guest lands on the data partition. |
| `bitbake qnx-host-image` | Hypervisor host IFS for RPi5 — `qvm`, vdevs, PCI, board drivers, the GPU stack |
| `bitbake rpi-gpio` | GPIO resource manager (CMake, own GitHub repo) |
| `bitbake motor-controller` | SPI/ADC motor controller (the monorepo's `giga_spi_8adc`) |
| `bitbake qnx-host-conf` | Screen display configuration and its start script, plus the wifi credentials file |
| `bitbake vdev-virtio-gpu` | Host-side virtio-gpu vdev, with `virglrenderer` and `libepoxy` (meson) beneath it |

## Where sources come from

Application recipes inherit `qnx-src`. `rpi-gpio` and `vdev-virtio-gpu` (plus
`virglrenderer`/`libepoxy` forks) clone their own repositories; the rest still live in the
hypervisor monorepo and share [`conf/qnx-project-repo.inc`](conf/qnx-project-repo.inc) —
one place holding the repo URL, branch and revision for all of them.

Setting the monorepo checkout in `conf/local.conf` is still needed for the host image and
disk (see below), and it also flips every monorepo recipe to building that working tree in
place via `externalsrc`:

```bitbake
QNX_PROJECT_SRC = "/path/to/Qnx_Hypervisor_rbye"
```

Without it, monorepo recipes fetch from the repository instead, but `qnx-host-image` and
`qnx-host-disk` are skipped — they need the RPi5 BSP install tree and the Pi firmware
files, which only exist in the working tree.

## How the host image differs from a guest

This is the interesting part, and the reason this layer exists. A hypervisor host and its
guests are both aarch64le QNX, but almost nothing else about their boot is the same:

| | guest (meta-qnx defaults) | host (this layer) |
| --- | --- | --- |
| load address | `0x80000000` | `0x80000` |
| image format | `aarch64le,elf` | `aarch64le,raw -compress` |
| startup | `startup-armv8_fm -H` | `startup-bcm2712-rpi5 -v -u reg -a -W 5000 -Q enable,el1-host` |
| console | virtio-console | real PL011 UART (`/dev/ser10`) |
| bus | none | real PCI (`pci-server`, `msix-rp1`) |

All of it is expressed as `@VARIABLE@` values set by the image recipe. meta-qnx needed no
board knowledge to support it.

Two consequences worth knowing:

- `-W 5000` arms the **hardware watchdog**, so `wdtkick` running early is not optional —
  without it the board resets a few seconds into boot.
- `-Q enable,el1-host` is what actually turns the hypervisor on.

## Three mkifs search roots

Binaries resolve from three places, searched in order:

1. the recipe sysroot — applications built by this layer's recipes
2. `${QNX_PROJECT_SRC}/qnx_host/install` — the RPi5 BSP tree
3. `$QNX_TARGET` — everything standard (`qvm`, `pci-server`, `io-sock`, `vdev-*.so`)

The BSP tree is needed because `startup-bcm2712-rpi5`, `i2c-dwc-rpi5`,
`devc-serpl011-rpi5`, `gpio-rp1`, `msix-rp1` and `wdtkick` are built from the BSP sources
in `qnx_host/src` and are **not** part of the SDP. Building that BSP through Yocto is a
separate job; for now this layer consumes its output via `QNX_IFS_EXTRA_ROOTS`.

## Fidelity against the makefile build

The boot header of the generated image is byte-identical to the makefile-built
`qnx_host/images/ifs-rpi5-hyp.bin` — same load address, same startup size (`0x2a048`),
same entry point (`83808`), same flags.

The image now carries the GPU stack (`libepoxy`, `virglrenderer`, `vdev-virtio-gpu`); it
still does not carry the Qt cluster, the SomeIP services or ssh, and guest payloads live
on the data partition rather than inside the host IFS.

## Not done yet

1. **Nothing has booted.** Every check so far is static (`dumpifs`, `fdisk`, boot-header
   comparison). Flashing `qnx-host-disk.img` and watching serial is the highest-value next
   step.
2. **Standalone repositories** for `motor-controller` and `qnx-host-conf`
   (and `shm-chunker` in meta-qnx-guest). They build from the monorepo working tree via
   `QNX_PROJECT_SRC` and therefore have no sstate; each recipe records the one-line
   `QNX_SRC_REPO` change to make once its repo exists.
3. **The BSP itself** (`qnx_host/src` → `startup-bcm2712-rpi5`, board drivers), so the
   second mkifs search root disappears and `QNX_PROJECT_SRC` stops being required for the
   host image.

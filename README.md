# meta-qnx-hyp

Project layer for the QNX hypervisor / Raspberry Pi 5 system. Contains **policy only** —
which applications exist and what the images look like. Every QNX mechanism comes from
[meta-qnx](../meta-qnx).

It also serves as meta-qnx's genericity test. If something board-specific has to be added
to meta-qnx to make this layer work, meta-qnx is not generic enough yet.

## What it builds

| Target | Result |
| --- | --- |
| `bitbake qnx-host-image` | Hypervisor host IFS for RPi5 — `qvm`, vdevs, PCI, board drivers |
| `bitbake rpi-gpio` | GPIO resource manager (CMake) |
| `bitbake shm-chunker` | Shared-memory chunker (plain make) |

## Requirements

In `conf/local.conf`, in addition to meta-qnx's own settings:

```bitbake
QNX_PROJECT_SRC = "/path/to/Qnx_Hypervisor_rbye"
```

The applications are built from that working tree via `externalsrc`, and the RPi5 BSP
install tree under it supplies the board drivers (see below).

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

The images differ in size (≈9 MB vs ≈80 MB) because this one does not yet carry the Qt
cluster, the GPU stack (`libepoxy`, `virglrenderer`, `vdev-virtio-gpu`), the SomeIP
services, ssh, or the guest IFS payloads.

## Not done yet

1. **Guest images and the data partition.** The real host boots guests whose IFS images and
   `.qvmconf` files live on a QNX6 data partition, because an IFS is RAM-resident. Needs
   `mkqnx6fsimg` support in meta-qnx.
2. **`disk.img`** — FAT boot partition (`start4.elf`, `config.txt`, dtb, overlays) plus the
   data partition, assembled with `mkfatfsimg` and `diskimage`.
3. **The remaining applications** — `frame_router`, `giga_spi_8adc`, the SomeIP/CommonAPI
   stack, and the GPU libraries (meson; `src/qnx-aarch64le.ini` is already a cross file).
4. **The BSP itself**, so root 2 above disappears.

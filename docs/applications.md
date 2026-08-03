# The applications on these images

Every application is its own repository, fetched by a recipe. This is what each
one is, which image carries it, and the things about it that are not obvious
from the recipe.

## The map

| Application | Repository | Image | Started at boot? |
| --- | --- | --- | --- |
| `motor-data-producer` | `PM-Maestro-ITI-GP-Org/motor-data-producer` | host **and** guest-1 | no |
| `motor-ai-client` | `…/motor_ai_client` | guest-1 | by `start-guest1.sh` |
| `motor-ai-server` | `…/motor_ai_server` | guest-2 (Linux) | yes, systemd |
| `motor-recorder` | `…/motor-recorder` | guest-1 | no |
| `shm-chunker` | `…/shm-chunker` | — | no |
| `qt-cluster` | `…/qt-cluster` | guest-1 `rootfs.img` | by `start-guest1.sh` |
| `hms` | `…/hms` | host | no |
| `wifi-service` | `…/wifi-service` | host | no |
| `spi-loopback` | `…/spi_loopback` | guest-1 | no |
| `rpi-gpio` | `…/rpi-gpio` | both, via packagegroup | yes |

Supporting libraries: `mosquitto` (both images), `font-dejavu` (guest-1
`rootfs.img`), `packagegroup-qnx-someip` (guest-1).

## The motor data path

One producer, three consumers of the same shared-memory ring:

```
  motor_data_producer ──> /motor_ctrl ──┬──> motor_ai_client ──SOME/IP──> motor_ai_server (Linux)
   (SPI + ADC + control)                ├──> motor_recorder  ──MQTT────> broker
                                        └──> shm_chunker
```

`motor_wire.h` and `motor_shm.h` describe that ring and are staged by
`motor-data-producer` alone. Every consumer takes them from the sysroot rather
than vendoring a copy — building against a different copy than the producer was
built with is a silent wrong answer, not a link error.

### motor-data-producer

Was `motor-controller`, and before that `src/giga_spi_8adc`. Renamed upstream and
switched from a Makefile to CMake. Two things the rename moved:

- the binary follows the CMake target, so `motor_controller` is now
  `motor_data_producer` — anything on a board calling the old name breaks
- `config.json` goes to `/etc/motor/config.json`, because the source says
  `#define DEFAULT_CONFIG_PATH "/etc/motor/config.json"`. The old build put it
  at `/usr/bin/config.json`, which this binary does not look at

It also replaced a *different* recipe of the same name, which built
`Mintharah/SPI-Stm32-QNX` — an STM32 SPI reader, now retired. The two copies of
`motor_wire.h`/`motor_shm.h` were byte-identical, so no consumer changed.

Its `CMakeLists.txt` reaches for `../../rpi-gpio/resmgr/public`, a path that does
not exist even in the monorepo. It is left alone — a non-existent include
directory is a `-I` that matches nothing, not an error — and the real header
arrives through the sysroot via `DEPENDS = "rpi-gpio"`.

> The host no longer starts `spi-dwc`, so `/dev/io-spi` never appears there and
> the producer has no bus to open. The guest owns SPI0; both sides driving the
> same passed-through register window is a hardware conflict neither reports.

### motor-ai-server

The Linux half. It writes a CSV window and runs the AI app over it, then replies
with the verdict. The seam is one command and two files:

```
motor-ai-infer <window.csv> <result.json>     exit 0 = fresh verdict
```

so the model, the features and the class meanings can all change without the
server knowing. `/etc/motor-ai-server/server.conf` carries `window_rows`,
`csv_dir`, `infer_command`, `infer_timeout_ms` and `csv_keep`.

`window_rows` is also the dial for how often the service stalls: inference blocks
the SOME/IP reply, and the reply blocks the QNX client.

### motor-recorder

Third consumer of the ring. Writes CSV and publishes over MQTT. The broker is a
compile-time constant (`MQTT_BROKER` in `mqtt_client.h`), so retargeting it means
changing the application. Its save directory is `-d <dir>`, defaulting to `/tmp`
— which is RAM on the guest, so recordings meant to survive a reboot want a path
on the mounted data disk.

## hms — Hypervisor Management System

Runs on the host. Discovers guests under `/guests`, starts and stops them through
`qvm`, and takes commands over MQTT so a GUI client elsewhere can drive the
board. OTA packages move by `scp`.

Installed but **not started**: it reaches a broker over the network and can stop
and start guests, so when it runs is a decision rather than a default.

It needs an ssh key pair that no layer supplies. See
[ssh.md](../../meta-qnx/docs/ssh.md) — `QNX_SSH_IDENTITY` in `local.conf` is the
one thing you must set for it to reach a guest at all.

`/etc/hms.conf` is in the IFS and therefore read-only. Changing the broker
address means editing it here and rebuilding.

## wifi-service

Gets WiFi credentials onto a board with no keyboard, using a phone. **The board
is a WiFi client throughout — it never becomes an access point.** The phone runs
the hotspot:

| State | What happens |
| --- | --- |
| `TRY_REAL` | `wpa_supplicant -D qwdi` with `wpa_supplicant_real.conf` if it exists; 25 s |
| `TRY_DEFAULT` | write the default config, join the **phone's** hotspot `QNX_wifi`; 15 s |
| `ON_DEFAULT` | `dhcpcd`, take the phone's address from the gateway, connect **out** to it on TCP 9999, read `{"ssid","password"}`, write `wpa_supplicant_real.conf`, go back to `TRY_REAL` |

Its README describes a hostapd/access-point/TCP-8888 design that the code does
not implement. Read the code.

Not started at boot: `.wifi-start.sh` already associates with the configured
network, and this takes `bcm0` down to join the provisioning hotspot — which
drops the link the board is being administered over.

Two configuration files, and only one of them changes anything:

- `wpa_supplicant_default.conf` is regenerated before every use, so the shipped
  copy just makes the provisioning SSID readable on the board
- `wpa_supplicant_real.conf` **is read if present** — shipping one
  pre-provisions the board and skips the phone entirely

Both live on the data partition, because the service rewrites them and an IFS is
read-only. `/etc/wifi` must exist and be writable, or both `fopen(…, "w")` calls
fail silently and the service cycles forever without associating.

## mosquitto

libmosquitto only — no broker, no clients, no TLS. `motor-recorder` and `hms`
both link it, and an image with the binary and not the library gets a process
that dies at startup with `ELIBACC`, naming nothing useful.

Pinned to the **v2.0.20 tag**, which is not what the project's own
`cross_compile_qnx.sh` builds: that script clones with no branch argument, so it
gets the default branch into a directory merely *named* `mosquitto-2.0.20`. The
difference is not academic — on master, `libcommon/CMakeLists.txt` hard-fails
without `getrandom()`, which is why that script carries ~100 lines of shell and
python to stub it. At this tag none of that applies.

`DOCUMENTATION=OFF`, not `WITH_DOCS=OFF` — the man pages are the one option that
does not take the `WITH_` prefix, and the wrong name fails on `xsltproc: not
found` *after* the library has built, which reads as a library problem.

## Things that bite when splitting a repository out

Every application split out of the monorepo needed the same two repairs, so
expect them on the next one:

**The Makefile sources the SDP itself.** `. "$(QNX800_DIR)/qnxsdp-env.sh"` with
`QNX800_DIR := ../../qnx800` only resolves inside the monorepo, and it takes the
choice of compiler away from whatever is driving the build. The environment is
the caller's job; paths outside the repository become variables.

**`CC ?=` does not work.** make predefines `CC` as `cc`, so its origin is
`default`, not `undefined`, and `?=` leaves it alone. The build then silently
uses the host gcc and dies on the first QNX header:

```
recorder.c:15:10: fatal error: sys/neutrino.h: No such file or directory
```

which reads like a missing SDP rather than a wrong compiler. Use
`ifeq ($(origin CC),default)`.

**Headers by relative path.** `#include "../../../motor_data_producer/QNX-SPI/motor_wire.h"`
resolves only in the monorepo. Include by name and let the sysroot supply it.

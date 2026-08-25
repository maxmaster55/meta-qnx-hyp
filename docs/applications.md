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
| `guest-launch` (`start-guests.sh`) | this tree | host | **yes**, from the boot script |
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

The Linux half, and now two processes rather than one. The service used to spawn
`motor-ai-infer` per window and wait for it to exit; it now writes the window to
a fixed path and signals a long-running `motor_ai_node`, which sleeps between
requests instead of being started and stopped for each one.

The seam is one directory, `data_dir` (`/motor_data`), and three signals:

```
/motor_data/input_data/data.csv     the window, written by the server
/motor_data/results/result.ini      one stage's verdict, written by the node
/motor_data/ai.pid                  the node's pid, so the server can signal it

SIGUSR1 -> anomaly       always
SIGUSR2 -> fault class   only when anomaly != "normal"
SIGTERM -> RUL           only when anomaly != "normal"
```

Both files are written under a temporary name and renamed into place, because
the reader on each side polls for existence and reads the moment the file
appears — "it is there" has to mean "it is complete". `result.ini` carries
`stage=` as well as `value=`, so a late answer to a timed-out stage cannot be
read as the next stage's.

A healthy window costs one model and an anomalous one costs three, which is the
point of the split.

`/etc/motor-ai-server/server.conf` carries `window_rows`, `data_dir`,
`ai_pid_file`, `result_timeout_ms` and the three `sig_*` values. The last four
are a contract with the node's `/etc/motor-ai-node/node.conf` — nothing checks
that the two agree, and a mismatch just means the server waits out
`result_timeout_ms` per window with no distinctive error.

`window_rows` is still the dial for how often the service stalls: inference
blocks the SOME/IP reply, and the reply blocks the QNX client. It is 26000 on
both sides, so one client call is one window is one inference.

> `sig_rul = SIGTERM` is the specified default and a poor one — SIGTERM's
> default disposition terminates, so any ordinary `kill` or `systemctl stop`
> looks like a request to run the model. The node publishes its pid only after
> installing handlers and takes SIGINT as "stop", and its unit sets
> `KillSignal=SIGINT` to match. Setting `sig_rul = SIGRTMIN` on both sides
> removes the collision instead of working around it.

The models are not implemented. `motor_ai_node` reads the window, counts the
rows and returns a fixed verdict per stage — enough to exercise the handshake
with the real layout and timing. `ai-app` (the TFLite `motor_infer` and its
models) is still installed but no longer wired to anything; the intended end
state is a signal-driven build of that repository replacing
`motor-ai-server-node`.

### motor-recorder

Third consumer of the ring. Writes CSV and publishes over MQTT. The broker is a
compile-time constant (`MQTT_BROKER` in `mqtt_client.h`), so retargeting it means
changing the application. Its save directory is `-d <dir>`, defaulting to `/tmp`
— which is RAM on the guest, so recordings meant to survive a reboot want a path
on the mounted data disk.

## guest-launch — the guests start themselves

`/scripts/start-guests.sh`, run by the boot script from `QNX_IFS_STARTUP_CMD`.
It walks `/guests`, launches one qvm per directory holding a `.qvmconf`, and
skips anything already running — so it is safe to re-run by hand. The launch
copies hms's `guest_start()` exactly (cwd, stdio cut off, output to
`qvm.log`), which is not imitation for its own sake: hms's discoverer adopts a
running guest it did not start only when it can match a qvm process to the
guest, and matching works off the command line and log layout this script
produces.

Ordering, all three ends load-bearing:

| | why |
| --- | --- |
| after the data partition mount | `/guests` is on it; earlier there is nothing to launch |
| after `.record-create.sh` | guest-1's `.qvmconf` attaches `record.img`; on a fresh card that file exists only once that script has made it |
| before hms | the manager comes up to guests already running and adopts them, instead of starting from an empty list |

Which guests start is decided by which directories are on the disk — the same
rule hms discovers by. To keep a guest out of autostart, remove it from the
disk, not from the script.

## hms — Hypervisor Management System

Runs on the host. Discovers guests under `/guests`, starts and stops them through
`qvm`, and takes commands over MQTT so a GUI client elsewhere can drive the
board. OTA packages move by `scp`.

**The binary lives on the data partition, not the IFS.** It is
`QNX_ROOTFS_INSTALL` in `qnx-host-data_1.0.bb`, not `QNX_IFS_INSTALL` in
`qnx-host-image_1.0.bb` — deliberately, since `hms` changes far more often than
anything else in this image and used to cost a full image rebuild and reflash
per fix. Updating it now is `scp build/hms root@host:/bin/hms`; `hms.conf`
still lives in the IFS (see below) and still needs a rebuild to change.

**Started at boot, but not immediately.** `.hms-start.sh` waits for the wifi to
have an address before exec'ing it, because hms's whole job is on the far side
of a broker on the public internet and the route there comes from the dhcpcd
lease. Started earlier it comes up, fails to connect and retries in the
background — harmless, but it interleaves with the wifi's own output on the
same console and makes a boot much harder to read.

| | default | |
| --- | --- | --- |
| `QNX_HOST_HMS_WAIT` | `60` | seconds to wait for `bcm0` to get an address |
| `QNX_HOST_HMS_PRIORITY` | `20` | what hms is spawned at |

The wait is bounded and hms starts either way. A board whose wifi never
associates is exactly the board someone needs to reach, hms retries the broker
on its own, and the wired link may route to it anyway.

The priority is not decoration — see [Priorities](#priorities) below. And it is
applied with a **full path**: `on` resolves the program itself rather than
inheriting the boot script's PATH, and reports the miss as

```
on: No such file or directory (hms)
```

which reads as `on` being absent when `on` ran perfectly well.

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

## Priorities

Who gets the CPU when the board is busy. Two separate namespaces — a guest's
internal priorities have nothing to do with the host's — but they interact
through the vCPU threads, which are host threads running guest code.

**On the host:**

| | priority | |
| --- | --- | --- |
| io-sock | 21 | the network stack; nothing below may starve it |
| hms | **20** | `QNX_HOST_HMS_PRIORITY` |
| guest AP vCPU threads | **20** | `QNX_GUEST_VCPU_AP_SCHED`, see [vcpus.md](../../meta-qnx-guest/docs/vcpus.md) |
| guest boot vCPU thread | 10 | qvm's own priority |
| everything else | 10 | procnto's default |

hms at 20 rather than the default 10 because 10 is *below* the guest vCPUs,
which leaves the manager beneath the thing it manages. Equal to them rather
than above: round-robin at the same priority makes them timeslice, where
putting hms higher would let a stats poll preempt the guest whose statistics it
is collecting.

**Inside the QNX guest:**

| | priority | |
| --- | --- | --- |
| spi-dwc | 30 | the SPI driver — measured at ~36% of the guest's CPU |
| sshd | **15** | `QNX_GUEST_SSHD_PRIORITY` |
| the applications | 10 | Qt cluster, motor producer/recorder, AI client |

sshd above the applications because an ssh key exchange is CPU *in the guest*,
and at equal priority a handshake queues behind a software-rendered cluster.
Below spi-dwc because a login must not preempt the SPI driver.

> The guest sshd priority is reasoned, not measured. When it was set, a
> handshake into an idle guest measured 339 ms and the guest was ~58% busy —
> which is to say the ordering was not being tested at the time. If ssh into
> the guest is ever slow again, `pidin -P sshd -f narpS` inside it during a
> handshake settles it: `READY` rather than `RUNNING` means it is still waiting
> for CPU and 15 was not enough.

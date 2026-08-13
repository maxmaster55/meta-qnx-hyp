# Guest networking and internet access

Three networks, two of which the host routes for:

```
             LAN 192.168.2.0/24            wifi 10.145.0.0/16
                     │                            │
                 cgem0│                           │bcm0
                     └──── bridge0 ────┐   ┌──────┘
                          192.168.2.2  │   │
                          ┌────────────┴───┴────────────┐
                          │        QNX host             │
                          │  vp0 10.0.0.1  vp1 10.0.1.1 │
                          └────┬───────────────┬────────┘
                               │               │
                       10.0.0.2│               │10.0.1.2
                        guest-1 (QNX)    guest-2 (Linux)
                               └──── 10.0.2.0/24 ────┘
                                  direct, no host
```

`10.0.2.0/24` is the SOME/IP wire between the two guests and is not routed anywhere.
The other two are point-to-point links to the host, which forwards and NATs them.

## What a guest needs, in order

| | where |
| --- | --- |
| 1. an address and a default route | the guest's own boot script / `network-setup.sh` |
| 2. `net.inet.ip.forwarding=1` on the host | `qnx-host.build.in` |
| 3. a pf `nat` rule matching the interface the packets **leave through** | `/etc/pf-nat.conf` |
| 4. a resolver | the guest's `/etc/resolv.conf` |

Steps 1 and 2 were already there. 3 was loaded in a way that silently failed, and 4 did
not exist — so a guest could reach `10.0.0.1` and nothing beyond it.

## `pfctl -f` does not work on this SDP

This is the part worth knowing, because the failure is invisible.

```
# pfctl -f /etc/pf.conf
pfctl: pfctl_rules
pfctl: DIOCXROLLBACK: Invalid argument
```

Every ruleset fails this way, including a file containing nothing but `pass all`. It is
not the rules. `pfctl -n -f` (parse, don't load) accepts the same file, and `pfctl -s
info` does not merely fail but aborts:

```
.../sys/contrib/libnv/nvlist.c:371: Element 'reass' of type NUMBER doesn't exist.
Abort (core dumped)
```

`pfctl` and the pf inside `io-sock` disagree about the shape of pf's ioctl nvlists. They
come from the same SDP but not the same package: a networking update dropped a newer
`io-sock`, `libsocket` and the vtnet drivers into the install without a matching `pfctl`.

```
sbin/io-sock      Jul 31 22:32     ← networking update
sbin/pfctl        Jul 31 17:43     ← base install
```

Loading a **single** ruleset avoids the part they disagree on:

```sh
pfctl -e
pfctl -N -f /etc/pf-nat.conf     # -N: nat ruleset only
```

That is what the image does now, and it is what the reference project did — the split
`-R`/`-N` form is not legacy style there, it is the only form that works.

Consequences to remember:

- **No filter rules.** pf's default is to pass, so an empty filter ruleset is the open
  policy the board already had. Plain `pass`/`block` rules do load through `-R` if
  filtering is ever wanted; the reference's `set skip on …` lines are what make `-R` fail
  here, with `Must enable table loading for optimizations`.
- **`pfctl -s nat` aborts.** The display path hits the same mismatch on a different
  element (`dnpipe`). The rules are loaded regardless — `pfctl -N -f` returning 0 is the
  check that means something. Test with traffic, not with `-s`.

## NAT is on both uplinks, deliberately

```
nat on bridge0 inet from 10.0.0.0/24 to any -> (bridge0)
nat on bridge0 inet from 10.0.1.0/24 to any -> (bridge0)
nat on bcm0    inet from 10.0.0.0/24 to any -> (bcm0)
nat on bcm0    inet from 10.0.1.0/24 to any -> (bcm0)
```

Which one the board actually uses is not a build-time fact. The image sets a static
default route through `bridge0`, and then `.wifi-start.sh` runs `dhcpcd` on `bcm0`, whose
lease installs a default route that replaces it:

```
default            10.145.0.1         UG             bcm0
```

A guest packet then leaves through `bcm0` while the only `nat` rule says `bridge0`, so it
is not translated at all — `10.0.0.2` goes out onto a network that has never heard of it,
and the replies go nowhere. It looks exactly like NAT not working, because it is.

Naming both uplinks costs one unused rule. pf applies `nat` only on the interface a packet
really leaves through, and it accepts a rule naming an interface that does not exist —
which `bcm0` does not, at the point in the boot script where these load. The `-> (iface)`
parentheses matter for the same reason: the address is read when a packet matches, not
when the rule loads, so these survive `bridge0` being addressed and the wifi associating
afterwards.

Set by `QNX_HOST_NAT_IFS` and `QNX_HOST_NAT_NETS`; one rule is generated per pair.

## The resolver

Routing without a resolver is the failure that reads as a routing failure:

```
# ping 8.8.8.8            -> replies
# ping www.google.com     -> cannot resolve: Name does not resolve
```

Nothing supplies `/etc/resolv.conf` in either guest. The SDP contributes `/etc/hosts`,
`services` and `protocols` and stops there, and no DHCP client runs in a guest — the
addresses are static. Both guests now ship one:

| guest | variable / file | default |
| --- | --- | --- |
| guest-1 (QNX) | `QNX_GUEST_DNS` → `/etc/resolv.conf` | `8.8.8.8 8.8.4.4` |
| guest-2 (Linux) | `network-setup.sh`, appended if no `nameserver` line exists | `8.8.8.8 8.8.4.4` |

Public resolvers rather than the LAN gateway, which is the host's own default
(`QNX_HOST_DNS`): when the board's uplink is the wifi, the gateway's address is on a
subnet the guest's translated traffic never reaches.

## Checking it on the board

```bash
ssh root@192.168.2.2 'pfctl -N -f /etc/pf-nat.conf; echo $?'
```

`0` means loaded. Then, from the host, through the guest:

```bash
ssh root@192.168.2.2 'ssh root@10.0.0.2 "ping -c3 8.8.8.8; ping -c2 www.google.com"'
```

If the addresses answer and the name does not, it is step 4, not step 3. If neither
answers, check which interface holds the default route — `netstat -rn | head -3` on the
host — and whether `pf-nat.conf` names it.

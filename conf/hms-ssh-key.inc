# The key pair hms uses to reach the guests it manages.
#
# hms runs on the host and ssh's into each guest to start, stop and inspect it,
# so the two halves live in different images:
#
#   the host   carries the PRIVATE key at /root/.ssh/id_ed25519
#   each guest authorises the PUBLIC one in /root/.ssh/authorized_keys
#
# Only the public half is here. It is a public key, so stating it outright is
# what it is for -- and it means a guest image can authorise hms without
# depending on hms or on anything outside its own layer.
#
# The private half is deliberately absent, and is not in the monorepo this came
# from either: that build file reads it from a path that is gitignored. Point
# QNX_SSH_IDENTITY at it in local.conf, next to QNX_SDP_ROOT:
#
#     QNX_SSH_IDENTITY = "/path/to/hms_ssh_key"
#
# Without it the host image still builds and hms still starts -- it just cannot
# log into any guest, and says so per connection rather than at build time.
QNX_HMS_PUBKEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJ63lkUzvugd3TnG6SQDUD4QYZH9iU5c+HihwiLP9D79 hms@hypervisor"

# The second key the reference guest-1 authorises, alongside hms's.
#
# Its private half is not in the monorepo and nothing there generates it -- the
# comment "root@localhost" is the only trace of it, and it appears in exactly
# one file. It is almost certainly a key made by hand on a board or a laptop so
# a person could ssh into the guest without a password, which is why guest-1 has
# it and guest-2, added later, does not.
#
# It is carried because the reference carries it and because a public key with
# no matching private half anywhere costs nothing: it authorises whoever holds
# that half and no one else. If that is nobody, drop this line -- an authorised
# key whose owner is unknown is worth removing rather than keeping out of
# caution.
QNX_HOST_ROOT_PUBKEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIA+xA4A9tvpQOgBiV7FWSxCLuAn/15Dtyq1HhCBC8xgo root@localhost"

SUMMARY = "Guest ssh host keys, generated at build time and pre-accepted by the host"
DESCRIPTION = "Generates one ed25519 host key per guest and the matching \
known_hosts for the hypervisor host. The guest images ship their own key so they \
stop generating one at first boot, and the host ships the known_hosts so it \
already knows them -- which is what turns host-to-guest ssh from a prompt into a \
verified connection."
LICENSE = "CLOSED"

inherit qnx-sdp

# Nothing is fetched or compiled from source; the keys are made here.
SRC_URI = ""
S = "${WORKDIR}"
do_configure[noexec] = "1"

# ---------------------------------------------------------------------------
# What to generate
# ---------------------------------------------------------------------------
# One entry per guest: "<name>:<address>". The name picks the staged directory,
# the address is what goes in known_hosts -- so it has to match what the host
# actually dials, not what the guest calls itself.
#
# guest-2 is deliberately absent. It is Linux and runs dropbear, whose host keys
# are in dropbear's own format rather than OpenSSH's, and converting one needs
# dropbearconvert -- which this build has no native recipe for. Shipping an
# OpenSSH key there would not be read, and putting guest-2 in known_hosts anyway
# would be worse than leaving it out: the host would then hold a key the guest
# does not have, turning a first-connection prompt into a hard mismatch.
#
# It is pinned on first contact instead, by StrictHostKeyChecking=accept-new --
# no prompt either way, and once learned the entry is as good as this one.
SSH_HOSTKEY_GUESTS ?= "guest-1:10.0.0.2"

SSH_HOSTKEY_TYPE ?= "ed25519"

# ---------------------------------------------------------------------------
# The identity question
# ---------------------------------------------------------------------------
# Generating at build time means every board flashed from one build shares a
# guest identity. That is exactly what ssh-server.sh refuses to do for the HOST
# key, and the reasoning there still holds: a key baked into an image is the
# same key on every board, so the identity proves nothing about which board you
# reached.
#
# It is the right trade here and the wrong one there, because the two links are
# not comparable:
#
#   host key    reached from a laptop, over a LAN, by a person. Generated on
#               first boot, kept in /var/ssh, unique per board.
#   guest keys  reached only by hms, only from the host it runs on, over a
#               point-to-point virtual wire that exists inside one board and
#               has no route off it. Nothing outside the board can present
#               itself as 10.0.0.2.
#
# What it buys is that the host can verify the guest instead of accepting
# whatever answers -- StrictHostKeyChecking can go back to yes. The alternative
# is the guest generating its own key on first boot, which nothing on the host
# could then have pre-accepted, which is why hms passes
# StrictHostKeyChecking=no today and verifies nothing at all.
#
# The keys are stable across rebuilds because do_compile is cached in sstate;
# `bitbake -c cleansstate ssh-hostkeys` deliberately mints new ones, and both
# images then have to be rebuilt together or the host's known_hosts will name
# keys the guests no longer have.

SSH_HOSTKEY_STAGE = "${QNX_STAGE_DIR}/ssh-hostkeys"

do_compile() {
	rm -rf ${B}/keys
	install -d ${B}/keys

	: > ${B}/keys/known_hosts

	for entry in ${SSH_HOSTKEY_GUESTS}; do
		name="${entry%%:*}"
		addr="${entry##*:}"

		if [ -z "$name" ] || [ -z "$addr" ] || [ "$name" = "$addr" ]; then
			bbfatal "SSH_HOSTKEY_GUESTS entry '$entry' is not <name>:<address>"
		fi

		install -d ${B}/keys/$name
		# -N "" because sshd starts unattended and cannot be given a
		# passphrase. -C names the guest so the key is identifiable in a
		# known_hosts that will grow more of them.
		ssh-keygen -q -t ${SSH_HOSTKEY_TYPE} -N "" -C "$name" \
			-f ${B}/keys/$name/ssh_host_${SSH_HOSTKEY_TYPE}_key

		# The address first, then the key type and blob -- ssh looks the host
		# up by exactly the string it dialled.
		printf '%s %s\n' "$addr" "$(cut -d' ' -f1,2 ${B}/keys/$name/ssh_host_${SSH_HOSTKEY_TYPE}_key.pub)" \
			>> ${B}/keys/known_hosts
	done

	if [ ! -s ${B}/keys/known_hosts ]; then
		bbfatal "no host keys generated -- is SSH_HOSTKEY_GUESTS empty?"
	fi
}

do_install() {
	install -d ${D}${SSH_HOSTKEY_STAGE}
	cp -a ${B}/keys/. ${D}${SSH_HOSTKEY_STAGE}/

	# sshd refuses a private key it considers loosely permissioned, and says so
	# only in its log -- so the modes are set here rather than hoped for.
	find ${D}${SSH_HOSTKEY_STAGE} -name 'ssh_host_*_key' -exec chmod 0600 {} \;
	find ${D}${SSH_HOSTKEY_STAGE} -name 'ssh_host_*_key.pub' -exec chmod 0644 {} \;
	chmod 0644 ${D}${SSH_HOSTKEY_STAGE}/known_hosts
}

# Nothing here belongs in an IFS by the automatic pass: these files are placed
# on writable storage by whichever image wants them.
QNX_IFS_AUTO_ENTRIES = "0"
QNX_IFS_STAGE_ONLY = "1"

do_compile[vardeps] += "SSH_HOSTKEY_GUESTS SSH_HOSTKEY_TYPE"

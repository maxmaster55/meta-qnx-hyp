#!/bin/ksh
#
# /scripts/start-guests.sh -- launch every discovered guest under qvm.
#
# Run from the host boot script (guest-launch's QNX_IFS_STARTUP_CMD), at a
# point where everything a guest needs already exists: the data partition is
# mounted, so /guests is populated; Screen is up, which vdev-virtio-gpu gates
# its EGL context on -- a qvm whose guest touches the GPU before that exists
# exits outright; and the vdevpeer interfaces are bound. hms starts later and
# adopts these guests as its own: its discoverer accepts "a qvm in /proc whose
# command line names this guest" regardless of who started it, so guests
# launched here are start/stop-able from the GUI exactly as if hms had run
# them.
#
# The launch itself copies hms's guest_start() deliberately -- cwd set to the
# guest directory, stdin cut off, output to qvm.log beside the config -- so a
# guest started here and one started through the broker are indistinguishable:
# same process shape, same cmdline evidence for the adopt scan, same log
# location when something goes wrong.
#
# Guests that are already running are skipped, which makes this safe to re-run
# by hand after adding a guest or reviving a board.

GUESTS_DIR=/guests
QVM=/sbin/qvm

if [ ! -d $GUESTS_DIR ]; then
    echo "guests: $GUESTS_DIR does not exist -- is the data partition mounted?"
    exit 0
fi

started=0

for dir in $GUESTS_DIR/*/ ; do
    test -d $dir || continue

    id=${dir#$GUESTS_DIR}
    id=${id%/}

    # One launch config per guest is the arrangement everywhere else, and hms's
    # own pick prefers *.qvmconf. First here wins; any surplus is named rather
    # than silently ignored, because "which config ran" is exactly the question
    # asked after a guest comes up wrong.
    conf=
    extra=0
    for f in $dir*.qvmconf; do
        test -f $f || continue
        if [ -z "$conf" ]; then
            conf=$f
        else
            extra=$((extra + 1))
        fi
    done

    if [ -z "$conf" ]; then
        echo "guests: $id has no .qvmconf -- skipping"
        continue
    fi
    if [ $extra -gt 0 ]; then
        echo "guests: $id has more than one .qvmconf -- using ${conf##*/}"
    fi

    conf_name=${conf##*/}

    # Skip a guest that is already up. /dev/qvm/<system> exists for exactly as
    # long as its qvm does -- the same signal hms trusts most after its own pid
    # record. A config without a system line cannot be checked this way and is
    # simply launched; qvm will have its own opinion about a second instance.
    system=$(grep '^system' $conf 2>/dev/null | head -n 1 | cut -d ' ' -f 2)
    if [ -n "$system" ] && [ -e /dev/qvm/$system ]; then
        echo "guests: $id already running (/dev/qvm/$system) -- skipping"
        continue
    fi

    echo "guests: starting $id ($conf_name) ..."
    (
        cd $dir || exit 1
        exec < /dev/null
        exec > qvm.log 2>&1
        exec $QVM @$conf_name
    ) &
    started=$((started + 1))
done

echo "guests: $started launched; per-guest output in /guests/<id>/qvm.log"
exit 0

#!/bin/ksh
#
# Mount the QNX6 data partition on /.
#
# This is why `ls /` on a board without it shows only what the IFS carries. The
# IFS is RAM-resident and deliberately small; /guests/guest-1/ (the guest IFS,
# its .qvmconf and rootfs.img) and everything else large is written to the
# disk's second partition by qnx-host-data. Until this runs, those files are on
# the SD card and not in the namespace.
#
# Mounting on / is a *union* mount in QNX: the IFS stays visible underneath and
# the disk's contents overlay it. That is why the mount point is / and not /mnt,
# and what the guest .qvmconf's absolute paths assume.

x=1
while [ $x -le @QNX_STORAGE_WAIT@ ]
do
    if [ -b @QNX_STORAGE_PART@ ]
    then
        echo "Mounting @QNX_STORAGE_PART@ on / ..."
        mount -t qnx6 @QNX_STORAGE_PART@ /
        break
    fi

    x=$(( $x + 1 ))
    sleep 1
done

if [ ! -b @QNX_STORAGE_PART@ ]
then
    echo "WARNING: @QNX_STORAGE_PART@ never appeared; no guests, no Qt."
    echo "         Check that the SDMMC driver started, and that the disk was"
    echo "         written whole (bmaptool copy / dd of the full .img)."
fi

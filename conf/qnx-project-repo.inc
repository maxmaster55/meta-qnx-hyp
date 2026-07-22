# The QNX hypervisor project repository, shared by every recipe that builds an
# application living inside it.
#
# Over SSH because that is how the repository is published. bitbake passes
# SSH_AUTH_SOCK through to tasks, so an agent-loaded key works; without one the
# fetch fails with a permission error from git rather than anything cryptic.
QNX_PROJECT_REPO ?= "git://git@github.com/PM-Maestro-ITI-GP-Org/Qnx_Hypervisor_rbye.git;protocol=ssh"

# Not "main". Three of the applications built from this repo -- giga_spi_8adc,
# shm_sender and the wifi configuration -- exist only on this branch so far.
# Move this to main once they are merged; it is a one-line override in
# local.conf in the meantime.
QNX_PROJECT_BRANCH ?= "wifi_fix"

# Every recipe tracks the branch head by default. Pin this to a commit for a
# reproducible, offline build:
#
#     QNX_PROJECT_REV = "f78036f..."
QNX_PROJECT_REV ?= "${AUTOREV}"

QNX_SRC_REPO ?= "${QNX_PROJECT_REPO};branch=${QNX_PROJECT_BRANCH}"
QNX_SRC_BRANCH ?= "${QNX_PROJECT_BRANCH}"
QNX_SRC_REV ?= "${QNX_PROJECT_REV}"

# One clone serves every recipe: bitbake keeps the bare repository in DL_DIR and
# each recipe checks out its own subdirectory from it.
QNX_SRC_LOCAL ?= "${QNX_PROJECT_SRC}"

#ifndef _SYS_EVENTFD_H
#define _SYS_EVENTFD_H
#include <fcntl.h>
#ifndef EFD_SEMAPHORE
#define EFD_SEMAPHORE (1 << 0)
#endif
#ifndef EFD_NONBLOCK
#define EFD_NONBLOCK O_NONBLOCK
#endif
#ifndef EFD_CLOEXEC
#define EFD_CLOEXEC O_CLOEXEC
#endif
static inline int eventfd(unsigned int initval, int flags)
{
    (void)initval; (void)flags;
    return -1;
}
#endif

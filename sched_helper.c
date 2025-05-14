#define _GNU_SOURCE
#include <errno.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/capability.h>
#include <sys/prctl.h>
#include <unistd.h>

int main(int argc, char *argv[]) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <executable_path> [args...]\n",
            argv[0]); // Generalized for any executable
    return 1;
  }

  // 1. Allow keeping capabilities after exec (e.g., if UID changes, though less
  // critical here)
  if (prctl(PR_SET_KEEPCAPS, 1L, 0L, 0L, 0L) != 0) {
    perror("[ERROR] prctl(PR_SET_KEEPCAPS)");
    goto error;
  }

  // 2. Prepare to activate the CAP_SYS_NICE capability
  cap_t caps = cap_get_proc();
  if (!caps) {
    perror("[ERROR] cap_get_proc");
    goto error;
  }

  cap_value_t cap_list[] = {CAP_SYS_NICE};
  // Set CAP_SYS_NICE in Effective, Permitted, and Inheritable sets for the
  // current process
  if (cap_set_flag(caps, CAP_EFFECTIVE, 1, cap_list, CAP_SET) != 0 ||
      cap_set_flag(caps, CAP_PERMITTED, 1, cap_list, CAP_SET) != 0 ||
      cap_set_flag(caps, CAP_INHERITABLE, 1, cap_list, CAP_SET) != 0) {
    perror("[ERROR] cap_set_flag");
    cap_free(caps);
    goto error;
  }

  if (cap_set_proc(caps) != 0) {
    perror("[ERROR] cap_set_proc");
    cap_free(caps);
    goto error;
  }
  // At this point, the current process has CAP_SYS_NICE in P, E, I sets.
  // We no longer need the caps object itself.
  cap_free(caps);

  // 3. Raise CAP_SYS_NICE into the ambient capability set.
  // This is crucial for the capability to be inherited by the executed program
  // if the program does not have file capabilities.
  // The capability must be in the Permitted and Inheritable sets to be raised.
  if (prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, CAP_SYS_NICE, 0, 0) != 0) {
    perror("[ERROR] prctl(PR_CAP_AMBIENT_RAISE for CAP_SYS_NICE)");
    // Check kernel version if this fails; requires Linux 4.3+
    // Also ensure CAP_SYS_NICE is in permitted and inheritable sets.
    goto error;
  }

  // 4. Optional: Test if sched_setscheduler works for the current process
  // (debug)
  // struct sched_param sp;
  // sp.sched_priority = 1; // Real-time priority
  // if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0) {
  //   perror("[ERROR] sched_setscheduler");
  //   goto error;
  // } else {
  //   fprintf(stderr, "[INFO] sched_setscheduler() with SCHED_FIFO "
  //                   "worked (This test is in the launcher)\n");
  //   sp.sched_priority = 0;                   // Standard priority
  //   sched_setscheduler(0, SCHED_OTHER, &sp); // Revert for the launcher
  //   itself
  // }

  // 5. Execute the target program with the remaining arguments
  // (e.g., /usr/bin/cat or your Java application)
  execv(argv[1], &argv[1]);

  // If execv fails:
  fprintf(stderr, "[ERROR] execv(%s) failed: %s\n", argv[1], strerror(errno));
  return 1; // execv only returns on error

error:
  fprintf(stderr,
          "> Make sure the process has CAP_SYS_NICE\n"
          "> Did you run this command before?\n"
          "   sudo setcap cap_sys_nice+ep %s\n",
          argv[0]);

  return 1;
}
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.NativeLong; // Import NativeLong
import java.util.Arrays;
import java.util.List;

interface LinuxLibC extends Library {
    LinuxLibC INSTANCE = Native.load("c", LinuxLibC.class);

    LinuxLibC.pthread_t pthread_self();

    int pthread_setschedparam(LinuxLibC.pthread_t th, int policy, LinuxLibC.sched_param param);

    void sched_yield();

    // Add syscall function
    // The signature is: long syscall(long number, ...);
    NativeLong syscall(NativeLong number, Object... args);

    class sched_param extends Structure {
        public int sched_priority;

        public sched_param() {
        }

        public sched_param(int priority) {
            this.sched_priority = priority;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("sched_priority");
        }

        public static class ByReference extends sched_param implements Structure.ByReference {
        }

        public static class ByValue extends sched_param implements Structure.ByValue {
        }
    }

    // New structure for SCHED_DEADLINE parameters
    class sched_attr extends Structure {
        public int size;
        public int sched_policy;
        public long sched_flags;
        public int sched_nice;
        public int sched_priority;
        public long sched_runtime;
        public long sched_deadline;
        public long sched_period;

        public sched_attr() {
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("size", "sched_policy", "sched_flags", "sched_nice", "sched_priority",
                    "sched_runtime", "sched_deadline", "sched_period");
        }

        public static class ByReference extends sched_attr implements Structure.ByReference {
        }
    }

    public static class pthread_t extends com.sun.jna.PointerType {
        public pthread_t() {
        }

        public pthread_t(com.sun.jna.Pointer p) {
            super(p);
        }
    }
}

public class CustomThread extends Thread {
    public static final int SCHED_OTHER = 0;
    public static final int SCHED_NORMAL = SCHED_OTHER;
    public static final int SCHED_FIFO = 1;
    public static final int SCHED_RR = 2;
    public static final int SCHED_BATCH = 3;
    public static final int SCHED_IDLE = 5;
    public static final int SCHED_DEADLINE = 6;

    // Syscall number for sched_setattr.
    // This is for x86-64. You may need to adjust for other architectures.
    // __NR_sched_setattr is 314 on x86-64.
    private static final NativeLong __NR_sched_setattr = new NativeLong(314);

    private int policy;
    private int priority; // Used for SCHED_FIFO, SCHED_RR, etc.
    private Runnable target;

    // SCHED_DEADLINE specific parameters
    private long schedRuntime;
    private long schedDeadline;
    private long schedPeriod;
    private boolean isDeadlinePolicy = false;

    static public void yield() {
        LinuxLibC.INSTANCE.sched_yield();
    }

    public CustomThread(Runnable target) {
        super(target);
        this.target = target;
        this.policy = -1; // Indicates not set
        this.priority = -1; // Indicates not set
    }

    public void setLinuxSchedParams(int policy, int priority) {
        if (policy == SCHED_DEADLINE) {
            throw new IllegalArgumentException(
                    "For SCHED_DEADLINE, use setLinuxSchedDeadlineParams method and provide runtime, deadline, and period.");
        }
        this.policy = policy;
        this.priority = priority;
        this.isDeadlinePolicy = false;
    }

    /**
     * Configures the thread to use the SCHED_DEADLINE scheduling policy with the
     * specified runtime, deadline, and period parameters.
     *
     * <p>
     * The SCHED_DEADLINE policy is a real-time scheduling policy available on
     * Linux systems that allows threads to specify timing constraints for
     * execution.
     *
     * @param runtime  The maximum CPU time (in nanoseconds) that the thread is
     *                 allowed to consume within a single period.
     * @param deadline The absolute deadline (in nanoseconds) by which the thread
     *                 must complete its execution within each period.
     * @param period   The period (in nanoseconds) that defines the recurring
     *                 interval for the thread's execution.
     */
    public void setLinuxSchedDeadlineParams(long runtime, long deadline, long period) {
        this.policy = SCHED_DEADLINE;
        this.schedRuntime = runtime;
        this.schedDeadline = deadline;
        this.schedPeriod = period;
        this.isDeadlinePolicy = true;
        this.priority = 0; // sched_priority in sched_attr must be 0 for SCHED_DEADLINE
    }

    @Override
    public void run() {
        if (this.policy != -1) {
            LinuxLibC libc = LinuxLibC.INSTANCE;

            if (this.isDeadlinePolicy) {
                LinuxLibC.sched_attr attr = new LinuxLibC.sched_attr();
                attr.size = attr.size(); // Set the size of the structure
                attr.sched_policy = SCHED_DEADLINE;
                attr.sched_flags = 0;
                attr.sched_nice = 0;
                attr.sched_priority = 0; // Must be 0 for SCHED_DEADLINE
                attr.sched_runtime = this.schedRuntime;
                attr.sched_deadline = this.schedDeadline;
                attr.sched_period = this.schedPeriod;

                // Call sched_setattr using syscall
                // syscall(__NR_sched_setattr, pid, attr_ptr, flags);
                // pid = 0 for current thread, flags = 0
                // The 'attr' Structure instance is passed by JNA as a pointer.
                // Arguments to syscall must match native types: pid (int), attr (pointer),
                // flags (int)
                NativeLong result = libc.syscall(__NR_sched_setattr, 0, attr, 0);

                // sched_setattr syscall returns 0 on success, -1 on error and sets errno.
                if (result.longValue() != 0) {
                    throw new RuntimeException(
                            "Failed to set SCHED_DEADLINE parameters via syscall. Errno: " + Native.getLastError()
                                    + ", syscall result: " + result.longValue());
                }
            } else {
                LinuxLibC.pthread_t threadId = libc.pthread_self();
                LinuxLibC.sched_param param = new LinuxLibC.sched_param(this.priority);
                int result = libc.pthread_setschedparam(threadId, this.policy, param);

                if (result != 0) {
                    throw new RuntimeException("Failed to set thread scheduling parameters (" + this.policy + "): "
                            + Native.getLastError());
                }
            }
        }
        if (target != null) {
            target.run();
        }
    }

    @Override
    public void start() {
        super.start();
    }
}
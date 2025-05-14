import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

interface LinuxLibC extends Library {
    LinuxLibC INSTANCE = Native.load("c", LinuxLibC.class);

    LinuxLibC.pthread_t pthread_self();

    int pthread_setschedparam(LinuxLibC.pthread_t th, int policy, LinuxLibC.sched_param param);

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

    public static class pthread_t extends com.sun.jna.PointerType {
        public pthread_t() {
        }

        public pthread_t(com.sun.jna.Pointer p) {
            super(p);
        }
    }
}

public class CustomThread extends Thread {
    public static final int SCHED_FIFO = 1;
    public static final int SCHED_RR = 2;
    public static final int SCHED_OTHER = 0;

    private int policy;
    private int priority;
    private Runnable target;

    public CustomThread(Runnable target) {
        super(target);
        this.target = target;
        this.policy = -1;
        this.priority = -1;
    }

    public void setLinuxSchedParams(int policy, int priority) {
        this.policy = policy;
        this.priority = priority;
    }

    @Override
    public void run() {
        if (this.policy != -1 && this.priority != -1) {
            LinuxLibC libc = LinuxLibC.INSTANCE;
            LinuxLibC.pthread_t thread = libc.pthread_self();
            LinuxLibC.sched_param param = new LinuxLibC.sched_param(this.priority);

            int result = libc.pthread_setschedparam(thread, this.policy, param);

            if (result != 0) {
                throw new RuntimeException("Failed to set thread scheduling parameters: " + Native.getLastError());
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
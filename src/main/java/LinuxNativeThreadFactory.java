import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class LinuxNativeThreadFactory implements ThreadFactory {
    public enum SchedulingPolicy {
        ROUND_ROBIN(CustomThread.SCHED_RR),
        FIFO(CustomThread.SCHED_FIFO);

        private final int value;

        SchedulingPolicy(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private LinuxNativeThreadFactory.SchedulingPolicy schedulingPolicy;
    private int priority;

    public LinuxNativeThreadFactory(SchedulingPolicy schedulingPolicy, int priority) {
        this.schedulingPolicy = schedulingPolicy;
        this.priority = priority;
    }

    private String generateThreadName() {
        String namePrefix;
        if (schedulingPolicy == SchedulingPolicy.ROUND_ROBIN) {
            namePrefix = "rr-thread-";
        } else if (schedulingPolicy == SchedulingPolicy.FIFO) {
            namePrefix = "fifo-thread-";
        } else {
            throw new IllegalArgumentException("Unsupported scheduling policy");
        }
        return namePrefix + threadNumber.getAndIncrement();
    }

    @Override
    public Thread newThread(Runnable r) {
        // Create a CustomThread with the runnable
        CustomThread thread = new CustomThread(r);

        // Configure thread with name and daemon status
        thread.setName(generateThreadName());
        thread.setDaemon(false);

        thread.setLinuxSchedParams(schedulingPolicy.getValue(), priority);
        return thread;
    }
}

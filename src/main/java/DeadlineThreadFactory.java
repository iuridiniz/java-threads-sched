import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ThreadFactory implementation that creates CustomThreads with SCHED_DEADLINE
 * parameters.
 */
public class DeadlineThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix = "deadline-thread-";
    private final long runtime;
    private final long deadline;
    private final long period;

    /**
     * Creates a new DeadlineThreadFactory with the specified scheduling parameters.
     *
     * @param runtime  the CPU time budget in nanoseconds
     * @param deadline the deadline in nanoseconds
     * @param period   the period in nanoseconds
     */
    public DeadlineThreadFactory(long runtime, long deadline, long period) {
        this.runtime = runtime;
        this.deadline = deadline;
        this.period = period;
    }

    private String generateThreadName() {
        return namePrefix + threadNumber.getAndIncrement();
    }

    @Override
    public Thread newThread(Runnable r) {
        // Create a CustomThread with the runnable
        CustomThread thread = new CustomThread(r);

        // Configure thread with name and daemon status
        thread.setName(generateThreadName());
        thread.setDaemon(false);

        // Set SCHED_DEADLINE parameters
        thread.setLinuxSchedDeadlineParams(runtime, deadline, period);

        return thread;
    }
}
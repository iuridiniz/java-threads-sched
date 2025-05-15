import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A ThreadPoolExecutor implementation that uses SCHED_DEADLINE scheduling
 * policy for its threads.
 * This executor creates threads with real-time scheduling characteristics
 * suitable for
 * deadline-constrained tasks in Linux environments.
 */
public class DeadlineThreadPoolExecutor extends ThreadPoolExecutor {

    // Deadline parameters in nanoseconds
    private final long runtime;
    private final long deadline;
    private final long period;

    // Number of physical CPUs in the system
    private static final int PHYSICAL_CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Creates a DeadlineThreadPoolExecutor with default parameters.
     * 
     * @param runtime  the CPU time budget in nanoseconds
     * @param deadline the deadline in nanoseconds
     * @param period   the period in nanoseconds
     */
    public DeadlineThreadPoolExecutor(long runtime, long deadline, long period) {
        this(PHYSICAL_CPU_COUNT, runtime, deadline, period, new LinkedBlockingQueue<>());
    }

    /**
     * Creates a DeadlineThreadPoolExecutor with a custom work queue.
     * 
     * @param runtime   the CPU time budget in nanoseconds
     * @param deadline  the deadline in nanoseconds
     * @param period    the period in nanoseconds
     * @param workQueue the blocking queue to use for holding tasks
     */
    public DeadlineThreadPoolExecutor(long runtime, long deadline, long period, BlockingQueue<Runnable> workQueue) {
        this(PHYSICAL_CPU_COUNT, runtime, deadline, period, workQueue);
    }

    /**
     * Creates a DeadlineThreadPoolExecutor with a custom pool size.
     * 
     * @param poolSize the maximum number of threads in the pool
     * @param runtime  the CPU time budget in nanoseconds
     * @param deadline the deadline in nanoseconds
     * @param period   the period in nanoseconds
     */
    public DeadlineThreadPoolExecutor(int poolSize, long runtime, long deadline, long period) {
        this(poolSize, runtime, deadline, period, new LinkedBlockingQueue<>());
    }

    /**
     * Creates a DeadlineThreadPoolExecutor with a custom pool size and work queue.
     * 
     * @param poolSize  the maximum number of threads in the pool
     * @param runtime   the CPU time budget in nanoseconds
     * @param deadline  the deadline in nanoseconds
     * @param period    the period in nanoseconds
     * @param workQueue the blocking queue to use for holding tasks
     */
    public DeadlineThreadPoolExecutor(int poolSize, long runtime, long deadline, long period,
            BlockingQueue<Runnable> workQueue) {
        super(
                poolSize, // corePoolSize = poolSize
                poolSize, // maximumPoolSize = poolSize
                60L, TimeUnit.SECONDS, // keepAliveTime and unit (unused since core threads = max threads)
                workQueue, // workQueue for tasks
                new DeadlineThreadFactory(runtime, deadline, period) // custom thread factory
        );

        this.runtime = runtime;
        this.deadline = deadline;
        this.period = period;

        prestartAllCoreThreads(); // Prestart all core threads

    }

    /**
     * Gets the CPU time budget in nanoseconds for the threads created by this
     * executor.
     * 
     * @return the runtime in nanoseconds
     */
    public long getRuntime() {
        return runtime;
    }

    /**
     * Gets the deadline in nanoseconds for the threads created by this executor.
     * 
     * @return the deadline in nanoseconds
     */
    public long getDeadline() {
        return deadline;
    }

    /**
     * Gets the period in nanoseconds for the threads created by this executor.
     * 
     * @return the period in nanoseconds
     */
    public long getPeriod() {
        return period;
    }

    /**
     * Method called after a task completes execution.
     * This implementation calls Thread.yield() to voluntarily give up the CPU
     * after each task finishes, which can help with scheduling efficiency for
     * deadline tasks.
     * 
     * @param r the runnable that has completed execution
     * @param t the exception that caused termination, or null if execution
     *          completed normally
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // Yield the CPU after task completion
        Thread.yield();
        // CustomThread.yield();
    }
}

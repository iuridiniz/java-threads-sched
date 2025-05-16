// Example usage class

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

class MyRunnable implements Runnable {
    private final String name;
    private final int sleepTime;

    public MyRunnable(String name, int sleepTime) {
        this.name = name;
        this.sleepTime = sleepTime;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(this.sleepTime); // Simulate work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

public class LinuxScheduler {

    private static final int PERIODIC_TASK_WORK_TIME = Integer
            .parseInt(System.getenv().getOrDefault("PERIODIC_TASK_WORK_TIME", "2"));
    private static final int PERIODIC_TASK_PERIOD = Integer
            .parseInt(System.getenv().getOrDefault("PERIODIC_TASK_PERIOD", "40"));
    private static final int PROGRAM_RUNTIME = Integer
            .parseInt(System.getenv().getOrDefault("PROGRAM_RUNTIME", "2000"));
    private static final int NUMBER_OF_PERIODIC_TASKS = Integer
            .parseInt(System.getenv().getOrDefault("NUMBER_OF_PERIODIC_TASKS", "50"));
    static final long DEADLINE_RUNTIME = Long
            .parseLong(System.getenv().getOrDefault("DEADLINE_RUNTIME", String.valueOf(1_000_000L)));
    static final long DEADLINE_DEADLINE = Long
            .parseLong(System.getenv().getOrDefault("DEADLINE_DEADLINE", String.valueOf(1_000_000L)));
    static final long DEADLINE_PERIOD = Long
            .parseLong(System.getenv().getOrDefault("DEADLINE_PERIOD", String.valueOf(1_000_000L)));
    static final int NUMBER_OF_THREADS = Integer.parseInt(System.getenv().getOrDefault("NUMBER_OF_THREADS",
            String.valueOf(Runtime.getRuntime().availableProcessors() - 1)));

    static final String THREAD_FACTORY = System.getenv().getOrDefault("THREAD_FACTORY", "deadline");

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Main thread PID: " + ProcessHandle.current().pid());
        System.out.println("Use Ctrl+C to stop the threads.");
        System.out.println("Use 'ps -elcLf' to check the scheduling policy and priority of the threads.");

        ThreadFactory threadFactory;

        if (THREAD_FACTORY.equals("deadline")) {
            System.out.println("Using DeadlineThreadFactory (runtime=" + DEADLINE_RUNTIME +
                    ", deadline=" + DEADLINE_DEADLINE + ", period=" + DEADLINE_PERIOD + ")");
            threadFactory = new DeadlineThreadFactory(DEADLINE_RUNTIME,
                    DEADLINE_DEADLINE, DEADLINE_PERIOD);
        } else {
            System.out.println("Using default ThreadFactory");
            threadFactory = Executors.defaultThreadFactory();
        }

        int numberOfThreads = NUMBER_OF_THREADS;
        if (numberOfThreads <= 0) {
            numberOfThreads = 1;
        }

        System.out.println("Creating ScheduledThreadPoolExecutor with " + numberOfThreads + " threads");

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(numberOfThreads,
                threadFactory) {
            ConcurrentHashMap<Runnable, Long> startTimes = new ConcurrentHashMap<>();
            ConcurrentHashMap<Runnable, Long> endTimes = new ConcurrentHashMap<>();

            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                super.beforeExecute(t, r);
                long currentTime = System.currentTimeMillis();

                startTimes.put(r, currentTime);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                long currentTime = System.currentTimeMillis();

                if (t != null) {
                    System.out.println(
                            String.format("Thread %s encountered an error while executing task `%s`: %s",
                                    Thread.currentThread().getName(), r.toString(), t.getMessage()));
                }

                Long startTime = startTimes.remove(r);
                long lastEndTime = endTimes.getOrDefault(r, currentTime);
                if (startTime != null) {
                    long duration = currentTime - startTime;
                    long period = currentTime - lastEndTime;
                    System.out.println(
                            String.format("[%d] Thread %s executed task `%s` in %d ms [p=%d]", currentTime,
                                    Thread.currentThread().getName(),
                                            r.toString(), duration, period));
                }
                endTimes.put(r, currentTime);
                super.afterExecute(r, t);
            }
        };
        // warm up the executor
        System.out.println("Warming up the executor...");
        int numberOfSimpleTasks = numberOfThreads;
        for (int i = 0; i < numberOfSimpleTasks; i++) {
            Thread.sleep(1000 / numberOfSimpleTasks);
            executor.execute(new MyRunnable("[Simple Task] " + (i + 1), 10));
        }
        System.out.println("------------------");
        Thread.sleep(1000);
        for (int i = 0; i < NUMBER_OF_PERIODIC_TASKS; i++) {
            executor.scheduleAtFixedRate(new MyRunnable("Task " + (i + 1), PERIODIC_TASK_WORK_TIME), 0,
                    PERIODIC_TASK_PERIOD,
                    TimeUnit.MILLISECONDS);
        }

        Thread.sleep(PROGRAM_RUNTIME);

        // show stats from executor
        System.out.println("Active count: " + executor.getActiveCount());
        System.out.println("Pool size: " + executor.getPoolSize());
        System.out.println("Task count: " + executor.getTaskCount());
        System.out.println("Completed task count: " +
                executor.getCompletedTaskCount());
        System.out.println("Largest pool size: " + executor.getLargestPoolSize());
        System.out.println("Queue size: " + executor.getQueue().size());
        System.out.println("Core pool size: " + executor.getCorePoolSize());
        System.out.println("Maximum pool size: " + executor.getMaximumPoolSize());
        System.out.println("Keep alive time: " +
                executor.getKeepAliveTime(TimeUnit.SECONDS) + " seconds");

        System.out.println("Stopping all tasks...");
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        executor.close();

        System.out.println("Main thread finished.");
    }
}

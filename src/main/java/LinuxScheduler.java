// Example usage class

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Main thread PID: " + ProcessHandle.current().pid());
        System.out.println("Use Ctrl+C to stop the threads.");
        System.out.println("Use 'ps -elcLf' to check the scheduling policy and priority of the threads.");

        long runtime = 1_000_000L;
        long deadline = 1_000_000L;
        long period = 1_000_000L;

        // int numOfThreads = Runtime.getRuntime().availableProcessors();
        int numOfThreads = 4;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(numOfThreads,
                new DeadlineThreadFactory(runtime, deadline, period)) {
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
        for (int i = 0; i < numOfThreads; i++) {
            executor.execute(new MyRunnable("[Task] " + (i + 1), 10));
        }
        Thread.sleep(1000);
        System.out.println("------------------");
        for (int i = 0; i < 40; i++) {
            executor.scheduleAtFixedRate(new MyRunnable("Task " + (i + 1), 2), 0, 40, TimeUnit.MILLISECONDS);
        }

        // Thread.sleep(3_600_000);
        Thread.sleep(1_000);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // show stats from executor
        // System.out.println("Active count: " + executor.getActiveCount());
        // System.out.println("Pool size: " + executor.getPoolSize());
        // System.out.println("Task count: " + executor.getTaskCount());
        // System.out.println("Completed task count: " +
        // executor.getCompletedTaskCount());
        // System.out.println("Largest pool size: " + executor.getLargestPoolSize());
        // System.out.println("Queue size: " + executor.getQueue().size());
        // System.out.println("Core pool size: " + executor.getCorePoolSize());
        // System.out.println("Maximum pool size: " + executor.getMaximumPoolSize());
        // System.out.println("Keep alive time: " +
        // executor.getKeepAliveTime(TimeUnit.SECONDS) + " seconds");

        executor.close();
        System.out.println("Main thread finished.");
    }
}

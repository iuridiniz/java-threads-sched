// Example usage class

import java.util.HashMap;
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
        DeadlineThreadPoolExecutor executor = new DeadlineThreadPoolExecutor(
                8,
                5_000_000L,
                20_000_000L,
                20_000_000L) {

            HashMap<String, Long> startTimes = new HashMap<>();

            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                super.beforeExecute(t, r);
                long currentTime = System.currentTimeMillis();
                System.out.println(
                        String.format("Thread %s is about to execute task `%s` at %d", t.getName(), r.toString(),
                                currentTime));
                startTimes.put(r.toString(), currentTime);
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                long currentTime = System.currentTimeMillis();

                if (t != null) {
                    System.out.println(
                            String.format("Thread %s encountered an error while executing task `%s`: %s",
                                    Thread.currentThread().getName(), r.toString(), t.getMessage()));
                }
                System.out.println(
                        String.format("Thread %s has finished executing task `%s` at %d",
                                Thread.currentThread().getName(),
                                r.toString(), currentTime));
                Long startTime = startTimes.remove(r.toString());
                if (startTime != null) {
                    long duration = currentTime - startTime;
                    System.out.println(
                            String.format("Thread %s executed task `%s` in %d ms", Thread.currentThread().getName(),
                                    r.toString(), duration));
                }
                super.afterExecute(r, t);
            }
        };

        for (int i = 0; i < 20; i++) {
            executor.execute(new MyRunnable(String.format("task-%s", i), 5));
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // show stats from executor
        System.out.println("Active count: " + executor.getActiveCount());
        System.out.println("Pool size: " + executor.getPoolSize());
        System.out.println("Task count: " + executor.getTaskCount());
        System.out.println("Completed task count: " + executor.getCompletedTaskCount());
        System.out.println("Largest pool size: " + executor.getLargestPoolSize());
        System.out.println("Queue size: " + executor.getQueue().size());
        System.out.println("Core pool size: " + executor.getCorePoolSize());
        System.out.println("Maximum pool size: " + executor.getMaximumPoolSize());
        System.out.println("Keep alive time: " + executor.getKeepAliveTime(TimeUnit.SECONDS) + " seconds");

        executor.close();
        System.out.println("Main thread finished.");
    }
}

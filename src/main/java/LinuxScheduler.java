// Example usage class
public class LinuxScheduler {
    public static void main(String[] args) throws InterruptedException {
        // Create a CustomThread
        CustomThread thread1 = new CustomThread(() -> {
            while (true) {
                System.out
                        .println("[FIFO] Thread 1 is running (every 2s)... the time is " + System.currentTimeMillis());
                try {
                    Thread.sleep(2000); // Simulate work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread 1 interrupted");
                }
            }
        });

        // Set the Linux scheduling parameters (SCHED_FIFO and priority 99)
        thread1.setLinuxSchedParams(CustomThread.SCHED_FIFO, 99); // Use CustomThread.SCHED_FIFO
        System.out.println("Setting Thread-1 to SCHED_FIFO, priority = " + 99);

        // Start the thread
        thread1.start();

        // Create another CustomThread
        CustomThread thread2 = new CustomThread(() -> {
            while (true) {
                System.out.println("[RR] Thread 2 is running (every 10s)... the time is " + System.currentTimeMillis());
                try {
                    Thread.sleep(10000); // Simulate work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread 2 interrupted");
                }
            }
        });
        // Set the scheduling of Thread2
        thread2.setLinuxSchedParams(CustomThread.SCHED_RR, 50); // Use CustomThread.SCHED_RR
        System.out.println("Setting Thread-2 to SCHED_RR, priority = " + 50);
        thread2.start();

        // Create a third CustomThread
        CustomThread thread3 = new CustomThread(() -> {
            while (true) {
                System.out.println(
                        "[DEADLINE] Thread 3 is running (every 500ms)... the time is " + System.currentTimeMillis());
                try {
                    Thread.sleep(2); // Simulate work
                    Thread.yield();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread 3 interrupted");
                }
            }
        });
        // Set the scheduling of Thread3
        // 2.5ms runtime, 500ms deadline, 500ms period
        thread3.setLinuxSchedDeadlineParams(2_500_000L, 500_000_000L, 500_000_000L);
        System.out.println("Setting Thread-3 to SCHED_DEADLINE, runtime = 2.5ms, deadline = 500ms, period = 500ms");
        thread3.start();

        System.out.println("Main thread PID: " + ProcessHandle.current().pid());
        System.out.println("Use Ctrl+C to stop the threads.");
        System.out.println("Use 'ps -elcLf' to check the scheduling policy and priority of the threads.");

        // Wait for the threads to finish
        thread1.join();
        thread2.join();
        thread3.join();

        System.out.println("Main thread finished.");
    }
}

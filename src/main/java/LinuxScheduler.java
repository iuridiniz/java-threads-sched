// Example usage class
public class LinuxScheduler {
    public static void main(String[] args) throws InterruptedException {
        // Create a CustomThread
        CustomThread thread1 = new CustomThread(() -> {
            System.out.println("Thread 1 running in " + Thread.currentThread().getName());
            try {
                Thread.sleep(1000); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread 1 interrupted");
            }
        });

        // Set the Linux scheduling parameters (SCHED_FIFO and priority 99)
        thread1.setLinuxSchedParams(CustomThread.SCHED_FIFO, 99); // Use CustomThread.SCHED_FIFO
        System.out.println("Setting Thread-1 to SCHED_FIFO, priority = " + 99);

        // Start the thread
        thread1.start();

        // Create another CustomThread
        CustomThread thread2 = new CustomThread(() -> {
            System.out.println("Thread 2 running in " + Thread.currentThread().getName());
            try {
                Thread.sleep(2000); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread 2 interrupted");
            }
        });
        // Set the scheduling of Thread2
        thread2.setLinuxSchedParams(CustomThread.SCHED_RR, 50); // Use CustomThread.SCHED_RR
        System.out.println("Setting Thread-2 to SCHED_RR, priority = " + 50);
        thread2.start();

        System.out.println("Main thread PID: " + ProcessHandle.current().pid());

        // Wait for the threads to finish
        thread1.join();
        thread2.join();

        System.out.println("Main thread finished.");
    }
}

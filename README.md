# Linux Scheduler

## Description

**This project serves as an example demonstration** of how a Java application can interact with Linux process scheduling capabilities. It utilizes JNA (Java Native Access) to call native Linux library functions related to thread and process management. **It is not intended to be a general-purpose library.**

## Installation Instructions

### Prerequisites

*   Java Development Kit (JDK) version 21 or higher.
*   Apache Maven

### Building the Project

1.  Clone the repository:

    ```bash
    git clone https://github.com/iuridiniz/java-threads-sched.git
    cd threads-sched
    ```

2.  Build the project using Maven:

    ```bash
    mvn package
    ```

    This will generate a JAR file with dependencies in the `target/` directory (e.g., `linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar`).

### Dependencies

The project uses the following main dependencies (managed by Maven):

*   JNA (Java Native Access): For calling native library functions.
    *   `net.java.dev.jna:jna:5.13.0`
    *   `net.java.dev.jna:jna-platform:5.13.0`

## Usage Instructions

This section outlines how to run the Java application.

### 1. Running with Standard Scheduling

You can run the Java application directly using standard Linux scheduling policies (e.g., `SCHED_OTHER`). This does not require any special privileges or the `sched_helper` utility.

After building the main Java project (using `mvn package` as described in "Installation Instructions"), run:

```bash
java -jar target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 2. Running with Real-Time Scheduling (using `sched_helper`)

To use real-time scheduling policies like `SCHED_FIFO` or `SCHED_RR`, the Java process needs `CAP_SYS_NICE` capabilities (see `sched_setscheduler(2)` man page for details). Running a Java JVM directly with such capabilities (e.g., as root) is generally discouraged for security reasons.

This project provides a C helper program (`sched_helper`) to launch the Java application with the necessary capabilities without granting excessive permissions to the entire JVM.

Follow these steps to build, configure, and use `sched_helper`:

#### a. Building `sched_helper`

The `sched_helper.c` program needs to be compiled first. The project includes a `Makefile` for this purpose. In the project's root directory, run:

```bash
make sched_helper
```

This will produce an executable named `sched_helper` in the same directory.

#### b. Setting Privileges for `sched_helper`

For `sched_helper` to be able to grant `CAP_SYS_NICE` to the Java process it starts, `sched_helper` itself must possess this capability.

The recommended way to achieve this is to set the capability directly on the `sched_helper` executable:

```bash
sudo setcap cap_sys_nice+ep ./sched_helper
```

This command should be run in the directory where `sched_helper` was built. You might need to install a package that provides the `setcap` utility (e.g., `libcap2-bin` on Debian/Ubuntu based systems). This method is more secure than alternatives like `setuid` root.

#### c. Using `sched_helper` to Run the Java Application

Once `sched_helper` is built and has the `cap_sys_nice+ep` capability set, you can use it to run your Java application with elevated scheduling privileges:

```bash
./sched_helper /usr/bin/java -jar target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar [any-other-java-args]
```

*(Important: Replace `/usr/bin/java` with the actual absolute path to your Java executable if it's different on your system. The `sched_helper` program expects the full path to the command it needs to execute, followed by its arguments.)*

This command will execute the Java application, and `CustomThread` instances within it should now be able to successfully apply `SCHED_FIFO` or `SCHED_RR` policies if `setLinuxSchedParams()` is called.

## Key Components

### `CustomThread.java`

`CustomThread.java` provides a custom Java thread implementation that allows setting Linux-specific scheduling policies (`SCHED_FIFO`, `SCHED_RR`, `SCHED_OTHER`, `SCHED_DEADLINE` - see `sched_setscheduler(2)` and `sched(7)`) and their respective parameters for the thread itself before it starts.

**How it works:**

1.  **Inheritance and Initialization**:
    *   `CustomThread` extends `java.lang.Thread`.
    *   It's initialized with a `Runnable` target, similar to a standard `Thread`.
    *   The method `setLinuxSchedParams(int policy, int priority)` can be called *before* `start()` to specify traditional Linux scheduling policies (e.g., `CustomThread.SCHED_FIFO`, `CustomThread.SCHED_RR`) and a priority for that policy.
    *   The method `setLinuxSchedDeadlineParams(long runtime, long deadline, long period)` can be called *before* `start()` to specify the `SCHED_DEADLINE` policy and its associated nanosecond-precision timing parameters (runtime, deadline, period).

2.  **Setting Scheduling Parameters (in `run()` method)**:
    *   When the `start()` method is called (which subsequently calls `run()`), `CustomThread` first checks which scheduling parameters were set.
    *   **For `SCHED_DEADLINE`**:
        *   If `setLinuxSchedDeadlineParams()` was called, it uses JNA to make a direct system call to `sched_setattr`. This is because `pthread_setschedparam` does not support `SCHED_DEADLINE`.
        *   It prepares a `sched_attr` structure (defined via JNA) containing the desired `runtime`, `deadline`, and `period` (along with other necessary fields like `size`, `sched_policy`, `sched_flags`, and `sched_priority` which is 0 for `SCHED_DEADLINE`).
        *   It then calls the native `syscall(__NR_sched_setattr, pid, attr_ptr, flags)` function. `pid = 0` targets the current thread. `__NR_sched_setattr` is the system call number (e.g., 314 for x86-64).
        *   If the `syscall` returns an error (a non-zero value, typically -1 for syscalls), a `RuntimeException` is thrown, including the native error code obtained via `Native.getLastError()`.
    *   **For other policies (e.g., `SCHED_FIFO`, `SCHED_RR`)**:
        *   If `setLinuxSchedParams()` was called for a non-deadline policy, it uses JNA to interact with the native Linux C library (`libc`):
            *   It calls the native `pthread_self()` function to get the ID of the current native thread.
            *   It prepares a `sched_param` structure (also defined via JNA) containing the desired `priority`.
            *   It then calls the native `pthread_setschedparam(thread_id, policy, sched_param_struct)` function (see `pthread_setschedparam(3)` man page). This system call attempts to change the scheduling policy and priority of the calling thread.
            *   If `pthread_setschedparam` returns an error (a non-zero value), a `RuntimeException` is thrown. This indicates that the scheduling parameters could not be set (e.g., due to insufficient permissions if not using `sched_helper` correctly, or invalid parameters).
    *   After attempting to set the scheduling parameters (if they were specified), the `run()` method proceeds to execute the `run()` method of the `Runnable` target that was passed during the `CustomThread`'s construction.

3.  **JNA Interface (`LinuxLibC`)**:
    *   An inner interface `LinuxLibC` (extending `com.sun.jna.Library`) is defined within `CustomThread.java`.
    *   This interface declares the JNA mappings for the native C functions (`pthread_self`, `pthread_setschedparam`), the `syscall` function, and the necessary C structures like `sched_param`, `sched_attr`, and `pthread_t`. This is what enables the Java code to call these C library functions and system calls.

**Conceptual Usage:**

```java
Runnable myTask = () -> {
    System.out.println("Thread " + Thread.currentThread().getName() + " with custom scheduling executing.");
    // ... some work ...
};

CustomThread customThread = new CustomThread(myTask);
customThread.setName("MyFIFOTask");

// Set SCHED_FIFO policy with priority 10
// This requires CAP_SYS_NICE, typically via sched_helper
customThread.setLinuxSchedParams(CustomThread.SCHED_FIFO, 10);

customThread.start();

// Example for SCHED_DEADLINE
Runnable deadlineTask = () -> {
    System.out.println("Thread " + Thread.currentThread().getName() + " with SCHED_DEADLINE executing.");
    // ... time-critical work ...
};

CustomThread deadlineThread = new CustomThread(deadlineTask);
deadlineThread.setName("MyDeadlineTask");

// SCHED_DEADLINE: runtime 2ms, deadline 500ms, period 500ms
// This requires CAP_SYS_NICE or running as root.
// Note: sched_helper as provided might not be sufficient for SCHED_DEADLINE
// as it typically requires more privileges (e.g. CAP_SYS_ADMIN or running as root)
// than just CAP_SYS_NICE for sched_setattr.
long runtimeNs = 2_000_000L;   // 2 ms
long deadlineNs = 500_000_000L; // 500 ms
long periodNs = 500_000_000L;   // 500 ms
deadlineThread.setLinuxSchedDeadlineParams(runtimeNs, deadlineNs, periodNs);

deadlineThread.start();
```

### Understanding `SCHED_DEADLINE` Parameters: `runtime`, `deadline`, and `period`

The `SCHED_DEADLINE` policy is designed for real-time tasks where timing is critical. It uses three key parameters, all specified in nanoseconds:

1.  **`period` (Periodicity)**:
    *   **What it is**: Defines how frequently a new "instance" or "job" of your task is expected to arrive or needs to be initiated. It's the regular interval at which your task needs to perform its work.
    *   **Analogy**: Imagine a factory conveyor belt. A new item (a task instance) arrives on the belt every `period` amount of time, and it needs to be processed.
    *   **Example**: If a task needs to execute every 20 milliseconds, its `period` is `20,000,000` ns.

2.  **`runtime` (Worst-Case Execution Time - WCET)**:
    *   **What it is**: The maximum amount of CPU time your task is *allowed* to consume within a single `period`. This is your CPU time budget for each instance of the task.
    *   **Analogy**: For each item arriving on the conveyor belt (every `period`), you are given a maximum of `runtime` seconds on the processing machine to complete your work on that item.
    *   **Important**: You must estimate the worst-case execution time (WCET) of your task's computation and set `runtime` to be at least this value (often slightly more, to provide a safety margin). If your task attempts to run longer than its `runtime` within a period, it will be throttled (stopped from running) until its next period begins.
    *   **Example**: If a task's computation takes at most 2 milliseconds of CPU time per instance, its `runtime` could be `2,000,000` ns.

3.  **`deadline` (Relative Deadline)**:
    *   **What it is**: The time by which each instance of your task *must* complete its `runtime` amount of work, relative to the start of its `period`.
    *   **Analogy**: For each item arriving on the conveyor belt, it must be fully processed (using up to `runtime` on the machine) before `deadline` seconds have passed since it arrived on the belt.
    *   **Constraint**: The kernel enforces `runtime <= deadline <= period`.
    *   **Common Practice**: Often, `deadline` is set equal to `period`. This means the task must complete its work for the current interval before the next interval begins.
    *   **Example**: If a task has a `period` of 20ms and its work must be finished before the next period starts, its `deadline` would also be `20,000,000` ns.

**Practical Scenarios:**

*   **Scenario 1: Video Frame Processing**
    *   **Task**: Process an incoming video frame. A new frame arrives every 33ms (approx 30 FPS).
    *   **Processing Time**: Worst-case CPU time per frame is 5ms.
    *   **Requirement**: Each frame must be processed before the next one arrives.
    *   **Parameters**:
        *   `period = 33_000_000` (33ms)
        *   `runtime = 5_000_000` (5ms, or slightly more, e.g., 6ms, for safety)
        *   `deadline = 33_000_000` (33ms)

*   **Scenario 2: Critical Control Loop (e.g., Robotics)**
    *   **Task**: Read sensor data, compute, send actuator commands. Loop must run every 10ms.
    *   **Computation Time**: Max 1ms.
    *   **Requirement**: Command must be sent within 8ms of the cycle start for stability.
    *   **Parameters**:
        *   `period = 10_000_000` (10ms)
        *   `runtime = 1_000_000` (1ms, or e.g., 1.2ms for buffer)
        *   `deadline = 8_000_000` (8ms - stricter than the period)

*   **Scenario 3: High-Frequency Data Sampling**
    *   **Task**: Sample a sensor every 1ms.
    *   **Work per Sample**: Very short, e.g., 50 microseconds (0.05ms).
    *   **Requirement**: Each sample captured within its 1ms window.
    *   **Parameters**:
        *   `period = 1_000_000` (1ms)
        *   `runtime = 50_000` (50µs, or e.g., 60,000ns for buffer)
        *   `deadline = 1_000_000` (1ms)

**How the Scheduler Uses These Parameters:**

The Linux kernel's scheduler uses these parameters to:

*   **Perform an Admissibility Test**: When a task requests `SCHED_DEADLINE`, the scheduler checks if it can guarantee that this new task and all existing `SCHED_DEADLINE` tasks will meet their deadlines. If not, the request to set the policy will fail.
*   **Prioritize Tasks**: Among `SCHED_DEADLINE` tasks, the one with the earliest absolute deadline (calculated as arrival_time + relative_deadline) is given priority.
*   **Enforce `runtime`**: If a task exceeds its allocated `runtime` within a `period`, it is throttled. This prevents a single misbehaving or unexpectedly long task from monopolizing the CPU and causing other critical tasks to miss their deadlines.

In the `LinuxScheduler.java` example, `thread3` is configured as:
`thread3.setLinuxSchedDeadlineParams(2_000_000L, 500_000_000L, 500_000_000L);`
This tells the system:
*   "My task `thread3` needs to run every 500 milliseconds (`period`)."
*   "In each 500ms window, I need up to 2 milliseconds of CPU time (`runtime`)."
*   "I must complete this 2ms of work within that 500ms window (`deadline`)."

## License

This project is licensed under the MIT License - see the LICENSE.md file for details.

## References

*   Links to relevant documentation, articles, or related projects.
*   JNA Documentation: [https://github.com/java-native-access/jna](https://github.com/java-native-access/jna)
*   Linux Scheduler Man Pages:
    *   `sched_setscheduler(2)`: [https://man7.org/linux/man-pages/man2/sched_setscheduler.2.html](https://man7.org/linux/man-pages/man2/sched_setscheduler.2.html)
    *   `pthread_setschedparam(3)`: [https://man7.org/linux/man-pages/man3/pthread_setschedparam.3.html](https://man7.org/linux/man-pages/man3/pthread_setschedparam.3.html)
    *   `sched(7)`: [https://man7.org/linux/man-pages/man7/sched.7.html](https://man7.org/linux/man-pages/man7/sched.7.html) (Overview of Linux scheduling)


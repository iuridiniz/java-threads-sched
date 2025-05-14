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
make
```
This will produce an executable named `sched_helper` in the same directory.

#### b. Setting Privileges for `sched_helper`

For `sched_helper` to be able to grant `CAP_SYS_NICE` to the Java process it starts, `sched_helper` itself must possess this capability.

The recommended way to achieve this is to set the capability directly on the `sched_helper` executable:

```bash
sudo setcap cap_sys_nice+ep sched_helper
```
This command should be run in the directory where `sched_helper` was built. You might need to install a package that provides the `setcap` utility (e.g., `libcap2-bin` on Debian/Ubuntu based systems). This method is more secure than alternatives like `setuid` root.

#### c. Using `sched_helper` to Run the Java Application

Once `sched_helper` is built and has the `cap_sys_nice+ep` capability set, you can use it to run your Java application with elevated scheduling privileges:

Make sure `sched_helper` is in your current directory (or accessible via your system's PATH).

```bash
./sched_helper /usr/bin/java -jar target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar [any-other-java-args]
```
*(Important: Replace `/usr/bin/java` with the actual absolute path to your Java executable if it's different on your system. The `sched_helper` program expects the full path to the command it needs to execute, followed by its arguments.)*

This command will execute the Java application, and `CustomThread` instances within it should now be able to successfully apply `SCHED_FIFO` or `SCHED_RR` policies if `setLinuxSchedParams()` is called.

## Key Components

### `CustomThread.java`

`CustomThread.java` provides a custom Java thread implementation that allows setting Linux-specific scheduling policies (`SCHED_FIFO`, `SCHED_RR`, `SCHED_OTHER` - see `sched_setscheduler(2)`) and priorities for the thread itself before it starts.

**How it works:**

1.  **Inheritance and Initialization**:
    *   `CustomThread` extends `java.lang.Thread`.
    *   It's initialized with a `Runnable` target, similar to a standard `Thread`.
    *   The method `setLinuxSchedParams(int policy, int priority)` can be called *before* `start()` to specify the desired Linux scheduling policy (e.g., `CustomThread.SCHED_FIFO`, `CustomThread.SCHED_RR`) and a priority for that policy.

2.  **Setting Scheduling Parameters (in `run()` method)**:
    *   When the `start()` method is called (which subsequently calls `run()`), `CustomThread` first checks if `policy` and `priority` were set via `setLinuxSchedParams()`.
    *   If they have been set, it uses JNA to interact with the native Linux C library (`libc`):
        *   It calls the native `pthread_self()` function to get the ID of the current native thread.
        *   It prepares a `sched_param` structure (also defined via JNA) containing the desired `priority`.
        *   It then calls the native `pthread_setschedparam(thread_id, policy, sched_param_struct)` function (see `pthread_setschedparam(3)` man page). This system call attempts to change the scheduling policy and priority of the calling thread.
        *   If `pthread_setschedparam` returns an error (a non-zero value), a `RuntimeException` is thrown, including the native error code obtained via `Native.getLastError()`. This indicates that the scheduling parameters could not be set (e.g., due to insufficient permissions if not using `sched_helper` correctly, or invalid parameters).
    *   After attempting to set the scheduling parameters (if they were specified), the `run()` method proceeds to execute the `run()` method of the `Runnable` target that was passed during the `CustomThread`'s construction.

3.  **JNA Interface (`LinuxLibC`)**:
    *   An inner interface `LinuxLibC` (extending `com.sun.jna.Library`) is defined within `CustomThread.java`.
    *   This interface declares the JNA mappings for the native C functions (`pthread_self`, `pthread_setschedparam`) and the necessary C structures like `sched_param` and `pthread_t`. This is what enables the Java code to call these C library functions.

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
```

## Contributing

Contributions are welcome! Please follow these general guidelines:

1.  Fork the repository.
2.  Create a new branch for your feature or bug fix.
3.  Make your changes.
4.  Ensure your code adheres to the project's coding standards.
5.  Write or update tests for your changes.
6.  Submit a pull request with a clear description of your changes.

## License

This project is licensed under the MIT License - see the LICENSE.md file for details.

## References

*   Links to relevant documentation, articles, or related projects.
*   JNA Documentation: [https://github.com/java-native-access/jna](https://github.com/java-native-access/jna)
*   Linux Scheduler Man Pages:
    *   `sched_setscheduler(2)`: [https://man7.org/linux/man-pages/man2/sched_setscheduler.2.html](https://man7.org/linux/man-pages/man2/sched_setscheduler.2.html)
    *   `pthread_setschedparam(3)`: [https://man7.org/linux/man-pages/man3/pthread_setschedparam.3.html](https://man7.org/linux/man-pages/man3/pthread_setschedparam.3.html)
    *   `sched(7)`: [https://man7.org/linux/man-pages/man7/sched.7.html](https://man7.org/linux/man-pages/man7/sched.7.html) (Overview of Linux scheduling)


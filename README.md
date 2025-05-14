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

After building the project, you can run the application using the generated JAR file:

```bash
java -jar target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar
```

(Note: Specific command-line arguments or usage patterns might be required depending on the application's entry point and functionality in `LinuxScheduler.java`.)

**Important Note on Privileges:** To use `SCHED_FIFO` and `SCHED_RR` scheduling policies (see `sched_setscheduler(2)` man page for details), the program requires `CAP_SYS_NICE` capabilities (or root privileges). Running a Java JVM as root or with `setuid` can be dangerous. To address this, a C helper program (`help_launcher.c`, compiled to `help_launcher`) is provided. This helper program can be used to launch the Java application with the necessary capabilities without granting excessive permissions to the entire JVM.

To enable `help_launcher` to provide these capabilities to the Java process, `help_launcher` itself needs to have elevated privileges. The recommended approach is to grant only the necessary `CAP_SYS_NICE` capability to the `help_launcher` executable:

```bash
sudo setcap cap_sys_nice+ep help_launcher
```

This method limits the potential impact if `help_launcher` is compromised. You might need to install a package that provides the `setcap` utility (e.g., `libcap2-bin` on Debian/Ubuntu).

After setting up `help_launcher` with appropriate privileges, you would typically use it like this (after compiling `help_launcher`):

```bash
./help_launcher /usr/bin/java -jar target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar [any-other-java-args]
```

The project also includes a C helper program `help_launcher.c`. It can be compiled using the provided `Makefile`:

```bash
make
```

This will produce an executable named `help_launcher`.

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
        *   If `pthread_setschedparam` returns an error (a non-zero value), a `RuntimeException` is thrown, including the native error code obtained via `Native.getLastError()`. This indicates that the scheduling parameters could not be set (e.g., due to insufficient permissions if not using `help_launcher` correctly, or invalid parameters).
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
// This requires CAP_SYS_NICE, typically via help_launcher
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


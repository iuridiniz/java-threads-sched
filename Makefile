CC = gcc
CFLAGS = -Wall -Wextra -O2
JAVA_SRC = src/main/java/LinuxScheduler.java src/main/java/CustomThread.java pom.xml
JAR_FILE = target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar
LAUNCHER = my_java_launcher
JAVA_BIN = $(shell which java)

.PHONY: all setcap run clean

all: $(LAUNCHER) $(JAR_FILE)

$(LAUNCHER): my_java_launcher.c
	$(CC) $(CFLAGS) -o $@ $< -lcap

setcap: $(LAUNCHER)
	sudo setcap cap_sys_nice+ep $<

$(JAR_FILE): $(JAVA_SRC)
	mvn package

run: all setcap
	@echo "Running Java application with custom launcher..."
	./$(LAUNCHER) $(JAVA_BIN) -jar $(JAR_FILE)

clean:
	rm -f $(LAUNCHER)
	mvn clean

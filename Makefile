CC = gcc
CFLAGS = -Wall -Wextra -O2
JAVA_SRC = pom.xml $(shell find src/ -name "*.java")
JAR_FILE = target/linux-scheduler-1.0-SNAPSHOT-jar-with-dependencies.jar
LAUNCHER = sched_helper
JAVA_BIN = $(shell which java)

.PHONY: all run clean

all: $(LAUNCHER) $(JAR_FILE)

$(LAUNCHER): sched_helper.c
	$(CC) $(CFLAGS) -o $@ $< -lcap

$(LAUNCHER).setcap: $(LAUNCHER)
	@echo "Setting capabilities for $(LAUNCHER)..."
	cp -f $< $@
	sudo setcap cap_sys_nice+ep $@

$(JAR_FILE): $(JAVA_SRC)
	mvn package

run: all $(LAUNCHER).setcap
	@echo "Running Java application with custom launcher..."
	./$(LAUNCHER).setcap $(JAVA_BIN) -jar $(JAR_FILE)

clean:
	rm -f $(LAUNCHER) $(LAUNCHER).setcap
	mvn clean

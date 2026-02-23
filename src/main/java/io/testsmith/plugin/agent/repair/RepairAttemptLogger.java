package io.testsmith.plugin.agent.repair;

import java.util.Objects;
import java.util.function.Consumer;

@FunctionalInterface
public interface RepairAttemptLogger {
    void log(RepairAttemptLog log);

    static RepairAttemptLogger noop() {
        return log -> {
        };
    }

    static RepairAttemptLogger toConsumer(Consumer<String> sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        return log -> sink.accept(log.format());
    }
}

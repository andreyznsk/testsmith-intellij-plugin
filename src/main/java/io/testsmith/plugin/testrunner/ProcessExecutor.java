package io.testsmith.plugin.testrunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface ProcessExecutor {
    ExecResult exec(List<String> command, Path workingDirectory, Map<String, String> env, Duration timeout);
}

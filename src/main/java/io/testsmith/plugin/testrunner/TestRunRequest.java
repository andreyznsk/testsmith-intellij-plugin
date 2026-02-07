package io.testsmith.plugin.testrunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TestRunRequest {
    private final TestRunMode mode;
    private final TestTarget target;
    private final Path projectRoot;
    private final Duration timeout;
    private final Map<String, String> env;
    private final List<String> mavenArgsExtra;
    private final Path jacocoXmlPath;

    public TestRunRequest(
            TestRunMode mode,
            TestTarget target,
            Path projectRoot,
            Duration timeout,
            Map<String, String> env,
            List<String> mavenArgsExtra,
            Path jacocoXmlPath
    ) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot must not be null");
        this.timeout = requirePositive(timeout, "timeout");
        this.env = env == null ? Map.of() : Map.copyOf(env);
        this.mavenArgsExtra = mavenArgsExtra == null ? List.of() : List.copyOf(mavenArgsExtra);
        this.target = target;
        this.jacocoXmlPath = jacocoXmlPath;
        validate();
    }

    public TestRunMode mode() {
        return mode;
    }

    public TestTarget target() {
        return target;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Duration timeout() {
        return timeout;
    }

    public Map<String, String> env() {
        return env;
    }

    public List<String> mavenArgsExtra() {
        return mavenArgsExtra;
    }

    public Path jacocoXmlPath() {
        return jacocoXmlPath;
    }

    private void validate() {
        if (mode == TestRunMode.VERIFY_TARGET) {
            if (target == null) {
                throw new IllegalArgumentException("target must be provided for VERIFY_TARGET");
            }
        } else if (mode == TestRunMode.FULL_SUITE_COVERAGE) {
            if (target != null) {
                throw new IllegalArgumentException("target must be null for FULL_SUITE_COVERAGE");
            }
            if (jacocoXmlPath == null) {
                throw new IllegalArgumentException("jacocoXmlPath must be provided for FULL_SUITE_COVERAGE");
            }
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}

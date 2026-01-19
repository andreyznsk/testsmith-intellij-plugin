package io.testsmith.plugin.testrunner;

public final class ExecResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    public ExecResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        this.exitCode = exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.timedOut = timedOut;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    public boolean timedOut() {
        return timedOut;
    }
}

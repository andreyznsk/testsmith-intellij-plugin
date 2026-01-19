package io.testsmith.plugin.testrunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class DefaultProcessExecutor implements ProcessExecutor {
    private static final long JOIN_TIMEOUT_MS = 1500L;

    @Override
    public ExecResult exec(List<String> command, Path workingDirectory, Map<String, String> env, Duration timeout) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.environment().putAll(env);

        String stdout = "";
        String stderr;
        int exitCode;
        boolean timedOut = false;

        try {
            Process process = builder.start();
            StreamCollector outCollector = new StreamCollector(process.getInputStream());
            StreamCollector errCollector = new StreamCollector(process.getErrorStream());
            Thread outThread = new Thread(outCollector, "maven-stdout-reader");
            Thread errThread = new Thread(errCollector, "maven-stderr-reader");
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                process.destroyForcibly();
                process.waitFor(JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                exitCode = process.exitValue(); // try/catch IllegalThreadStateException
            } else {
                exitCode = process.exitValue();
            }

            outThread.join(JOIN_TIMEOUT_MS);
            errThread.join(JOIN_TIMEOUT_MS);
            stdout = outCollector.output();
            stderr = errCollector.output();
        } catch (IOException e) {
            stderr = "Failed to start process: " + e.getMessage();
            exitCode = -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stderr = "Process interrupted";
            exitCode = -1;
        }

        return new ExecResult(exitCode, stdout, stderr, timedOut);
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream inputStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(InputStream inputStream) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        }

        @Override
        public void run() {
            try (InputStream stream = inputStream) {
                byte[] chunk = new byte[4096];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
            } catch (IOException ignored) {
                // Best effort; failures are handled via process exit and output diagnostics.
            }
        }

        private String output() {
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

package io.testsmith.plugin.agent;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ProgressListener {
    void onProgressChanged(@NotNull AgentProgress progress);
}

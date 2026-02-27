package io.testsmith.plugin.agent;

public interface AgentSession {
    void start();

    void requestStop();

    boolean isRunning();
}

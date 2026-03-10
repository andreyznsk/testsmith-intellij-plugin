package io.testsmith.plugin.ui.controller;

public interface AgentController {
    void start();

    void stop();

    void analyzeCoverage();

    void approve();

    void reject();

    void openSettings();

    void editProposal();
}

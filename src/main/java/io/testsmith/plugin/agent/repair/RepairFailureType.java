package io.testsmith.plugin.agent.repair;

public enum RepairFailureType {
    COMPILATION,
    ASSERTION,
    MISSING_IMPORT,
    INFRASTRUCTURE,
    MISSING_JACOCO_XML,
    OTHER_TEST_FAILURE,
    TIMEOUT,
    UNKNOWN
}

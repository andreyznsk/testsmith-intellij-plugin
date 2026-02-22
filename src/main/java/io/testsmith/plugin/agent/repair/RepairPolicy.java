package io.testsmith.plugin.agent.repair;

public final class RepairPolicy {
    public static final int MAX_REPAIR_ATTEMPTS = 2;

    public boolean repairAllowed(RepairFailureType failureType) {
        return switch (failureType) {
            case COMPILATION, ASSERTION, MISSING_IMPORT -> true;
            case INFRASTRUCTURE, MISSING_JACOCO_XML, OTHER_TEST_FAILURE, TIMEOUT, UNKNOWN -> false;
        };
    }
}

package io.testsmith.plugin.agent.repair;

public final class RepairPolicy {
    public static final int MAX_REPAIR_ATTEMPTS = 2;

    public boolean repairAllowed(RepairFailureType failureType) {
        return switch (failureType) {
            case ASSERTION,
                 COMPILATION_MISSING_IMPORT,
                 COMPILATION_MISSING_TYPE,
                 COMPILATION_MISSING_SYMBOL,
                 COMPILATION_OTHER -> true;
            case INFRASTRUCTURE, MISSING_JACOCO_XML, OTHER_TEST_FAILURE, TIMEOUT, UNKNOWN -> false;
        };
    }
}

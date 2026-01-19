package io.testsmith.plugin.agent.coverage.model;

import java.util.Objects;
import java.util.Set;

public record ClassCoverage(
        ClassId classId,
        PackageName packageName,
        int lineCovered,
        int lineMissed,
        int instrCovered,
        int instrMissed,
        int branchCovered,
        int branchMissed,
        Set<Integer> missedLineNumbers,
        String sourceFileName) {
    public ClassCoverage {
        Objects.requireNonNull(classId, "classId must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(missedLineNumbers, "missedLineNumbers must not be null");

        validateLineCoverage(lineCovered, lineMissed, missedLineNumbers);
        validateInstructionCoverage(instrCovered, instrMissed);
        validateBranchCoverage(branchCovered, branchMissed);

        for (Integer missedLine : missedLineNumbers) {
            if (missedLine == null || missedLine <= 0) {
                throw new IllegalArgumentException("missedLineNumbers must contain only positive line numbers");
            }
        }

        missedLineNumbers = Set.copyOf(missedLineNumbers);
    }

    public String className() {
        return classId.value();
    }

    public String packageNameValue() {
        return packageName.value();
    }

    public int totalLines() {
        if (lineCovered == -1 || lineMissed == -1) {
            return 0;
        }
        return lineCovered + lineMissed;
    }

    private static void validateLineCoverage(int lineCovered, int lineMissed, Set<Integer> missedLineNumbers) {
        if (lineCovered < -1 || lineMissed < -1) {
            throw new IllegalArgumentException("line coverage counts must be -1 or non-negative");
        }
        if ((lineCovered == -1) != (lineMissed == -1)) {
            throw new IllegalArgumentException("line coverage must be consistently available or unavailable");
        }
        if (lineCovered >= 0) {
            if (lineCovered + lineMissed < 0) {
                throw new IllegalArgumentException("line coverage counts must not overflow");
            }
            if (lineCovered + lineMissed > 0 && missedLineNumbers.size() != lineMissed) {
                throw new IllegalArgumentException(
                        "missedLineNumbers size must equal lineMissed when line coverage is available"
                );
            }
        } else if (!missedLineNumbers.isEmpty()) {
            throw new IllegalArgumentException("missedLineNumbers must be empty when line coverage is unavailable");
        }
    }

    private static void validateInstructionCoverage(int instrCovered, int instrMissed) {
        if (instrCovered < 0 || instrMissed < 0) {
            throw new IllegalArgumentException("instruction coverage counts must be non-negative");
        }
    }

    private static void validateBranchCoverage(int branchCovered, int branchMissed) {
        if (branchCovered < 0 || branchMissed < 0) {
            throw new IllegalArgumentException("branch coverage counts must be non-negative");
        }
    }
}

package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;

import java.util.Comparator;
import java.util.Locale;

public final class DefaultCandidateRanker {
    public Comparator<ClassCoverage> comparator(SelectionTuning tuning) {
        return Comparator
                .comparingInt(this::missedMetric).reversed()
                .thenComparingDouble(this::coverageRatio)          // меньше ratio = хуже = выше приоритет (asc ок)
                .thenComparing(Comparator.comparingInt(ClassCoverage::branchMissed).reversed())
                .thenComparing(Comparator.comparingInt((ClassCoverage c) -> domainBias(c, tuning)).reversed())
                .thenComparing(ClassCoverage::className);

    }

    public int missedMetric(ClassCoverage coverage) {
        return coverage.lineMissed() != -1 ? coverage.lineMissed() : coverage.instrMissed();
    }

    public double coverageRatio(ClassCoverage coverage) {
        if (coverage.lineMissed() != -1 && coverage.lineCovered() != -1) {
            int total = coverage.lineCovered() + coverage.lineMissed();
            if (total == 0) {
                return 1.0;
            }
            return coverage.lineCovered() / (double) total;
        }

        int instrTotal = coverage.instrCovered() + coverage.instrMissed();
        if (instrTotal == 0) {
            return 1.0;
        }
        return coverage.instrCovered() / (double) instrTotal;
    }

    private int domainBias(ClassCoverage coverage, SelectionTuning tuning) {
        String packageName = coverage.packageNameValue().toLowerCase(Locale.ROOT);
        int bias = 0;
        for (String keyword : tuning.positivePackageKeywords()) {
            if (packageName.contains(keyword.toLowerCase(Locale.ROOT))) {
                bias += 1;
                break;
            }
        }
        for (String keyword : tuning.negativePackageKeywords()) {
            if (packageName.contains(keyword.toLowerCase(Locale.ROOT))) {
                bias -= 1;
                break;
            }
        }
        return bias;
    }
}

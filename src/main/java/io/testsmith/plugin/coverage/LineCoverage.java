package io.testsmith.plugin.coverage;

public record LineCoverage(int missed, int covered) {
    public int total() {
        return missed + covered;
    }

    public double ratio() {
        return total() == 0 ? 1.0 : (double) covered / total();
    }
}

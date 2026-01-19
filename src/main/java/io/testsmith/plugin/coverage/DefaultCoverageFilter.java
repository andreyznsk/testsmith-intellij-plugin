package io.testsmith.plugin.coverage;

public final class DefaultCoverageFilter implements CoverageFilter {
    @Override
    public boolean include(ClassCoverage coverage) {
        String className = coverage.className();
        String packageName = packageName(className);
        if (packageName.contains(".dto") || packageName.contains(".config") || packageName.contains(".generated")) {
            return false;
        }
        String simpleName = simpleName(className);
        return !simpleName.endsWith("Dto") && !simpleName.endsWith("Config");
    }

    private static String packageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot == -1 ? "" : className.substring(0, lastDot);
    }

    private static String simpleName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot == -1 ? className : className.substring(lastDot + 1);
    }
}

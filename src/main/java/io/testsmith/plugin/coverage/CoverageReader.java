package io.testsmith.plugin.coverage;

import java.nio.file.Path;

public interface CoverageReader {
    CoverageSnapshot read(Path jacocoXml);
}

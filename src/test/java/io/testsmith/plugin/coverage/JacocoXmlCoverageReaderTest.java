package io.testsmith.plugin.coverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JacocoXmlCoverageReaderTest {
    private final JacocoXmlCoverageReader reader = new JacocoXmlCoverageReader();

    @Test
    void happyPathParsesPackagesAndClasses() throws Exception {
        CoverageSnapshot snapshot = reader.read(resourcePath("jacocoTest/happy-path-2pkgs-5classes.xml"));

        assertEquals(2, snapshot.packages().size());
        assertEquals(5, totalClasses(snapshot));

        assertTrue(snapshot.packages().containsKey("com.acme.service"));
        assertTrue(snapshot.packages().containsKey("com.acme.dto"));

        ClassCoverage orderService = snapshot.packages()
                .get("com.acme.service")
                .classes()
                .get("OrderService");
        assertNotNull(orderService);
        assertEquals(7, orderService.lineCoverage().missed());
        assertEquals(3, orderService.lineCoverage().covered());
    }

    @Test
    void missingLineCounterSkipsClass() throws Exception {
        CoverageSnapshot snapshot = reader.read(resourcePath("jacocoTest/missing-line-counter.xml"));

        assertFalse(snapshot.packages()
                .get("com.acme.service")
                .classes()
                .containsKey("HasOnlyInstruction"));
        assertTrue(snapshot.packages()
                .get("com.acme.service")
                .classes()
                .containsKey("Normal"));
    }

    @Test
    void zeroTotalLinesGivesFullRatio() throws Exception {
        CoverageSnapshot snapshot = reader.read(resourcePath("jacocoTest/zero-total-lines.xml"));

        ClassCoverage emptyCoverage = snapshot.packages()
                .get("com.acme.edge")
                .classes()
                .get("EmptyCoverage");
        assertNotNull(emptyCoverage);
        assertEquals(0, emptyCoverage.lineCoverage().total());
        assertEquals(1.0, emptyCoverage.lineCoverage().ratio(), 1e-12);
    }

    @Test
    void exclusionsAndSelectionOnlyKeepDomainInvoice() throws Exception {
        CoverageSnapshot snapshot = reader.read(resourcePath("jacocoTest/exclusions.xml"));
        CoverageFilter filter = new DefaultCoverageFilter();
        LowestLineCoveragePolicy policy = new LowestLineCoveragePolicy(filter);

        List<ClassCoverage> included = snapshot.packages().values().stream()
                .flatMap(pkg -> pkg.classes().values().stream())
                .filter(filter::include)
                .toList();

        assertEquals(1, included.size());
        assertEquals("com.acme.domain.Invoice", included.getFirst().className());

        Optional<ClassCoverage> selected = policy.select(snapshot);
        assertTrue(selected.isPresent());
        assertEquals("com.acme.domain.Invoice", selected.get().className());
    }

    @Test
    void tieBreakerSelectsHighestMissedWhenRatioEqual() throws Exception {
        CoverageSnapshot snapshot = reader.read(resourcePath("jacocoTest/weakest-tie-breaker.xml"));
        LowestLineCoveragePolicy policy = new LowestLineCoveragePolicy(new DefaultCoverageFilter());

        Optional<ClassCoverage> selected = policy.select(snapshot);
        assertTrue(selected.isPresent());
        assertEquals("com.acme.service.BService", selected.get().className());
    }

    @Test
    void missingFileThrowsException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.xml");
        assertThrows(IllegalStateException.class, () -> reader.read(missing));
    }

    @Test
    void malformedXmlThrowsException(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.xml");
        Files.writeString(broken, "<report><package></report>");
        assertThrows(IllegalStateException.class, () -> reader.read(broken));
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        var url = JacocoXmlCoverageReaderTest.class.getClassLoader().getResource(resource);
        assertNotNull(url, "Resource not found: " + resource);
        return Path.of(url.toURI());
    }

    private static int totalClasses(CoverageSnapshot snapshot) {
        return snapshot.packages().values().stream()
                .mapToInt(pkg -> pkg.classes().size())
                .sum();
    }
}

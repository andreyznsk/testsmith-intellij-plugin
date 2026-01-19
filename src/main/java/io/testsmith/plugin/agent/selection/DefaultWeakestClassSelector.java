package io.testsmith.plugin.agent.selection;

import io.testsmith.plugin.agent.coverage.model.ClassCoverage;
import io.testsmith.plugin.agent.coverage.model.CoverageSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Default weakest-class policy that applies: hard exclusions, layered ranking, stagnation guard,
 * and deterministic tie-breaking.
 *
 * <p>Defaults are provided by {@link SelectionTuning#defaults()} (maxSameClassRepeats = 2,
 * blacklistIterations = 3, and domain bias keywords). Exclusions apply to test/generated modules,
 * module-info/package-info, and classes without executable misses.</p>
 */
public final class DefaultWeakestClassSelector implements WeakestClassSelector {
    private final DefaultCandidateFilter filter;
    private final DefaultCandidateRanker ranker;
    private final DefaultStagnationGuard stagnationGuard;

    public DefaultWeakestClassSelector(SelectionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        this.ranker = new DefaultCandidateRanker();
        this.filter = new DefaultCandidateFilter(context.exclusions());
        this.stagnationGuard = new DefaultStagnationGuard(ranker);
    }

    @Override
    public Optional<ClassCoverage> select(CoverageSnapshot snapshot, SelectionContext context) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(context, "context must not be null");

        SelectionState state = context.state();
        state.decrementBlacklist();

        List<ClassCoverage> candidates = snapshot.classes().values().stream()
                .filter(filter::include)
                .filter(coverage -> !state.isBlacklisted(coverage.className()))
                .sorted(ranker.comparator(context.tuning()))
                .toList();

        Optional<ClassCoverage> selected = stagnationGuard.select(candidates, context);
        selected.ifPresent(coverage -> {
            String fingerprint = snapshotFingerprint(snapshot);
            state.recordSelection(coverage.className());
            state.updateSnapshotFingerprint(fingerprint);
            state.updateMissedMetric(coverage.className(), ranker.missedMetric(coverage));
        });

        return selected;
    }

    private String snapshotFingerprint(CoverageSnapshot snapshot) {
        CRC32 crc32 = new CRC32();
        String payload = snapshot.timestamp().toEpochMilli()
                + ":" + snapshot.summary().totalLines()
                + ":" + snapshot.summary().coveredLines()
                + ":" + snapshot.summary().missedLines()
                + ":" + snapshot.classes().size();
        crc32.update(payload.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc32.getValue());
    }
}

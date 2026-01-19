package io.testsmith.plugin.agent.selection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExclusionRulesTest {
    @Test
    void exactClassExclusion() {
        ExclusionRules rules = new ExclusionRules(List.of(), List.of("com.a.B"));

        assertTrue(rules.isExcluded("com.a.B"));
        assertFalse(rules.isExcluded("com.a.C"));
    }

    @Test
    void packagePrefixExclusion() {
        ExclusionRules rules = new ExclusionRules(List.of("com.a"), List.of());

        assertTrue(rules.isExcluded("com.a.B"));
        assertFalse(rules.isExcluded("com.ab.C"));
    }

    @Test
    void packageWildcardBehavesLikePrefix() {
        ExclusionRules rules = new ExclusionRules(List.of("com.a.*"), List.of());

        assertTrue(rules.isExcluded("com.a.B"));
        assertTrue(rules.isExcluded("com.a.sub.C"));
    }

    @Test
    void trimsAndIgnoresEmptyEntries() {
        ExclusionRules rules = new ExclusionRules(
                java.util.Arrays.asList("  ", " com.a ", null),
                java.util.Arrays.asList(" ", "com.a.B ")
        );

        assertTrue(rules.isExcluded("com.a.B"));
        assertTrue(rules.isExcluded("com.a.C"));
        assertFalse(rules.isExcluded("com.b.C"));
    }

    @Test
    void defaultPackageNeverMatchesPackageRule() {
        ExclusionRules rules = new ExclusionRules(List.of("com.a"), List.of());
        assertFalse(rules.isExcluded("A"));
    }
}

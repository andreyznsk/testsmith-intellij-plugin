Task: Implement “Weakest class selection policy” (Iteration 1.9)
Goal

Реализовать детерминированный и расширяемый механизм выбора следующего класса для генерации тестов на основе JaCoCo CoverageSnapshot, с hard-exclusions, ranking и anti-stagnation.

Scope

Входит:

API/контракты:

WeakestClassSelector

SelectionContext (rules/tuning/state)

минимальные доменные модели для покрытия (если уже есть — расширить)

Default implementation:

DefaultWeakestClassSelector (композиция стадий: filter → rank → stagnation → tie-break)

DefaultCandidateFilter (hard exclusions)

DefaultCandidateRanker (слоистый компаратор)

DefaultStagnationGuard (blacklist на M итераций при отсутствии прогресса)

Unit tests (минимум 6 тестов — см. Acceptance Criteria)

Не входит:

PSI-анализ (это Iteration 4+)

UI/Settings экран (достаточно POJO-конфигов)

запись в disk cache (можно держать state в памяти)

Inputs / Data Contracts
CoverageSnapshot

Должен давать список ClassCoverage (или аналог):

ClassCoverage must include:

String className (FQCN)

String packageName

int lineCovered, int lineMissed (может быть -1 если нет данных)

int instrCovered, int instrMissed

int branchCovered, int branchMissed (опционально, можно 0/0)

String sourceFileName (optional)

SelectionContext
public record SelectionContext(
ExclusionRules exclusions,
SelectionTuning tuning,
SelectionState state
) {}

ExclusionRules

user-defined patterns: package/class include/exclude (glob или regex — выбрать один и придерживаться)

defaults for tests/generated/do-not-test

SelectionTuning (defaults)

int maxSameClassRepeats = 2 (N)

int blacklistIterations = 3 (M)

domainBias rules (минимально: list of positive and negative package keywords)

SelectionState

Хранит:

String lastSelectedClass

int sameClassRepeatCount

map blacklist: className -> remainingIterations

last snapshot marker: String snapshotFingerprint (можно hash строки/чисел)

per-class last seen missed lines/instructions to detect “no improvement”

Selection Algorithm (must match)
Hard exclusions (mandatory)

Exclude candidate if:

matches user exclusion rules

is test class: name ends with Test, Tests, IT, IntegrationTest, contains E2E, or package contains .test.

module-info, package-info

contains generated-like markers: generated, proto, thrift, avro, jooq, swagger, openapi

has no executable misses: (lineMissed == 0 || lineMissed == -1) AND instrMissed == 0

Ranking (desc)

Use “layered comparator”, not weighted sum:

missedLines (if lineMissed != -1 else instrMissed)

1 - lineCoverage (worse is higher). If lines missing, use instruction coverage.

branchMissed (higher first)

domainBias:

+1 if package contains any of: domain, core, service, logic

-1 if contains: dto, config, model, entity, generated, api

final tie-breaker: className lexicographically asc

Anti-stagnation

If the same class is selected more than N times подряд and нет прогресса, then:

put it into blacklist for M selections (iterations)

select next best candidate

“Нет прогресса” считать так:

snapshotFingerprint changed OR not? (up to you), but minimum:

for selected class, missed metric (lines if present else instructions) did NOT decrease vs previous attempt.

Blacklist decrement every call.

Output

Optional<ClassCoverage>.

If no candidates => Optional.empty().

Acceptance Criteria

Unit tests (JUnit 5 unless project dictates otherwise):

picks class with highest missedLines

tie on missedLines → picks worse coverage%

excludes user patterns

excludes *Test / .test. package

fallback to instructions when lines missing

stagnation guard blacklists repeated non-improving target and picks next best

Also:

stable deterministic ordering (no hash-map iteration dependence)

no NPEs on missing optional fields

Deliverables

Source code in .../agent/selection/... (package naming consistent with project)

Unit tests

Short README section or JavaDoc for policy rules and defaults
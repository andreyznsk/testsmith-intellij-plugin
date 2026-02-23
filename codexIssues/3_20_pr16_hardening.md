# Iteration 3.20 PR #16 Hardening

This hardening pass removes ambiguity and improves determinism in the Fix-Test loop. Repair now has a single structured action (`REPAIR_TEST`), stricter contract enforcement (`version=1.1` + requested action), and richer failure typing for compilation errors so repair prompts receive precise context instead of a generic compilation bucket. The validator was also relaxed for valid Java file starts (`final`, `abstract`, annotation-first, interface/enum/record declarations) while keeping wrapper prose rejection deterministic.

Operationally, repair attempt logging is now post-mortem friendly: each attempt records `initialFailureType` and recomputed `postRepairFailureType` after re-verify, plus outcome and duration. This makes state transitions explicit across retries and prevents stale failure labels from masking what actually failed after a repair attempt.

## DoD Checklist

- [x] A1: attemptNumber is 1-based everywhere; attemptsUsed is a count (0 when no repair attempted)
- [x] A2: deprecated FIX_TEST action rejects with SCHEMA_INVALID and message contains FIX_TEST
- [x] B1: no raw List/Map types in StructuredTest/GenerationContext contracts
- [x] B2: repair context always requests version 1.1 and action REPAIR_TEST; validator emits consistent messages
- [x] B3: RepairFailureClassifier prioritizes IMPORT > TYPE > SYMBOL > OTHER with tests for overlaps

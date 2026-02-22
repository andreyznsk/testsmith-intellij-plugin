# Iteration 3.20 PR #16 Hardening

This hardening pass removes ambiguity and improves determinism in the Fix-Test loop. Repair now has a single structured action (`REPAIR_TEST`), stricter contract enforcement (`version=1.1` + requested action), and richer failure typing for compilation errors so repair prompts receive precise context instead of a generic compilation bucket. The validator was also relaxed for valid Java file starts (`final`, `abstract`, annotation-first, interface/enum/record declarations) while keeping wrapper prose rejection deterministic.

Operationally, repair attempt logging is now post-mortem friendly: each attempt records `initialFailureType` and recomputed `postRepairFailureType` after re-verify, plus outcome and duration. This makes state transitions explicit across retries and prevents stale failure labels from masking what actually failed after a repair attempt.

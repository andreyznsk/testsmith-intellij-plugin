TASK: Iteration 2.13 — Test execution result model

Context:
We are building TestSmith (IntelliJ IDEA plugin + core). Iteration 2 implements test execution (Maven/Gradle, two-phase: VERIFY_TARGET then FULL_SUITE_COVERAGE). Now we need a unified, build-tool-agnostic result model that runners return and that the agent loop can consume.

Hard invariant:
TestSmith never modifies build configuration, test infrastructure, or production code automatically.

Goal:
Introduce a deterministic, immutable “test execution result” model with clear classification of outcomes, capture of stdout/stderr and duration, and phase awareness (VERIFY_TARGET vs FULL_SUITE_COVERAGE). Runners should map their raw process outputs into this model.

Scope:
1) Add core model types:
    - enum TestExecutionPhase { VERIFY_TARGET, FULL_SUITE_COVERAGE }
    - enum TestExecutionStatus { SUCCESS, COMPILATION_FAILED, TEST_FAILED, INFRASTRUCTURE_ERROR, TIMEOUT }
    - enum TestFailureKind { ASSERTION, EXCEPTION, COMPILATION, CONTEXT_INITIALIZATION, UNKNOWN }  // optional enrichment later
    - record TestExecutionResult(
      TestExecutionPhase phase,
      TestExecutionStatus status,
      @Nullable TestFailureKind failureKind,
      @Nullable String failedTest,          // fqcn or fqcn#method
      @Nullable String failureMessage,
      String stdout,
      String stderr,
      Duration duration
      )
    - Convenience methods on result:
      boolean isSuccess()
      boolean isFixableByLlm()           // true for TEST_FAILED or COMPILATION_FAILED
      boolean isInfrastructureProblem()  // true for INFRASTRUCTURE_ERROR or TIMEOUT

2) Update runners to return TestExecutionResult (minimal mapping for now):
    - Maven runner:
        - SUCCESS when exitCode == 0
        - TIMEOUT if timedOut flag is true
        - Otherwise: classify as TEST_FAILED by default
        - If output indicates compilation error OR Maven compilation failure markers => COMPILATION_FAILED (simple heuristic allowed; do not overfit)
        - If Maven invocation itself failed (e.g., “Could not resolve dependencies”, “No such file or directory”, tool not found) => INFRASTRUCTURE_ERROR
    - Gradle runner: same semantics
      NOTE: The classification can be “best effort” but must be deterministic and documented.

3) Add a FailureExtractor interface stub for future enrichment (no heavy parsing now):
    - interface FailureExtractor { TestExecutionResult enrich(TestExecutionResult raw); }
    - Provide a NoopFailureExtractor implementation that returns raw unchanged.

4) Ensure invariants:
    - stdout/stderr must never be null (use empty string)
    - duration must always be present
    - if status == SUCCESS then failureKind/failedTest/failureMessage must be null
    - if phase == FULL_SUITE_COVERAGE and status == SUCCESS, the runner should verify JaCoCo XML exists; if missing, return INFRASTRUCTURE_ERROR with a meaningful failureMessage.

5) Tests:
    - Unit tests for the model convenience methods and invariants.
    - Unit tests for classification mapping heuristics for Maven and Gradle:
        - timeout -> TIMEOUT
        - exit 0 -> SUCCESS
        - compilation markers -> COMPILATION_FAILED
        - infra markers -> INFRASTRUCTURE_ERROR
        - fallback -> TEST_FAILED
    - Use small representative stdout/stderr fixtures as strings (no integration tests).

Non-goals:
- Do not add/modify JaCoCo configuration.
- Do not implement deep log parsing or multi-failure listing.
- Do not change production project code.

Implementation notes:
- Place model in a package consistent with existing structure, e.g. io.testsmith.plugin.testrunner.model (or similar existing module). Prefer “core” if there is a shared core package.
- Keep API minimal and stable. Prefer records and enums.
- Avoid leaking build-tool specifics into the model.

Deliverable:
A PR implementing Iteration 2.13 with:
- new model types + tests
- updated Maven/Gradle runners returning TestExecutionResult
- minimal deterministic classification heuristics
- jacoco xml existence check in FULL_SUITE_COVERAGE success path

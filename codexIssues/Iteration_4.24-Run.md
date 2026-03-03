# Iteration 4.24 — Run / Stop Actions (UI → Agent Control)

## Goal

Introduce deterministic and safe **Run / Stop actions** in the IntelliJ Tool Window
to control the agent lifecycle in both:

- 🟢 Manual Mode
- 🔴 Autonomous Mode

The implementation must strictly respect RFC invariants:

- No automatic build modification
- No production code changes
- Human remains in control
- Execution must be deterministic

---

## Context

We already have:

- Agent loop abstraction
- Maven & Gradle two-phase TestRunner (2.11 / 2.12)
- Manual approval flow
- UI Tool Window skeleton

What is missing:

- Explicit lifecycle control
- Proper cancellation semantics
- State synchronization between UI and Agent
- Safe termination guarantees

---

## Functional Requirements

### 1️⃣ Run Action

When user clicks **Run**:

- Validate configuration:
  - LLM configured
  - JaCoCo path configured
  - Build tool detected
- Initialize new AgentSession
- Reset previous state
- Transition UI state to:

```

IDLE → RUNNING

```

- Disable:
  - Run button
- Enable:
  - Stop button

- Start agent loop in background (non-blocking UI)

---

### 2️⃣ Stop Action

When user clicks **Stop**:

- Signal cancellation token
- Agent must:
  - Finish current atomic step
  - NOT start next iteration
- Transition state:

```

RUNNING → STOPPING → IDLE

````

- Ensure:
  - No test execution continues
  - No file write happens after stop signal
  - No UI deadlock

Stop must be:

- Idempotent
- Thread-safe
- Deterministic

---

## Non-Goals

- No pause/resume (future iteration)
- No parallel agent sessions
- No background auto-start
- No multi-project orchestration

---

## Architecture Design

### AgentSession

New abstraction:

```java
public interface AgentSession {
    void start();
    void requestStop();
    boolean isRunning();
}
````

Responsibilities:

* Hold cancellation token
* Own lifecycle state
* Own iteration counter
* Own execution context snapshot

---

### Cancellation Model

Use:

```java
AtomicBoolean stopRequested
```

Agent loop must check:

* Before each iteration
* Before test write
* Before test execution
* Before LLM call

Hard rule:
No blocking operation may ignore cancellation.

---

### UI State Model

Introduce explicit UI state enum:

```java
enum AgentUiState {
    IDLE,
    RUNNING,
    STOPPING,
    ERROR
}
```

UI must react only to state changes,
not to internal agent events directly.

---

## Threading Model

* UI thread: button interactions
* Background thread: agent loop
* No blocking on EDT
* No busy waiting

Preferred:

* IntelliJ BackgroundTask
* or ExecutorService with proper disposal

---

## Failure Modes to Handle

1. Stop during:

    * LLM generation
    * Test execution
    * Coverage parsing
    * Diff generation

2. Double-click Run

3. Stop without Run

4. Project disposed during execution

5. Gradle daemon hanging

Each must be:

* Safe
* No resource leak
* No zombie process

---

## Logging Requirements

Log structured lifecycle events:

```
[Agent] START
[Agent] ITERATION 1
[Agent] STOP_REQUESTED
[Agent] TERMINATED
```

Logs must be visible in Tool Window console.

---

## Acceptance Criteria

* Run starts loop
* Stop halts loop safely
* UI state always consistent
* No thread leaks
* No duplicate sessions
* No test executed after stop
* No file written after stop

Manual mode must still require approval.

Autonomous mode must stop when requested.

---

## Edge Case Validation

Test scenarios:

* Rapid Run → Stop → Run
* Stop during failing test
* Stop during compilation error
* Stop when LLM times out
* Stop when coverage file missing

---

## Invariants (Must Not Be Violated)

* Build configuration never modified
* No JaCoCo injection
* No silent background execution
* User always aware of active session
* Only one active AgentSession per project

---

## Deliverables

1. AgentSession abstraction
2. Cancellation-aware agent loop
3. Tool Window Run/Stop wiring
4. Deterministic state transitions
5. Thread-safe implementation
6. PR ready for Codex-level review

---

## Open Questions (to resolve during implementation)

* Should Stop interrupt running process (Process.destroy)?
* Should we differentiate soft stop vs hard stop?
* Should we debounce Run clicks?
* How to surface STOPPING state visually?

---

## Definition of Done

* Implementation merged in iteration-4.24-run-stop
* No architectural violations of RFC
* Clean shutdown confirmed via manual tests
* Code review approved
* RFC unchanged (no architectural shift required)

```
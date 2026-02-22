## Iteration 3.20 — Fix-Test Loop (Codex Task Specification)

### Goal

Design and formalize the **Fix-Test loop mechanism** inside Iteration 3 (Single LLM / Ollama), enabling deterministic test repair after targeted execution failure — without violating TestSmith invariants.

---

## Context

Iteration 3 already includes:

* Structured LLM output
* StrictStructuredResponseParser
* Deterministic generation
* VERIFY_TARGET execution phase
* Failure extraction

Now we introduce a **controlled repair loop** between:

```
VERIFY_TARGET → failure → LLM fix → VERIFY_TARGET (retry)
```

This must be:

* deterministic
* bounded
* infrastructure-safe
* hallucination-resistant
* compatible with Manual & Autonomous modes

---

# 🎯 Task for Codex

## Title

Implement Bounded Fix-Test Loop with Structured Repair Contract

---

## 1️⃣ Architectural Objective

Introduce a new component:

```java
public interface TestRepairAgent {
    RepairResult attemptRepair(
        StructuredTest originalTest,
        TestExecutionResult failure,
        RepairContext context
    );
}
```

### Responsibilities

* Accept failed test + structured failure
* Generate corrected version
* Re-validate structure
* Return deterministic repair result

---

## 2️⃣ Repair Loop Specification

### State Machine

```text
GENERATED
   ↓
VERIFY_TARGET
   ↓
FAILED ?
   ↓ yes
REPAIR_ATTEMPT (bounded)
   ↓
VERIFY_TARGET
   ↓
SUCCESS → exit
FAIL (max retries) → abort
```

---

## 3️⃣ Hard Constraints (Invariant-Safe)

The repair loop MUST NOT:

* modify production code
* modify build files
* modify test infrastructure
* introduce new dependencies
* switch test framework
* invent new DTO fields

Violation = HARD ABORT

Invariant reference:

---

## 4️⃣ Retry Policy

```java
int MAX_REPAIR_ATTEMPTS = 2;
```

Reasoning:

* 1 = too brittle
* > 2 = high hallucination risk
* deterministic behavior preferred

---

## 5️⃣ Failure Classification Integration

Repair is allowed only for:

| Failure Type           | Repair Allowed |
| ---------------------- | -------------- |
| Compilation failure    | ✅              |
| Assertion failure      | ✅              |
| Missing import         | ✅              |
| Infrastructure failure | ❌              |
| Missing JaCoCo XML     | ❌              |

Infrastructure errors must short-circuit.

---

## 6️⃣ Structured Repair Prompt Contract

Extend structured response schema:

```json
{
  "version": "1.1",
  "action": "REPAIR_TEST",
  "targetClass": "...",
  "testClassName": "...",
  "imports": [...],
  "code": "...",
  "confidence": 0.87
}
```

New rule:

* action MUST be REPAIR_TEST
* version MUST match expected

If mismatch → parser reject.

---

## 7️⃣ Determinism Requirements

Repair prompt must include:

* original generated test
* exact compiler/test error
* minimal project context
* failure classification

Temperature must remain 0.1.

No randomness escalation.

---

## 8️⃣ Integration with Agent Loop

Modify main agent loop:

```java
TestExecutionResult result = runner.verifyTarget(test);

if (result.failed()) {
    if (repairAllowed(result)) {
        test = repairAgent.attemptRepair(test, result, context);
        retryVerify();
    } else {
        abortIteration();
    }
}
```

---

## 9️⃣ Manual vs Autonomous Behavior

### Manual Mode

* Show diff between:

    * original test
    * repaired test
* User must approve repaired version

### Autonomous Mode

* Apply repair automatically
* Log attempt
* Stop after max retries

---

## 🔟 Logging Requirements

Each repair attempt must log:

```text
[RepairAttempt #1]
FailureType: COMPILATION
TargetClass: OrderService
Outcome: SUCCESS
Duration: 1432ms
```

Logs must allow post-mortem debugging.

---

# 📦 Deliverables for This Iteration

Codex should:

1. Propose interface design
2. Define RepairContext model
3. Define RepairResult model
4. Extend structured response schema
5. Update StrictStructuredResponseParser
6. Provide bounded loop implementation draft
7. List failure modes
8. Identify architectural risks

---

# 🧠 Architectural Risks to Address

* Repair loop masking real design issues
* Infinite oscillation between two broken states
* Test overfitting to implementation
* False-positive green test
* Hidden infrastructure side effects

Codex must explicitly analyze these.

---

# 🚦 Success Criteria

Iteration is complete when:

* Repair loop is deterministic
* No invariant violations possible
* Max retries enforced
* Manual & Autonomous modes respected
* Structured contract validated
* No test framework mixing
* No infrastructure mutation possible

---

If accepted, next step:
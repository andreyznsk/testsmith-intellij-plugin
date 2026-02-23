# Structured Output Validation Notes (Iteration 3)

## Architecture boundary

- The transport layer (`LlmClient`) now returns raw LLM text only.
- Structured parsing and validation are enforced in agent code (`io.testsmith.plugin.agent.generation.structured`).
- Agent flow is: `LLM raw text -> StrictStructuredResponseParser -> DefaultStructuredValidator -> agent-safe StructuredTest`.

## Canonical schema (v1)

- Contract version: `1.0`
- Required fields: `version`, `action`, `targetClass`, `testClassName`, `imports`, `code`, `assumptions`, `requiresInfrastructure`
- Reserved optional fields (accepted and ignored by core validation): `confidence`, `metadata`
- Parsing is strict JSON only and rejects unknown fields.

## Validation and failure modes

- `STRUCTURE_INVALID`: payload is not strict JSON object (or invalid JSON)
- `SCHEMA_INVALID`: missing/unknown fields or invalid field types/enums
- `SEMANTIC_INVALID`: target/action/class-name/code invariants violated
- `INFRASTRUCTURE_REQUIRED`: `requiresInfrastructure=true` (blocked in Iteration 3)

## Retry policy

- Deterministic retry policy with max retries = 2
- Retries are allowed for `STRUCTURE_INVALID`, `SCHEMA_INVALID`, `SEMANTIC_INVALID`
- `INFRASTRUCTURE_REQUIRED` is terminal and aborts immediately
- Retry hints are injected into request notes with `[structured-retry]` prefix

## Version evolution

- Introduce future versions as new parser+validator contracts in parallel (e.g., v2)
- Keep v1 behavior stable and immutable for deterministic replay
- Agent can route by `version` if multi-version support is added later

## Iteration 3.20: Bounded Fix-Test loop

- Added `TestRepairAgent` contract and `LlmTestRepairAgent` implementation.
- Added deterministic bounded loop (`BoundedFixTestLoop`) with `MAX_REPAIR_ATTEMPTS=2`.
- Repair is allowed only for classified failures: `COMPILATION`, `ASSERTION`, `MISSING_IMPORT`.
- Infrastructure failures (`INFRASTRUCTURE`, `MISSING_JACOCO_XML`, `TIMEOUT`) short-circuit with abort.
- Manual mode requires diff+approval callback; autonomous mode auto-applies repairs.
- Per-attempt logging format:
  - `[RepairAttempt #n]`
  - `FailureType: ...`
  - `TargetClass: ...`
  - `Outcome: ...`
  - `Duration: ...ms`
- Repair schema contract:
  - `version=1.1`
  - `action=REPAIR_TEST`
  - parser remains strict JSON object + known fields only.

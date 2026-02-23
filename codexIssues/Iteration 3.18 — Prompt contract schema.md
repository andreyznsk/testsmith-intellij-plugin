Below is a **Codex task** for **Iteration 3.18 — Prompt contract schema** (aligned with the RFC invariants: no build/test infra/prod code modifications).

---

## Codex Task — Iteration 3.18: Prompt Contract Schema (Strict + Testable)

### Goal

Introduce a **strict, versioned prompt contract schema** for LLM test generation (Ollama, deterministic), so that:

* prompts are **structured, reproducible, and diffable**
* output is **machine-validated** (no “free text”)
* future multi-LLM providers can reuse the same contract

### Scope

1. **Prompt contract model**

    * Add a versioned contract object, e.g. `PromptContractV1`.
    * Must be **serializable** to JSON (for logging + debugging) and renderable to a final prompt string.
    * Must contain only information already available in `LlmRequest` + derived prompt metadata (like contractVersion).

2. **Prompt rendering**

    * Implement `PromptRenderer` that converts `PromptContractV1 -> String` using a **stable template**.
    * Template must include (in a fixed order):

        * Contract version
        * Target class FQCN
        * Target class source
        * Related sources
        * Detected test framework
        * Project test pattern notes
        * Generation mode (manual/autonomous or generate/fix)
        * Failure context (optional)
        * Deterministic generation constraints
        * **Output format instructions** (strict)

3. **Strict output contract**

    * Define `LlmResponseContractV1` that the model must emit as **JSON only** (no prose).
    * Fields must map 1:1 to `LlmResponse`:

        * `testClassFqcn`, `suggestedFilePath`, `testFramework`, `javaSource`, `notes`
    * Implement a `LlmResponseParser` that:

        * rejects non-JSON preamble/epilogue
        * rejects missing required fields
        * rejects invalid enum values (test framework)
        * returns a typed `LlmResponse`

4. **Validation + tests**

    * Add unit tests for:

        * prompt rendering stability (golden test: exact string match)
        * parser correctness (happy path + failure cases)
        * contract JSON serialization snapshot (optional but recommended)

### Non-goals

* No UI work
* No agent-loop policy changes
* No build script changes
* No RAG/embeddings

### Constraints (hard)

* Must preserve **“no hallucinations”** stance: prompt must explicitly forbid inventing fields/methods/infrastructure.
* Must preserve invariant: **TestSmith never modifies build configuration, test infrastructure, or production code automatically.**
* Must remain deterministic (stable ordering, stable formatting, stable serialization).

---

## Implementation Notes (expected design)

### Suggested package layout

* `io.testsmith.plugin.llm.prompt.contract`

    * `PromptContractV1`
    * `PromptRenderer`
    * `PromptContractFactory` (builds contract from `LlmRequest`)
* `io.testsmith.plugin.llm.prompt.response`

    * `LlmResponseContractV1`
    * `LlmResponseParser`

### Template requirements

* The final prompt should be a single string composed of clearly delimited sections, e.g.:

```
[TestSmith Prompt Contract v1]
## Target
...
## Source
...
## Related Sources
...
## Project Test Pattern Notes
...
## Failure Context (optional)
...
## Output Contract (MUST FOLLOW)
Return JSON with fields: ...
No markdown. No explanations. JSON only.
```

### Parser behavior (strict)

* If the model returns JSON wrapped in markdown fences, either:

    * reject (preferred strict), **or**
    * accept only if fences are trivially removable and no other text exists.
      Pick one and enforce in tests.

---

## Acceptance Criteria

* ✅ `PromptContractV1` exists, versioned, serializable, and renderable.
* ✅ `PromptRenderer` produces stable output (golden test passes).
* ✅ `LlmResponseParser` strictly validates output and returns `LlmResponse`.
* ✅ Unit tests cover:

    * valid response
    * missing field
    * extra leading text
    * invalid enum
    * invalid JSON
* ✅ No changes to build files, prod code, or test infra beyond adding these classes/tests.

---

## Deliverables

* Code + tests
* A short `notes.md` (or JavaDoc) explaining:

    * contract versioning strategy (how we will evolve v2)
    * strictness rules for parser
    * where prompt/response raw payloads should be logged later (not implemented here)

---

If you want, I can also draft the **exact prompt template text** for `PromptRenderer` (v1) to minimize iteration churn—just say “draft template”.

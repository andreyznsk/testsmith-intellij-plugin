## Codex Task — Iteration 3.16: LLM Client Abstraction (TestSmith)

Source of truth: `PROJECT_RFC.md`

### Goal

Introduce a **provider-agnostic LLM client layer** with a **strict prompt/response contract** so the agent can request test generation deterministically (Iteration 3 = single provider, Ollama), and Iteration 5 can add provider switching without refactors.

---

## Scope

### 1) Public API (core contract)

Create a small, stable contract in `io.testsmith.plugin.llm` (or equivalent core module):

#### Interfaces & models

* `LlmClient`

    * `LlmResponse generateTest(LlmRequest request) throws LlmException;`
* `LlmRequest`

    * `String targetClassFqcn`
    * `String targetClassSource` (raw source)
    * `List<String> relatedSources` (optional, may be empty)
    * `TestFramework testFramework` (enum: JUNIT4, JUNIT5; keep extendable)
    * `String projectTestPatternNotes` (discovered patterns; can be empty in MVP)
    * `GenerationMode mode` (GENERATE | FIX) *(optional now, but add for future)*
    * `String failureContext` *(only for FIX; else empty)*
    * `LlmTuning tuning` (temperature, topP, repeatPenalty, seed?; see below)
    * `Duration timeout`
    * `Map<String, String> metadata` (trace ids, iteration, etc.)
* `LlmResponse`

    * `String testClassFqcn`
    * `String suggestedFilePath` (relative path under test root if known; otherwise empty)
    * `TestFramework testFramework`
    * `String javaSource` (ONLY Java source, no markdown fences)
    * `List<String> notes` (optional: short bullet notes)
* `LlmTuning`

    * `double temperature`
    * `double topP`
    * `double repeatPenalty`

#### Errors

* `LlmException extends RuntimeException`
* Subtypes:

    * `LlmTransportException` (I/O, HTTP, timeouts)
    * `LlmProtocolException` (response not parseable / schema mismatch)
    * `LlmRefusalException` (provider refused / policy)
    * `LlmRateLimitException` (future-proof)
    * `LlmMisconfigurationException` (missing base URL / model)

---

### 2) Strict response format (structured output)

Define a **single canonical output format** for the LLM to return:

* LLM must return **JSON only** (no prose, no markdown), matching `LlmResponseJson` schema.
* Parse JSON strictly; on any mismatch → `LlmProtocolException`.
* `javaSource` must be:

    * valid Java compilation unit text
    * no ``` fences
    * no “Here is the test…” wrapper text

Deliverable: `LlmResponseParser` with:

* `LlmResponse parse(String rawText)`
* unit tests for good/bad payloads (missing fields, non-json, markdown, extra leading text, wrong enums)

---

### 3) Prompt builder (single place, deterministic)

Create `PromptComposer` (or `LlmPromptBuilder`) that:

* accepts `LlmRequest`
* builds a provider-neutral prompt that:

    * instructs the model to output JSON only
    * embeds the schema
    * includes target class source + minimal related context
    * includes explicit “do not invent methods/fields” constraints
    * includes test framework requirement (Junit4 vs Junit5)
* No randomization; stable ordering of fields and context.

Deliverable: snapshot-like tests for prompt generation (string contains key constraints + schema header).

---

### 4) Single-provider implementation stub (Ollama-ready, but safe)

Implement **one** concrete client (Iteration 3 local default):

* `OllamaLlmClient implements LlmClient`

    * Config:

        * baseUrl (default `http://localhost:11434`)
        * model (default `qwen2.5-coder:7b`)
    * Uses `java.net.http.HttpClient`
    * Respects `timeout`
    * Passes tuning parameters from request (defaults must match RFC: temp=0.1, top_p=0.9, repeat_penalty=1.1)
    * Returns parsed `LlmResponse`

**Important:** If you don’t want to commit to exact Ollama API payload details yet, you may:

* implement `OllamaLlmClient` behind an internal `OllamaApi` interface
* fully unit-test mapping + parsing using mocks
* add a lightweight “contract test” with a fake HTTP server (no real Ollama dependency)

---

## Non-goals (explicit)

* No IntelliJ UI, no settings UI, no agent loop wiring (unless already needed by compilation).
* No changes to build scripts, test frameworks, or project test infra.
* No multi-provider switching yet (Iteration 5).
* No embeddings/RAG.

---

## Acceptance Criteria

1. `LlmClient` is a stable, provider-agnostic interface with request/response models.
2. Prompt builder enforces:

    * **JSON only**
    * **no hallucinations**
    * **explicit test framework constraint**
3. Parser is strict and well-tested (success + multiple failure cases).
4. `OllamaLlmClient` exists, compiles, and is unit-tested via mocked HTTP / local fake server.
5. Deterministic defaults are present (temp=0.1, top_p=0.9, repeat_penalty=1.1).
6. No build/test infra modifications; all changes are within plugin code + tests.

---

## Suggested Package Layout

* `io.testsmith.plugin.llm.api`

    * `LlmClient`, `LlmRequest`, `LlmResponse`, exceptions, enums
* `io.testsmith.plugin.llm.prompt`

    * `PromptComposer`
* `io.testsmith.plugin.llm.parse`

    * `LlmResponseParser`
* `io.testsmith.plugin.llm.ollama`

    * `OllamaLlmClient`, `OllamaApi` (optional), DTOs for request/response

---

## Test Plan

* `LlmResponseParserTest`

    * parses valid JSON
    * rejects markdown fenced output
    * rejects leading prose before JSON
    * rejects missing required fields
    * rejects unknown enum values
* `PromptComposerTest`

    * includes schema
    * includes “JSON only” rule
    * includes “no invented fields/methods” rule
    * includes correct `testFramework`
* `OllamaLlmClientTest`

    * maps request → HTTP payload (assert fields)
    * handles non-200 → `LlmTransportException`
    * handles timeout → `LlmTransportException`
    * handles invalid JSON → `LlmProtocolException`

---

## Definition of Done

* Code + unit tests merged
* Public contract documented in javadoc on `LlmClient`, `LlmRequest`, and “response JSON rules”
* No TODOs that block Iteration 3.17 (wiring into agent)

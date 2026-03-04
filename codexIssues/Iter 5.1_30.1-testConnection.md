# Iteration 5.1 / 30.1 — Minor Improvements: Test Connection (LLM Providers)

## Goal
Add "Test connection" button to LLM Provider settings UI that performs a lightweight provider connectivity/auth check.
Must be safe: no file writes, no test execution, strict timeout, no secret leaks in logs/UI.

## Scope (DoD)
1) Settings UI:
    - Add a "Test connection" button in the LLM provider configuration section.
    - On click, execute provider health check asynchronously (no UI freeze).
    - Show result in UI: ✅ Success / ❌ Failed + short sanitized message.
    - Disable button while request in progress; allow retry afterwards.

2) Provider API contract:
    - Ensure LlmClient (or equivalent abstraction) exposes:
      `HealthCheckResult healthCheck();`
    - HealthCheckResult must contain:
        - status (OK/FAILED)
        - userMessage (safe to show)
        - optional technicalCode (HTTP code / exception type) but still sanitized
        - providerId (OPENAI/GIGACHAT/OLLAMA/...)
        - measured latency (ms) if easy

3) Provider implementations:
   Implement healthCheck() for each provider:
    - Ollama: call a lightweight endpoint like `/api/version` or `/api/tags`.
    - OpenAI: do a lightweight request (prefer a “models list” or minimal safe endpoint) with strict timeout.
    - GigaChat: minimal auth/connectivity check (token fetch or lightweight endpoint) with strict timeout.
      Requirements:
    - Strict timeouts (connect + read).
    - Never log API keys/tokens. If baseUrl includes credentials (rare) — sanitize.
    - Error mapping: auth error vs network error vs invalid baseUrl vs timeout.
    - Never perform generation, never touch filesystem, never run tests.

4) Diagnostics hardening:
    - Introduce a utility to sanitize secrets in any string:
        - mask API keys, Bearer tokens, long secrets, query params like `api_key=...`
    - Ensure health check errors shown in UI are sanitized.
    - Ensure logs (if any) are sanitized.

## Non-goals
- No changes to build scripts.
- No automatic test execution.
- No RAG / indexing improvements.

## Suggested Implementation Steps
1) Locate LLM abstraction:
    - Find `LlmClient` interface (likely `io.testsmith.plugin.llm` or similar).
    - Add `HealthCheckResult healthCheck()` if not already present.
    - Create `HealthCheckResult` model in the same module/package (immutable record preferred).

2) Implement healthCheck per provider:
    - Find provider clients (e.g. `OpenAiLlmClient`, `GigaChatLlmClient`, `OllamaLlmClient`).
    - Use existing HTTP client (OkHttp/HttpClient) with explicit timeouts.
    - Implement request + map to HealthCheckResult.

3) UI wiring:
    - Find settings UI classes (likely `io.testsmith.plugin.settings.*`).
    - Add button "Test connection".
    - When pressed:
        - run in background thread (IntelliJ `ProgressManager` / pooled thread)
        - call selected provider’s `healthCheck()`
        - update UI on EDT
    - Show result label under button or inline status row.

4) Secret masking:
    - Add `SecretSanitizer` utility.
    - Apply it to:
        - exception messages
        - http error bodies (if used at all — лучше не показывать body, только код + тип)
        - any logged diagnostics.

## Testing (minimum)
- Unit tests for `SecretSanitizer`:
    - masks `Bearer xxx`, `sk-...`, `api_key=...`, long random tokens.
- Unit tests for healthCheck error mapping (mock HTTP):
    - timeout -> FAILED + “Timeout”
    - 401/403 -> FAILED + “Authentication failed”
    - DNS/connection refused -> FAILED + “Cannot connect”
      (Use existing test framework in project; keep tests small.)

## Acceptance checklist
- Button exists in Settings and works without freezing UI.
- Each provider returns meaningful OK/FAILED result.
- No secrets appear in UI messages or logs.
- Strict timeout confirmed in code.
- No filesystem writes / no test execution in health check path.
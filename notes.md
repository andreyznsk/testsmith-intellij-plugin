# Prompt Contract Notes

## Versioning strategy

- The prompt contract is versioned via `PromptContractV1.VERSION` and rendered with the version header.
- A future `PromptContractV2` should be introduced as a new record class and renderer with an updated template.
- Backward compatibility is maintained by keeping the v1 renderer and parser unchanged; callers can select the version via the factory.

## Parser strictness rules

- Only a single top-level JSON object is accepted; any leading or trailing text is rejected.
- Required fields are enforced and unknown fields are rejected.
- `testFramework` must match a known enum value.
- Markdown fences inside `javaSource` are rejected.

## Future logging locations (not implemented)

- Log the contract JSON (`PromptContractV1.toJson()`), rendered prompt string, and raw LLM response at the LLM client boundary.
- Suggested place: the `LlmClient` implementation in `io.testsmith.plugin.llm.ollama.OllamaLlmClient` around `generateTest` (before sending and before parsing).

# Task: Tool Window “Analyze” button — read existing JaCoCo XML and show current coverage

## Goal
Add a new **Analyze** button to the TestSmith Tool Window that reads the **existing JaCoCo XML report** (configured in Settings) and updates the UI with the **real current coverage** before starting the agent loop.

This must **not run tests** and must **not invoke Maven/Gradle**.

## Background / Problem
Currently the Tool Window shows `Coverage: 0.0 / 80.0` on first start even when the project already has tests and an existing JaCoCo report. This confuses users.

## Requirements

### UI
- Add **Analyze** button рядом с `Run / Stop / Settings` (same toolbar/row).
- Button enabled when agent is **IDLE** (safe default).
- On click: triggers coverage analysis without starting agent iteration.

### Behavior
- Read JaCoCo XML report from **Settings → JaCoCo XML path**.
- Parse the report and compute **current total coverage** (same metric currently shown in UI).
- Update Tool Window fields:
    - `Coverage: <current> / <target>` (target remains from settings, e.g. 80.0)
    - `Last Event: Coverage analyzed` (include percent/value if convenient)
    - `Current Class` can remain `--` (optional: set weakest class if already easy with existing selector)
- **Agent state must remain `IDLE`**
- **Iteration remains `0 / 0`**
- Must run in background (no UI freeze).

### Failure handling
If:
- JaCoCo XML path is not configured
- file does not exist
- XML parse fails

Then:
- Show user-visible error (modal dialog or IDE notification — match the style used for “Test connection” errors).
- Log full diagnostics (stacktrace) but user message should be short and actionable.
- Do not leak secrets.

### Constraints
- Reuse existing JaCoCo XML parsing implementation (do not re-invent parser).
- Do not modify any project files.
- Do not execute external processes.

## Implementation Guide (where to change)

### 1) Tool Window UI
Locate the Tool Window panel that renders the buttons `Run`, `Stop`, `Settings`.
Add `Analyze` button and wire it to controller call, e.g. `controller.analyzeCoverage()`.

Search hints:
- `ToolWindow`
- `Run` button creation
- `Settings` button creation
- `TestSmith` tool window title

### 2) Controller API
In the UI controller interface (or similar), add a method:
- `void analyzeCoverage();`

Update:
- real controller (e.g. `DefaultAgentController` or UI controller impl)
- stub controller (if exists): simulate immediate update or no-op

### 3) Background task
Use IntelliJ background execution:
- `Task.Backgroundable` (preferred) OR equivalent project-safe background executor
- When finished, update UI model on EDT:
    - `ApplicationManager.getApplication().invokeLater(...)`

### 4) Coverage service
Implement/extend a small service, e.g. `CoverageAnalyzeService`:
- input: `Project`, settings (path, target)
- output: `CoverageSnapshot` or `(coveredPercent, covered, missed)` depending on existing model

Use existing classes:
- existing JaCoCo reader/parser in `io.testsmith.plugin.coverage.*` (or whichever package exists)
- do not create a second parser

### 5) Update UI model
Find how Tool Window binds:
- `AgentProgress` / `AgentUiState` / observable model / state holder
  Update only:
- coverage current value
- last event message
  Keep:
- state = IDLE
- iteration = 0/0

## Logging
Add logs:
- “Coverage analyze requested”
- “JaCoCo XML path=<path>”
- “Coverage analyzed: <value>”
- On error: “Coverage analyze failed: <reason>” + exception

## Definition of Done
- [ ] Analyze button present in Tool Window
- [ ] Clicking Analyze updates coverage using existing JaCoCo XML (no test run)
- [ ] UI remains responsive (background task)
- [ ] IDLE state and iteration 0/0 preserved
- [ ] Missing/invalid path shows user-visible error + logs
- [ ] Works on a project that already has `target/site/jacoco/jacoco.xml` (Maven) or typical Gradle report path

## Notes
- Optional improvement: if there is already a “weakest class” selector using coverage data, set `Current Class` to that result after analysis. Otherwise keep `--`.
Ниже — задача для Codex в формате **MD-файла** для новой итерации.

Документ опирается на RFC v1.0
(архитектурные инварианты, Manual/Autonomous режимы, детерминизм, безопасность).

---

# Iteration 4.25 — Progress Visualization

## Goal

Добавить **визуализацию прогресса агента** в Tool Window IntelliJ Plugin:

* текущая итерация
* статус шага
* покрытие (до / после)
* прогресс к target coverage
* состояние (RUNNING / ANALYZING / FIXING / STOPPED / ERROR)

Визуализация должна быть:

* детерминированной
* не зависящей от LLM
* синхронизированной с AgentController
* безопасной (UI не управляет execution напрямую)

---

## Architectural Constraints (RFC-aligned)

1. UI не содержит бизнес-логики агента.
2. Источник истины — AgentController.
3. Coverage отображается только после FULL_SUITE_COVERAGE.
4. UI обновляется через immutable state snapshot.
5. Никаких side-effects из UI.
6. Invariant: TestSmith never modifies build configuration.

---

## Scope

### 1️⃣ Agent Progress Model

Ввести immutable модель состояния:

```java
public record AgentProgress(
    AgentUiState state,
    int iteration,
    int maxIterations,
    double currentCoverage,
    double targetCoverage,
    String currentClass,
    String lastMessage,
    long startedAt,
    Long lastUpdateAt
) {}
```

### Requirements

* iteration — 1-based
* coverage округлять до 1 decimal
* currentClass nullable
* timestamps для future telemetry

---

### 2️⃣ AgentController Extension

Добавить:

```java
AgentProgress getProgress();
void addProgressListener(ProgressListener listener);
void removeProgressListener(ProgressListener listener);
```

Listener:

```java
public interface ProgressListener {
    void onProgressChanged(AgentProgress progress);
}
```

---

### 3️⃣ Tool Window UI

Добавить блок Progress Panel:

#### Elements:

* 🔹 Status badge (цветной)
* 🔹 Progress bar (coverage → target)
* 🔹 Iteration counter (e.g. 3 / 20)
* 🔹 Current class label
* 🔹 Last event log (single-line)

---

## UI States Mapping

| AgentUiState    | Badge Color |
| --------------- | ----------- |
| IDLE            | Gray        |
| ANALYZING       | Blue        |
| GENERATING      | Yellow      |
| VERIFYING       | Cyan        |
| FIXING          | Orange      |
| COVERAGE_UPDATE | Purple      |
| STOPPED         | Gray        |
| ERROR           | Red         |
| COMPLETED       | Green       |

Цвета — минималистичные, без неона (см. Design Philosophy).

---

## Progress Bar Rules

```
progress = currentCoverage / targetCoverage
```

* capped at 100%
* если coverage > target → show 100%
* если targetCoverage == 0 → bar hidden

---

## Manual Mode Behavior

* Прогресс обновляется:

    * при генерации теста
    * при VERIFY_TARGET
    * при FULL_SUITE_COVERAGE
* При ожидании approve:

    * статус = WAITING_APPROVAL
    * progress bar frozen

---

## Autonomous Mode Behavior

* iteration increment только после FULL_SUITE_COVERAGE
* stagnation detection отображается:

    * "No coverage improvement"

---

## Logging Panel Integration

Progress panel не заменяет лог.
Он отображает **state abstraction**, а не raw logs.

---

## Failure Modes

| Scenario          | Expected Behavior |
| ----------------- | ----------------- |
| No JaCoCo XML     | Status = ERROR    |
| Test runner crash | Status = ERROR    |
| LLM timeout       | Status = ERROR    |
| Manual stop       | Status = STOPPED  |

---

## Non-Goals

* Исторический график покрытия
* Персистентность прогресса между перезапусками IDE
* Telemetry / metrics storage
* Real-time charting
* Multi-session aggregation

---

## Implementation Steps

1. Introduce AgentProgress model
2. Extend AgentController contract
3. Implement listener propagation
4. Add ProgressPanel UI
5. Wire controller → UI
6. Add deterministic unit tests for:

    * progress calculation
    * state transitions
7. Manual verification in:

    * Manual mode
    * Autonomous mode
    * Stop scenario
    * Error scenario

---

## Definition of Done

* UI отображает корректный progress
* Нет race conditions
* Нет UI freeze
* Progress отражает реальное состояние агента
* Все инварианты RFC соблюдены
* PR прошёл Codex-style review

---

## Open Questions

1. Нужно ли отображать elapsed time?
2. Нужно ли показывать last coverage delta (+1.3%)?
3. Нужно ли логировать iteration duration?

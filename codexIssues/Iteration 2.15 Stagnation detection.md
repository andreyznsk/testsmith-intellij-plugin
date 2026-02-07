# Codex Task: Iteration 2.15 — Stagnation Detection

## Context

Сейчас агент (или скоро будет) выбирает **"weakest class"** и крутит цикл генерации/фикса тестов.  
Нужно добавить **детектор стагнации**, чтобы Autonomous mode не зацикливался, а останавливался детерминированно и логировал причину.

> 📌 **PROJECT_RFC**

---

## Goal

Реализовать **stagnation detection** как отдельный компонент (`policy` / `guard`), интегрировать в agent loop/state, покрыть юнит-тестами.

---

## Functional Requirements

### Что считается "стагнацией"?

Считать стагнацией, если выполняется **хотя бы один** из следующих критериев:

### 1. No Coverage Progress

После `FULL_SUITE_COVERAGE` прогресса в покрытии не было **N раз подряд**.

- **Прогресс** = `deltaCoveredLines > 0` или `deltaMissedLines < 0` по `CoverageDiff` между "before" и "after".
- Учитываются только успешные запуски suite и корректно прочитанный JaCoCo XML.
  - В противном случае — это failure mode, а не стагнация.

### 2. Same Class Thrashing

Агент выбирает один и тот же `className` более **M раз подряд**, и **"missed metric"** для этого класса **не улучшается** (не уменьшается).

- Защита от ситуации: "weakest class" не меняется, но улучшений нет.
- Если есть метрика `missed lines` / `missed instructions` — использовать её.
- Иначе брать `missedLines` из snapshot по классу.

---

### Действие при стагнации

| Режим           | Действие                                                                 |
|----------------|--------------------------------------------------------------------------|
| **Autonomous** | Вернуть решение `STOP` с `reason = STAGNATION`, включить детали: критерий, счётчики, className, дельты. |
| **Manual**     | Не останавливать выполнение автоматически. Логировать или передавать флаг `WARN` во внешний слой (например, UI). |

> 📌 **PROJECT_RFC**

---

## Design Requirements (Contracts)

### Новые абстракции (предлагаемые)

#### `StagnationDecision`
java boolean isStagnating(); StagnationReason reason(); String details();

#### `StagnationReason`
- `NO_COVERAGE_PROGRESS`
- `SAME_CLASS_THRASHING`

#### `StagnationTuning`
- `maxNoProgressFullSuiteRuns` (N)
- `maxSameClassRepeats` (M)

#### `StagnationState` *(хранится в памяти на время сессии агента)*
- `noProgressCount`: int
- `lastSelectedClass`: String
- `sameClassRepeatCount`: int
- `lastMissedMetricByClass`: Map<String, Integer> *(или только для lastSelectedClass, если минимализм)*

#### `StagnationGuard`
java StagnationDecision evaluate( AgentIterationContext ctx, StagnationState state, CandidateClass candidate, Optional lastFullSuiteDiff )

> ⚠️ Компонент должен быть **pure / детерминированным**: без I/O, без PSI, без зависимостей от IDE.

---

## Integration Points

### После выбора кандидата ("weakest class")
- Обновить счётчики повторений.
- Проверить условие `SAME_CLASS_THRASHING` (на основе missed metric).

### После успешного `FULL_SUITE_COVERAGE`
- Вычислить `diff` (between before/after snapshots).
- Обновить `noProgressCount`.
- Проверить `NO_COVERAGE_PROGRESS`.

### В агент-цикле

java if (decision.isStagnating() && mode == AUTONOMOUS) { gracefulStop(reason); }

---

## Acceptance Criteria

✅ При отсутствии прогресса в покрытии в течение **N** подряд `FULL_SUITE_COVERAGE` — агент в **Autonomous** режиме останавливается с `reason = NO_COVERAGE_PROGRESS`.  
✅ При выборе одного и того же класса более **M** раз подряд без улучшения `missed`-метрики — агент останавливается с `reason = SAME_CLASS_THRASHING`.  
✅ В **Manual** режиме стагнация **не останавливает** выполнение, но результат доступен вызывающему коду (или логируется структурированно).  
✅ Юнит-тесты покрывают:
- Граничные значения N/M (ровно N — не стопаем, N+1 — стопаем; зафиксировать контракт явно).
- Сброс `noProgressCount` при наличии прогресса.
- Смена класса → сброс `repeatCount`.
- Улучшение `missed metric` → thrashing **не срабатывает**.

---

## Test Plan (JUnit)

Создать `DefaultStagnationGuardTest` (или аналог) с table-driven сценариями:

- `noCoverageProgress_stopsAfterNFullSuiteRuns()`
- `coverageProgress_resetsNoProgressCounter()`
- `sameClassRepeatsWithoutMissedImprovement_stopsAfterMRepeats()`
- `sameClassRepeatsWithImprovement_doesNotStop()`
- `classChange_resetsRepeatCounter()`

---

## Non-goals (Explicit)

❌ Не трогать build scripts / JaCoCo configuration / инфраструктуру тестов проекта.  
❌ Не добавлять UI (это задача Iteration 4). Сейчас достаточно: `decision object + stop reason`.

> 📌 **PROJECT_RFC**

---

## Deliverables

- **Код**:
  - `StagnationGuard`
  - `StagnationState`
  - `StagnationTuning`
  - `StagnationDecision` / `StagnationReason`
- **Интеграция** в agent runtime (в существующий loop/selection/execution)
- **Тесты**
- **Мини-документация** в Javadoc: что считается стагнацией и какой контракт по порогам
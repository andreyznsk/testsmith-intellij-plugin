Отлично. Ниже — **ФИНАЛЬНАЯ ИНСТРУКЦИЯ (v1.0)**.
Это **единый source of truth** для нашего нового чат-проекта.
Формат сделан так, чтобы **тебе было удобно вести проект**, а **мне — делать review и держать контекст**.

> 📌 **Скопируй этот документ целиком и вставь в новый чат.**
> После этого напиши: **“Iteration 0 — architecture validation”**.

### Invariant: TestSmith never modifies build configuration, test infrastructure, or production code automatically.

---

# 🧩 PROJECT RFC — Coverage-Driven Test Agent (IntelliJ IDEA Plugin)

## Version

**v1.0 (Codex-driven, agentic, production-grade)**

---

## 🎯 Project Goal

Создать **IntelliJ IDEA plugin (Java)**, реализующий **coverage-driven agent**, который:

* анализирует **JaCoCo coverage**
* итеративно **пишет и исправляет тесты**
* повышает покрытие до заданного уровня
* работает в **Manual** и **Autonomous** режимах
* **не придумывает** поля, методы и инфраструктуру
* **безопасен** для production-проектов
* масштабируем для **использования командами**

**Primary goal:** поднять покрытие реального проекта (~30%).
**Secondary goal:** подготовить плагин к использованию другими командами.

---

## 🛠 Product Identity & Philosophy

**Plugin name:** TestSmith

**Core metaphor:**  
TestSmith is a test blacksmith. Tests are not generated — they are forged from real code,
following the project’s existing patterns and infrastructure.

**Visual identity:**
- Dark IDE-first theme
- Engineering blueprint style
- Outline, minimal icons
- Core symbol: anvil + checkmark
- No fantasy imagery, no neon, no AI-themed visuals
- IDE platform 2025.X
  **Design principles:**
- Craft over automation
- Determinism over creativity
- Safety over speed
- Coverage is a signal, not a KPI
- gradle
- IDE IC 2025.1
  **User role:**
  TestSmith assists the engineer. The final decision always belongs to the human.
  --
## 🧠 Development Mode
### **MODE: CODEX**

* приоритет архитектуры над количеством кода
* review как у senior/staff engineer
* фокус на:

    * контрактах
    * инвариантах
    * failure modes
    * extensibility
* LLM используется для **reasoning, review и генерации**, но **не управляет execution**

---

## 🧱 Supported Build Systems

### Automatic detection (default)

| Tool   | Detection                           |
| Maven  | `pom.xml`                           |
| Gradle | `build.gradle` / `build.gradle.kts` |

### Manual override (user setting)

Пользователь может явно выбрать:

* Maven
* Gradle

Используется для mono-repo и кастомных пайплайнов.

---

## 🧪 JaCoCo Integration

### Automatic detection (default)

| Build tool | Default JaCoCo path                              |
| ---------- | ------------------------------------------------ |
| Maven      | `target/site/jacoco/jacoco.xml`                  |
| Gradle     | `build/reports/jacoco/test/jacocoTestReport.xml` |

### User configuration (mandatory)

* кастомный путь к JaCoCo XML
* exclusion rules (packages / classes)

---

## 🔁 Agent Execution Modes

### 🟢 Manual Mode (default)

* агент **предлагает** тест
* пользователь видит diff
* пользователь подтверждает / редактирует / отклоняет

### 🔴 Autonomous Mode

* агент работает без подтверждений
* остановка при:

    * достижении target coverage
    * max iterations
    * stagnation
    * manual stop
* все шаги логируются

---

## 🧠 Agent Loop (Formalized)

```text
while (coverage < target && not stopped):
    snapshot = readCoverage()
    targetClass = selectWeakest(snapshot)

    context = discoverContext(targetClass)
    prompt = buildPrompt(targetClass, context, snapshot)

    test = llm.generate(prompt)

    if mode == MANUAL:
        waitForApproval(test)

    writeTest(test)

    result = runTests()

    if result.failed:
        errorContext = extractErrors(result)
        llm.fix(test, errorContext)
    else:
        updateCoverage()
```

---

## 🎯 Target Selection Policy

* lowest coverage first
* highest missed lines
* exclude:

    * DTO
    * config
    * generated code
* prefer:

    * deterministic logic
    * public API
    * pure domain code

Политика **расширяемая**.

---

## 🧠 LLM Abstraction

### Unified Interface

```java
public interface LlmClient {
    LlmResponse generateTest(LlmRequest request);
}
```

### Local default (Iteration 3)

* **Ollama**
* Model: `qwen2.5-coder:7b`
* Deterministic settings:

    * temperature = 0.1
    * top_p = 0.9
    * repeat_penalty = 1.1

### Multi-LLM Support (Iteration 5)

* OpenAI API
* GigaChat
* provider switching via settings

---

## 🧪 Production-Grade Test Generation Rules (MANDATORY)

Агент **НЕ имеет права** генерировать тест, пока не выполнены все пункты.

### 1️⃣ Test framework detection

* определить: JUnit 3 / 4 / 5
* стратегия:

    1. `pom.xml` / `build.gradle`
    2. существующие тесты
    3. импорты и аннотации
* **запрещено** смешивать версии

### 2️⃣ Test pattern discovery

* анализ существующих тестов проекта
* выявить:

    * base test classes
    * test utilities (например `DatabaseUtil`)
    * способ работы с БД, миграциями, контейнерами
* **запрещено** изобретать новую инфраструктуру

### 3️⃣ Context assembly (no hallucinations)

* собрать **реальные**:

    * DTO
    * сервисы
    * поля
    * конструкторы
* **запрещено** придумывать поля/методы

### Pre-test checklist

```text
[ ] Test framework detected
[ ] Existing test patterns analyzed
[ ] Test infrastructure identified
[ ] DTOs and services collected
[ ] No imaginary fields or methods
```

Любое нарушение = **blocker**, даже если coverage вырос.

---

## 🧠 Project Context Indexing (RAG-style)

### Purpose

Собрать и кэшировать контекст проекта **до генерации тестов**, чтобы:

* уменьшить галлюцинации
* ускорить retrieval
* обеспечить консистентность

### Storage (recommended)

* **User cache**, а не репозиторий:

    * `~/.cache/coverage-agent/<project-hash>/`
* автоматически добавлять `.gitignore`, если используется локальная папка

### Cached content

* build tool + test framework
* discovered test patterns
* exemplar tests (3–10)
* symbols index (classes, DTOs, services)
* fingerprints для инвалидации

### Invalidation

* изменение build files
* изменение тестовой инфраструктуры
* смена ветки
* смена версии плагина

> 📌 Это **RAG без embeddings** для MVP.
> Semantic retrieval — позже (Iteration 6 / MCP).

---

## 🧩 IntelliJ Plugin Scope

* Tool Window
* Settings (agent, LLM, coverage)
* Actions: Run / Stop / Approve
* PSI API — анализ классов и сигнатур
* Process runner — `mvn test` / `gradle test`

---

## 🧭 Roadmap

### Iteration 0 — Architecture & Contracts

* agent model
* interfaces
* failure modes
* hard constraints

### Iteration 1 — JaCoCo Reader

* XML parsing
* coverage snapshot
* weakest class selection

### Iteration 2 — Test Execution
# Iteration 2.11 — Maven Test Runner (Two-Phase Execution)

## Goal
Provide a reliable and efficient test execution mechanism for Maven-based projects, optimized for iterative test generation.

## Key Principle
Test execution is two-phase. Newly generated tests must be verified in isolation before running the full test suite.

## Scope

### TestRunner Abstraction for Maven
- Support two execution modes:

#### `VERIFY_TARGET`
- Compile and execute only the generated test (class or method).
- Fast feedback loop.
- No coverage report required.

#### `FULL_SUITE_COVERAGE`
- Execute full test suite.
- Rely on existing project JaCoCo configuration.
- Generate/update JaCoCo XML report.

### Targeted Execution Support
Use Surefire/Failsafe with:

bash -Dtest=ClassName -Dtest=ClassName#method


### Result Capture & Classification
Capture and classify:
- Compilation failure
- Test failure
- Infrastructure failure

Additionally capture:
- `stdout` / `stderr`
- Execution duration

## Non-goals
- Automatic JaCoCo instrumentation or configuration.
- Smart scheduling of full-suite runs (handled by agent loop later).

---

# Iteration 2.12 — Gradle Test Runner (Two-Phase Execution)

## Goal
Provide a Gradle-compatible test execution runner following the same two-phase strategy.

## Key Principle
Gradle test execution must mirror Maven behavior **semantically**, even if underlying commands differ.

## Scope

### TestRunner Abstraction for Gradle
- Support two execution modes:

#### `VERIFY_TARGET`
- Execute only the generated test (class or method).
- Fast feedback loop.
- No coverage report required.

#### `FULL_SUITE_COVERAGE`
- Execute full test suite.
- Expect JaCoCo XML to be generated by project configuration.

### Targeted Execution Support
Use Gradle with:

bash --tests 'fully.qualified.TestClass' --tests 'fully.qualified.TestClass.method'


### Result Capture & Classification
Capture and classify:
- Compilation failure
- Test failure
- Infrastructure failure

Additionally capture:
- `stdout` / `stderr`
- Execution duration

## Non-goals
- Injecting JaCoCo plugins or modifying Gradle build scripts.
- Advanced Gradle task graph inspection or custom test task discovery.

---

## Cross-Iteration Invariants (2.11 & 2.12)
- Test execution is never fully automatic by default.
- Targeted verification always precedes full-suite execution.
- Full-suite execution is the **source of truth** for coverage snapshots.
- Absence of JaCoCo XML in `FULL_SUITE_COVERAGE` mode is a **hard error**.
- Runners must be deterministic and side-effect free.

---

## Deferred Decisions (Handled in Later Iterations)
- Policies deciding when to run full-suite coverage.
- Optimization strategies for expensive test suites.
- Parallel or incremental execution strategies.

* failure extraction

### Iteration 3 — Single LLM (Ollama)

* strict prompt contract
* structured output
* deterministic generation

### Iteration 4 — IntelliJ Plugin UI

* settings
* tool window
* manual approval flow

### Iteration 5 — Multi-LLM Support

* OpenAI
* GigaChat
* provider switching

### Iteration 6 — MCP (Optional / Educational)

* MCP tools for **decision support only**
* no file writes
* no test execution
* compare:

    * rule-based decision
    * LLM
    * LLM + MCP

---

## 🧑‍💻 Workflow & Collaboration Rules

### Iterations

* **Каждая итерация = отдельный чат**
* **Каждая итерация = отдельная git-ветка**

  ```
  iteration-X-short-description
  ```

### Feature decomposition

* сложные итерации декомпозируются на features

### Git & PR Flow

1. Ветка под итерацию
2. Реализация
3. Pull Request
4. PR приносится в чат
5. **Codex-style review** (архитектура, контракты, риски)
6. Правки
7. Повторный review
8. **APPROVE**

    * фиксация решений
    * обновление этой инструкции при необходимости

---

## ❗ Guiding Principles

* Coverage — **сигнал**, а не цель
* Безопасно по умолчанию
* Никаких галлюцинаций
* Архитектура > скорость
* Тесты следуют проекту, а не фантазии модели

---

## 🚀 Entry Point (каждый новый чат)

```
Iteration X — <short title>
Goal:
Scope:
Open questions:
```

---

### 🔚 End of RFC v1.0
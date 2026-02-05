Codex Task: Iteration 2.14 — Failure extraction (Maven + Gradle)
Goal

Сделать детерминированное извлечение причины падения из результата запуска тестов (stdout/stderr/exitCode/timedOut), чтобы агент мог:

классифицировать фейл (compilation / test failure / infra)

собрать короткий “root cause” (1–3 строки)

вытащить контекст для LLM-fix (ключевые фрагменты логов, список упавших тестов/методов, сообщения компилятора)

работать одинаково для Maven и Gradle.

Constraints (must)

Никаких изменений build.gradle/pom.xml, тестовой инфраструктуры или production-кода.

Только парсинг/эвристики по логам + структурирование результата.

Парсер должен быть side-effect free, детерминированный, с покрытием тестами.

Не завязываться на локаль/язык сообщений (но поддержать типовые английские строки).

Proposed API (add new package/module)

Создать пакет: io.testsmith.plugin.testrunner.failure

Models
public enum FailureKind {
NONE,
TIMEOUT,
COMPILATION,
TEST_FAILURE,
INFRASTRUCTURE
}

public record FailureReport(
FailureKind kind,
String summary,            // коротко: "Compilation error: ...", "Test failed: ..."
String rootCause,          // 1–3 строки, максимально полезно для человека/LLM
List<String> failingTests, // FQCN или "FQCN#method" когда возможно
List<String> evidence,     // 5–30 строк: отфильтрованные ключевые фрагменты
Map<String, String> hints  // optional: "mavenGoal", "gradleTask", "reportPath", etc.
) {
public static FailureReport none() { ... }
}

Extractor contract
public interface FailureExtractor {
FailureReport extract(String stdout, String stderr, int exitCode, boolean timedOut);
}

Implementation

DefaultFailureExtractor implements FailureExtractor

Внутри: последовательность детекторов (order matters):

timeout

compilation (maven/gradle)

test failure (maven/gradle)

infra (generic build failure, daemon crash, missing java, missing gradle wrapper, etc.)

fallback: INFRASTRUCTURE с best-effort rootCause

Detection rules (minimum)
Timeout

timedOut == true ⇒ FailureKind.TIMEOUT

evidence: последние N строк stderr/stdout (например 30)

Maven — compilation

Триггеры (any):

COMPILATION ERROR

Failed to execute goal .*maven-compiler-plugin

Compilation failure
RootCause:

первая “реальная” ошибка компилятора (например строки вида ...: error: ...)
FailingTests: пусто
Evidence:

блок “COMPILATION ERROR” (ограничить разумно: ~50 строк max)

Maven — test failure

Триггеры (any):

There are test failures

Failed tests:

Surefire summary Tests run: .* Failures: .* Errors: .*
FailingTests:

распарсить строки после Failed tests: и/или типовые <<< FAILURE! / <<< ERROR!

если видим com.a.BTest.testX → нормализовать к com.a.BTest#testX (best effort)
Evidence:

список failed tests

первые N строк stacktrace для каждого (или общий блок surefire)

Gradle — compilation

Триггеры (any):

Compilation failed

error: (рядом с путём к файлу и номером строки — best effort)

Execution failed for task ':compileTestJava' / :compileJava
Evidence:

блок вокруг Compilation failed или task-failure (ограничить)

Gradle — test failure

Триггеры (any):

There were failing tests

> Task :test FAILED

See the report at: (с путём)
FailingTests:

если в логах есть FAILED строки (gradle verbose) — best effort

иначе пусто, но hints.reportPath заполнить из See the report at:
Evidence:

task failure + report path + ключевой фрагмент

Infra (generic)

Триггеры (any):

Could not resolve / Could not determine the dependencies

No matching toolchains found / Cannot find a Java installation

Gradle build daemon disappeared unexpectedly

Could not find or load main class

Permission denied
RootCause:

первая строка из блока * What went wrong: (Gradle) или top error (Maven [ERROR])
Evidence:

* What went wrong: section (Gradle) или [ERROR] section (Maven)

Evidence slicing rules

Сделать утилиту LogSlicer:

вход: raw stdout/stderr

выход: List<String> evidence lines

правила:

нормализовать \r\n

убрать пустые хвосты

ограничить общий объём (например 200 строк max)

приоритет: “ключевые блоки” → иначе последние 60 строк stderr+stdout

Wiring (integration points)

В TestRunner (или где формируется TestRunResult) при failed == true:

вызвать FailureExtractor.extract(...)

положить FailureReport в результат выполнения (добавить поле, не ломая API — если нужно, сделать новый record/DTO или расширение)

Важное: extraction не должен зависеть от типа билда, но может использовать эвристику по строкам.

Tests (required)

Добавить unit-тесты на extractor (табличные):

maven_compilation_error.txt

maven_test_failure.txt

gradle_compilation_error.txt

gradle_test_failure.txt

gradle_infra_toolchain_missing.txt

timeout_case.txt (stdout/stderr любые)

Для каждого:

assert FailureKind

assert rootCause not blank

assert evidence not empty and within limits

assert failingTests matches expected when possible

assert hints.reportPath для Gradle test failure (если есть)

Формат: хранить фикстуры в src/test/resources/... и проверять стабильность.

Acceptance Criteria

✅ При падении mvn test -Dtest=... extractor возвращает корректный FailureKind и человекочитаемый rootCause.

✅ При падении gradle test --tests ... extractor возвращает корректный FailureKind, и (если есть) reportPath.

✅ Evidence ограничен по размеру и стабилен (без флакания).

✅ 6+ unit тестов на реальные/псевдо-реальные логи.

✅ Нет изменений билд-файлов и тестовой инфраструктуры.
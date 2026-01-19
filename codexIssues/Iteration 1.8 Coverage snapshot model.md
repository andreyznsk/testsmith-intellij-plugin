Задание для Codex

Контекст:
Мы делаем IntelliJ IDEA plugin TestSmith (Java). На Iteration 1.8 нужно реализовать доменную модель coverage snapshot, которая будет использоваться агентом для выбора целей, диффов и детекта стагнации. Модель должна быть immutable, diff-friendly, и не содержать JaCoCo-специфики.

1) Требования (обязательные)

1.1 Доменные типы:
Реализуй следующие классы/records в пакете (пример):
com.testsmyth.agent.coverage.model (можно выбрать другое, но единообразно).

CoverageSnapshot

поля: Instant timestamp, CoverageSummary summary, Map<ClassId, ClassCoverage> classes

immutable (defensive copies, Map.copyOf)

публичные getters (или record, если ок)

CoverageSummary

поля: int totalLines, int coveredLines, int missedLines

метод double coverageRatio():

если totalLines == 0 → вернуть 1.0

ClassCoverage

поля: ClassId classId, PackageName packageName,
int totalLines, int coveredLines, int missedLines,
Set<Integer> missedLineNumbers

immutable (Set.copyOf)

ClassId (value object)

хранит fully qualified name (String)

валидирует non-null, non-blank

PackageName (value object)

non-null, non-blank

1.2 Инварианты (строго):

Для CoverageSummary:

totalLines >= 0, coveredLines >= 0, missedLines >= 0

coveredLines + missedLines == totalLines

Для ClassCoverage:

те же ограничения по суммам: coveredLines + missedLines == totalLines

missedLineNumbers.size() == missedLines (если totalLines > 0)

missedLineNumbers содержит только > 0

Для CoverageSnapshot:

timestamp != null, summary != null, classes != null

classes не содержит null key/value

Опционально, но желательно: проверка, что summary согласован с суммой классов (можно сделать как validateConsistency() и вызывать в конструкторе или оставить отдельным методом — решение за тобой, но объясни tradeoff в комментарии).

1.3 Diff модель:
Добавь класс CoverageDiff (в пакете ...coverage.diff или рядом):

CoverageSnapshot before, CoverageSnapshot after

методы:

int deltaCoveredLines()

int deltaMissedLines()

boolean hasProgress() → deltaCoveredLines() > 0

Никаких чтений JaCoCo/файлов внутри diff.

1.4 Без JaCoCo в домене:
Никаких org.jacoco.* импортов и типов в этих пакетах.

2) Unit tests (обязательно)

Используй JUnit 5.

Покрыть тестами:

CoverageSummary.coverageRatio() для totalLines=0 и для нормального случая.

Инварианты сумм (валидный и невалидный cases) для CoverageSummary и ClassCoverage.

Defensive copy:

передай mutable Map/Set в snapshot/classCoverage, затем измени исходную коллекцию → объект не должен поменяться.

CoverageDiff:

delta на росте покрытия

hasProgress() false при 0 или отрицательном росте

3) Coding constraints (важно)

Java 17

Минимум зависимостей (JUnit 5 для тестов)

Кинь осмысленные сообщения в исключения (IllegalArgumentException / NullPointerException — ок).

Код должен быть “library-grade”: чистые API, понятные названия, no side effects.

Никаких TODO и заглушек.

4) Что вернуть в ответе

Список файлов с путями.

Полный код файлов.

Коротко (5–10 строк) объясни ключевые решения:

record vs class

где и как валидируются инварианты

как обеспечена immutable семантика
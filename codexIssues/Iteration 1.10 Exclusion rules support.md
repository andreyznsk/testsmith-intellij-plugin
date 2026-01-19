Iteration 1, Feature 10 — **Exclusion rules support (packages/classes)**
Задание для **Codex** (реализация + тесты)

## Goal

Добавить поддержку **exclusion rules** (исключения по пакетам и классам), чтобы агент **не выбирал** и **не учитывал** в статистике покрытия те классы, которые пользователь исключил (DTO, config, generated, etc).

## Scope

1. **Модель конфигурации исключений**

* Ввести `ExclusionRules` (immutable value object), содержащий:

    * `List<String> excludedPackages` — исключаемые пакеты (prefix).
    * `List<String> excludedClasses` — исключаемые классы (FQCN).
* Нормализовать ввод: trim, убрать пустые строки, удалить дубликаты (с сохранением порядка опционально).

2. **Matcher / predicate**

* Реализовать `ExclusionMatcher` (или `ExclusionRules#matches(String fqcn)`), который отвечает: “класс исключён?”
* Правила матчинга (MVP, без regex):

    * `excludedClasses`: **точное совпадение** FQCN (`com.acme.Foo`).
    * `excludedPackages`: **prefix match** по package-name:

        * если rule = `com.acme` → исключает `com.acme.*` и `com.acme.sub.*`
        * допускается формат `com.acme.*` как синоним `com.acme` (нормализовать, убрав `.*` на конце)
* Входной формат имен для матчера — **FQCN** (`com.example.MyClass`).

3. **Интеграция в weakest-class selection**

* В `WeakestClassSelectionPolicy` (или стратегии выбора) добавить фильтрацию:

    * перед сортировкой/выбором удалить все `ClassCoverage` попавшие под `ExclusionMatcher`.
* Если после фильтрации список пуст:

    * вернуть `SelectionResult.none(reason=ALL_EXCLUDED_OR_EMPTY)` (или `Optional.empty()`), в зависимости от текущего контракта Iteration 1.

4. **Интеграция в coverage snapshot assembly**

* На уровне `CoverageSnapshot` (или builder/assembler) обеспечить возможность хранить “сырой” список и “effective” список:

    * MVP: можно просто фильтровать **только на этапе selection** (быстрее внедрить).
    * Но обязательно: публичный API должен позволить агенту объяснить, что класс пропущен из-за rules (логика/причина пригодится для UI позже).

## Non-goals (явно не делаем)

* Regex/Glob полноценные.
* Исключения по методам/аннотациям.
* Авто-детект DTO/config/generated (это будет политикой/пресетом позже).
* UI/Settings экраны (Iteration 4). Сейчас — только программная модель + возможность передать rules.

## Contracts / API changes

* Добавить в selection policy метод/конструктор, принимающий `ExclusionRules` или `Predicate<String fqcn>`:

    * Вариант A (предпочтительно):
      `WeakestClassSelectionPolicy.select(snapshot, ExclusionRules rules)`
    * Вариант B (более расширяемо):
      `select(snapshot, Predicate<String> isExcluded)`
* Важно: не ломать текущие контракты Iteration 1, если они уже закоммичены — допустимо добавить перегрузку/новую реализацию стратегии.

## Edge cases / Failure modes

* Пустые rules → ничего не исключаем.
* Rule `com.acme.*` → нормализовать до `com.acme`.
* Rule с пробелами → trim.
* Некорректные строки (например, `..`, пустые) → игнорировать + (опционально) вернуть warning через результат сборки rules. MVP можно просто silently drop.

## Tests (обязательно)

1. `ExclusionMatcherTest`

* exact class exclusion: `excludedClasses=[com.a.B]` matches `com.a.B` true, `com.a.C` false.
* package prefix exclusion: `excludedPackages=[com.a]` matches `com.a.B` true, `com.ab.C` false.
* `com.a.*` behaves like `com.a`.
* trimming + empty lines ignored.

2. `WeakestClassSelectionPolicyTest` (или аналог)

* Снапшот с 3 классами, один самый “weak”, но он excluded → выбирается следующий.
* Все excluded → результат `none` (или `Optional.empty()`), причина корректная.

## Acceptance criteria

* Эксклюзии применяются **детерминированно** и **без regex**.
* `Weakest class selection` никогда не возвращает исключённый класс.
* Тесты покрывают matcher и интеграцию в selection.
* Не добавляется “магия” или новые зависимости; код в стиле “production-grade”, immutable модели.

## Suggested file layout (пример)

* `agent/coverage/domain/ExclusionRules.java`
* `agent/coverage/domain/ExclusionMatcher.java` (если нужен отдельно)
* `agent/coverage/selection/WeakestClassSelectionPolicy.java` (обновить)
* `.../tests/...`

Если в текущей кодовой базе уже есть `CoverageSnapshot` / `SelectionPolicy` / `ClassCoverage` — **использовать их**, не придумывать новые сущности-дубликаты.

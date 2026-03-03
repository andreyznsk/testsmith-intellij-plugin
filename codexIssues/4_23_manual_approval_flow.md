# Iteration 4.23 — Manual Approval Flow

## Goal

Реализовать **Manual Approval Flow** в IntelliJ plugin TestSmith.

В Manual Mode (default):

* агент **НЕ имеет права записывать файлы автоматически**
* пользователь видит **diff**
* пользователь может:

    * Approve
    * Edit
    * Reject

Manual approval является обязательным safety-барьером перед записью тестов.

---

## RFC Alignment

Согласно RFC v1.0:

> Manual Mode (default): агент предлагает тест, пользователь подтверждает / редактирует / отклоняет

Инвариант:

> TestSmith never modifies build configuration, test infrastructure, or production code automatically.

---

# Scope

## 1️⃣ UI State

Добавить новое состояние:

```java
WAITING_FOR_APPROVAL
```

UI должен отображать:

* target class
* proposed test class
* confidence (если есть)
* requiresInfrastructure flag
* unified diff preview
* кнопки:

    * Approve
    * Edit
    * Reject

---

## 2️⃣ Approval Abstraction

Добавить слой абстракции, чтобы агент не зависел от UI.

### ApprovalRequest

```java
public final class ApprovalRequest {
    UUID runId;
    String targetClassFqn;
    String testClassFqn;
    String diffText;
    Map<Path, String> proposedFiles; // full file content
    Double confidence;
    boolean requiresInfrastructure;
}
```

### ApprovalDecision

```java
public enum DecisionType {
    APPROVE,
    REJECT
}

public final class ApprovalDecision {
    DecisionType type;
    Map<Path, String> editedFiles; // optional
}
```

### ApprovalGateway

```java
public interface ApprovalGateway {
    CompletableFuture<ApprovalDecision> requestApproval(ApprovalRequest request);
    void cancel(UUID runId);
}
```

UI реализует ApprovalGateway.

---

## 3️⃣ Agent Integration

При генерации теста:

```java
if (mode == MANUAL) {
    transitionTo(WAITING_FOR_APPROVAL);

    ApprovalDecision decision =
        approvalGateway.requestApproval(request).get();

    if (decision == APPROVE) {
        writeTestFiles(decision.editedFiles or original);
    } else {
        log("User rejected test");
        return;
    }
}
```

---

## 4️⃣ Diff Preview

Требования:

* unified diff
* read-only viewer
* поддержка:

    * new file
    * modify existing file

Минимально допустимая реализация:

* plain text diff panel

Желательно:

* IntelliJ Diff API

---

## 5️⃣ Edit Flow

Минимальный MVP:

* при нажатии Edit:

    * открыть proposed content в editable modal
* после редактирования:

    * сохранить в pending state
    * Approve применяет изменённый контент

---

## 6️⃣ Safe Write

Запись файлов должна:

* выполняться через IntelliJ WriteCommandAction
* писать только test source root
* никогда не изменять:

    * build.gradle
    * pom.xml
    * production code
    * test infrastructure

Нарушение = blocker.

---

# Edge Cases

## Stop pressed during approval

* approval future отменяется
* файлы НЕ записываются
* состояние → STOPPED

## New run started

* предыдущий approval invalidated
* никаких stale writes

## File changed on disk

* detect mismatch
* abort write
* показать ошибку

---

# Definition of Done

* [ ] В Manual mode тесты не пишутся без Approve
* [ ] Diff отображается
* [ ] Approve записывает файл
* [ ] Reject ничего не записывает
* [ ] Edit позволяет изменить контент до записи
* [ ] Stop отменяет pending approval
* [ ] Новый run отменяет предыдущий approval
* [ ] Нет записи вне test source root
* [ ] Нет изменений build/config/production code

---

# Failure Modes To Consider

* Deadlock при ожидании future в UI thread
* Race condition runId mismatch
* Memory leak pending approval
* Write action outside EDT
* Null editedFiles handling
* Multiple approvals for same run

---

# Non-Goals

* Autonomous mode auto-approval
* Partial hunk approval
* Multi-file patch merge strategy
* Conflict auto-resolution

---

# Architectural Constraints

* UI не знает о LLM
* Agent не знает о Swing
* ApprovalGateway — единственная точка контакта
* Асинхронность обязательна
* Нет блокировки UI thread

---

# Expected Outcome

После реализации:

Manual Mode становится production-safe.

Пользователь полностью контролирует запись тестов.

Plugin соответствует философии:

> Craft over automation
> Safety over speed



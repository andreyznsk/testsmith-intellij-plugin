package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredAction;
import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.llm.api.TestFramework;
import io.testsmith.plugin.testrunner.model.TestExecutionPhase;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;
import io.testsmith.plugin.testrunner.model.TestExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedFixTestLoopTest {

    @Test
    void succeedsAfterOneRepairAttempt() {
        StructuredTest generated = generatedTest();
        StructuredTest repaired = repairedTest();

        List<String> logs = new ArrayList<>();
        BoundedFixTestLoop loop = new BoundedFixTestLoop(
                (originalTest, failure, context) -> RepairResult.repaired(repaired, "ok"),
                new RepairFailureClassifier(),
                new RepairPolicy(),
                RepairAttemptLogger.toConsumer(logs::add),
                RepairDiffRenderer.simpleCodeDiff(),
                RepairApprovalGate.alwaysApprove()
        );

        AtomicInteger calls = new AtomicInteger();
        VerifyTargetExecutor verifier = test -> {
            if (calls.getAndIncrement() == 0) {
                return compilationFailure("cannot find symbol\nimport com.example.Missing");
            }
            return success();
        };

        RepairContext context = baseContext(generated, RepairMode.AUTONOMOUS);
        FixLoopResult result = loop.execute(generated, verifier, context);

        assertEquals(FixLoopStatus.SUCCESS, result.status());
        assertEquals(1, result.attemptsUsed());
        assertEquals(repaired.code(), result.finalTest().code());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("[RepairAttempt #1]"));
        assertTrue(logs.get(0).contains("Outcome: SUCCESS"));
    }

    @Test
    void abortsWhenFailureTypeIsInfrastructure() {
        StructuredTest generated = generatedTest();
        BoundedFixTestLoop loop = new BoundedFixTestLoop((originalTest, failure, context) -> {
            fail("repair agent must not be invoked");
            return RepairResult.rejected("should not happen");
        });

        VerifyTargetExecutor verifier = test -> infrastructureFailure("JaCoCo XML not found at build/reports/jacoco.xml");

        FixLoopResult result = loop.execute(generated, verifier, baseContext(generated, RepairMode.AUTONOMOUS));

        assertEquals(FixLoopStatus.ABORTED_DISALLOWED_FAILURE, result.status());
        assertEquals(0, result.attemptsUsed());
    }

    @Test
    void stopsAtMaxRepairAttempts() {
        StructuredTest generated = generatedTest();
        StructuredTest repaired = repairedTest();

        BoundedFixTestLoop loop = new BoundedFixTestLoop((originalTest, failure, context) -> RepairResult.repaired(repaired, "retry"));
        VerifyTargetExecutor verifier = test -> compilationFailure("cannot find symbol");

        FixLoopResult result = loop.execute(generated, verifier, baseContext(generated, RepairMode.AUTONOMOUS));

        assertEquals(FixLoopStatus.ABORTED_MAX_RETRIES, result.status());
        assertEquals(RepairPolicy.MAX_REPAIR_ATTEMPTS, result.attemptsUsed());
    }

    @Test
    void manualModeRequiresApproval() {
        StructuredTest generated = generatedTest();
        StructuredTest repaired = repairedTest();

        BoundedFixTestLoop loop = new BoundedFixTestLoop(
                (originalTest, failure, context) -> RepairResult.repaired(repaired, "ok"),
                new RepairFailureClassifier(),
                new RepairPolicy(),
                RepairAttemptLogger.noop(),
                RepairDiffRenderer.simpleCodeDiff(),
                (original, candidate, diff, attempt) -> false
        );

        VerifyTargetExecutor verifier = test -> compilationFailure("cannot find symbol\nimport com.example.Missing");

        FixLoopResult result = loop.execute(generated, verifier, baseContext(generated, RepairMode.MANUAL));

        assertEquals(FixLoopStatus.ABORTED_MANUAL_REJECTION, result.status());
        assertEquals(1, result.attemptsUsed());
        assertEquals(generated.code(), result.finalTest().code());
    }

    private static RepairContext baseContext(StructuredTest generated, RepairMode mode) {
        return new RepairContext(
                "com.example.Service",
                "package com.example; public class Service {}",
                List.of(),
                TestFramework.JUNIT5,
                "",
                List.of(),
                Duration.ofSeconds(5),
                Map.of("traceId", "iter3.20"),
                mode,
                1,
                generated
        );
    }

    private static StructuredTest generatedTest() {
        return new StructuredTest(
                "1.0",
                StructuredAction.GENERATE_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of("org.junit.jupiter.api.Test"),
                "package com.example;\npublic class ServiceTest {}",
                List.of(),
                false,
                0.8,
                Map.of()
        );
    }

    private static StructuredTest repairedTest() {
        return new StructuredTest(
                "1.1",
                StructuredAction.REPAIR_TEST,
                "com.example.Service",
                "ServiceTest",
                List.of("org.junit.jupiter.api.Test"),
                "package com.example;\npublic class ServiceTest {\n  // repaired\n}",
                List.of(),
                false,
                0.7,
                Map.of()
        );
    }

    private static TestExecutionResult success() {
        return new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.SUCCESS,
                null,
                null,
                null,
                null,
                "",
                "",
                Duration.ofMillis(20)
        );
    }

    private static TestExecutionResult compilationFailure(String message) {
        return new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.COMPILATION_FAILED,
                null,
                null,
                message,
                null,
                "",
                message,
                Duration.ofMillis(30)
        );
    }

    private static TestExecutionResult infrastructureFailure(String message) {
        return new TestExecutionResult(
                TestExecutionPhase.VERIFY_TARGET,
                TestExecutionStatus.INFRASTRUCTURE_ERROR,
                null,
                null,
                message,
                null,
                "",
                message,
                Duration.ofMillis(30)
        );
    }
}

package io.testsmith.plugin.agent.repair;

import io.testsmith.plugin.agent.generation.structured.StructuredTest;
import io.testsmith.plugin.testrunner.model.TestExecutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class BoundedFixTestLoop {
    private final TestRepairAgent repairAgent;
    private final RepairFailureClassifier failureClassifier;
    private final RepairPolicy repairPolicy;
    private final RepairAttemptLogger attemptLogger;
    private final RepairDiffRenderer diffRenderer;
    private final RepairApprovalGate approvalGate;

    public BoundedFixTestLoop(TestRepairAgent repairAgent) {
        this(
                repairAgent,
                new RepairFailureClassifier(),
                new RepairPolicy(),
                RepairAttemptLogger.noop(),
                RepairDiffRenderer.simpleCodeDiff(),
                RepairApprovalGate.alwaysApprove()
        );
    }

    public BoundedFixTestLoop(
            TestRepairAgent repairAgent,
            RepairFailureClassifier failureClassifier,
            RepairPolicy repairPolicy,
            RepairAttemptLogger attemptLogger,
            RepairDiffRenderer diffRenderer,
            RepairApprovalGate approvalGate
    ) {
        this.repairAgent = Objects.requireNonNull(repairAgent, "repairAgent must not be null");
        this.failureClassifier = Objects.requireNonNull(failureClassifier, "failureClassifier must not be null");
        this.repairPolicy = Objects.requireNonNull(repairPolicy, "repairPolicy must not be null");
        this.attemptLogger = Objects.requireNonNull(attemptLogger, "attemptLogger must not be null");
        this.diffRenderer = Objects.requireNonNull(diffRenderer, "diffRenderer must not be null");
        this.approvalGate = Objects.requireNonNull(approvalGate, "approvalGate must not be null");
    }

    public FixLoopResult execute(
            StructuredTest generatedTest,
            VerifyTargetExecutor verifyTargetExecutor,
            RepairContext baseContext
    ) {
        Objects.requireNonNull(generatedTest, "generatedTest must not be null");
        Objects.requireNonNull(verifyTargetExecutor, "verifyTargetExecutor must not be null");
        Objects.requireNonNull(baseContext, "baseContext must not be null");

        StructuredTest currentTest = generatedTest;
        TestExecutionResult verification = verifyTargetExecutor.verifyTarget(currentTest);
        if (verification.isSuccess()) {
            return new FixLoopResult(FixLoopStatus.SUCCESS, currentTest, verification, 0, "initial verification passed");
        }

        int attempts = 0;
        while (attempts < RepairPolicy.MAX_REPAIR_ATTEMPTS) {
            RepairFailureType initialFailureType = failureClassifier.classify(verification);
            if (!repairPolicy.repairAllowed(initialFailureType)) {
                return new FixLoopResult(
                        FixLoopStatus.ABORTED_DISALLOWED_FAILURE,
                        currentTest,
                        verification,
                        attempts,
                        "repair is not allowed for failure type: " + initialFailureType
                );
            }

            attempts++;
            Instant start = Instant.now();
            RepairContext attemptContext = baseContext.withAttemptNumber(attempts, currentTest);

            RepairResult repair = repairAgent.attemptRepair(currentTest, verification, attemptContext);
            if (repair.outcome() == RepairOutcome.HARD_ABORT) {
                long durationMillis = durationMillis(start);
                attemptLogger.log(new RepairAttemptLog(
                        attempts,
                        initialFailureType,
                        null,
                        generatedTest.targetClass(),
                        "HARD_ABORT",
                        durationMillis
                ));
                return new FixLoopResult(
                        FixLoopStatus.ABORTED_HARD_ABORT,
                        currentTest,
                        verification,
                        attempts,
                        repair.message()
                );
            }
            if (repair.outcome() == RepairOutcome.REJECTED) {
                long durationMillis = durationMillis(start);
                attemptLogger.log(new RepairAttemptLog(
                        attempts,
                        initialFailureType,
                        null,
                        generatedTest.targetClass(),
                        "REJECTED",
                        durationMillis
                ));
                return new FixLoopResult(
                        FixLoopStatus.ABORTED_REPAIR_REJECTED,
                        currentTest,
                        verification,
                        attempts,
                        repair.message()
                );
            }

            StructuredTest repaired = repair.repairedTest();
            if (attemptContext.repairMode() == RepairMode.MANUAL) {
                String diff = diffRenderer.render(currentTest, repaired);
                boolean approved = approvalGate.approve(currentTest, repaired, diff, attempts);
                if (!approved) {
                    long durationMillis = durationMillis(start);
                    attemptLogger.log(new RepairAttemptLog(
                            attempts,
                            initialFailureType,
                            null,
                            generatedTest.targetClass(),
                            "MANUAL_REJECTED",
                            durationMillis
                    ));
                    return new FixLoopResult(
                            FixLoopStatus.ABORTED_MANUAL_REJECTION,
                            currentTest,
                            verification,
                            attempts,
                            "manual approval rejected repaired test"
                    );
                }
            }

            currentTest = repaired;
            verification = verifyTargetExecutor.verifyTarget(currentTest);

            String outcome = verification.isSuccess() ? "SUCCESS" : "FAILED";
            RepairFailureType postRepairFailureType = verification.isSuccess()
                    ? null
                    : failureClassifier.classify(verification);
            attemptLogger.log(new RepairAttemptLog(
                    attempts,
                    initialFailureType,
                    postRepairFailureType,
                    generatedTest.targetClass(),
                    outcome,
                    durationMillis(start)
            ));

            if (verification.isSuccess()) {
                return new FixLoopResult(FixLoopStatus.SUCCESS, currentTest, verification, attempts, "repair succeeded");
            }
        }

        return new FixLoopResult(
                FixLoopStatus.ABORTED_MAX_RETRIES,
                currentTest,
                verification,
                attempts,
                "reached max repair attempts: " + RepairPolicy.MAX_REPAIR_ATTEMPTS
        );
    }

    private static long durationMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}

package io.testsmith.plugin.agent.repair;

public enum FixLoopStatus {
    SUCCESS,
    ABORTED_DISALLOWED_FAILURE,
    ABORTED_REPAIR_REJECTED,
    ABORTED_HARD_ABORT,
    ABORTED_MANUAL_REJECTION,
    ABORTED_MAX_RETRIES
}

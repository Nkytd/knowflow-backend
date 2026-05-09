package com.knowflow.parser.support;

import java.util.Set;

public final class ParseTaskStateMachine {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private static final Set<String> RETRYABLE_STATUSES = Set.of(FAILED);
    private static final Set<String> TERMINAL_STATUSES = Set.of(SUCCESS, FAILED);

    private ParseTaskStateMachine() {
    }

    public static boolean canStart(String status) {
        return canTransition(status, PROCESSING);
    }

    public static boolean canComplete(String status) {
        return PROCESSING.equals(status);
    }

    public static boolean canRetry(String status) {
        return canTransition(status, PENDING);
    }

    public static boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    public static boolean canRecover(String status) {
        return PROCESSING.equals(status);
    }

    public static boolean canTransition(String fromStatus, String toStatus) {
        if (PENDING.equals(fromStatus)) {
            return PROCESSING.equals(toStatus);
        }
        if (PROCESSING.equals(fromStatus)) {
            return SUCCESS.equals(toStatus) || FAILED.equals(toStatus) || PENDING.equals(toStatus);
        }
        if (RETRYABLE_STATUSES.contains(fromStatus)) {
            return PENDING.equals(toStatus);
        }
        return false;
    }

    public static String describeTransition(String fromStatus, String toStatus) {
        return "%s -> %s".formatted(fromStatus == null ? "NULL" : fromStatus, toStatus == null ? "NULL" : toStatus);
    }
}

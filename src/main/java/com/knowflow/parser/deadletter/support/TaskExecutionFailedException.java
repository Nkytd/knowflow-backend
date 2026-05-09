package com.knowflow.parser.deadletter.support;

public class TaskExecutionFailedException extends RuntimeException {

    private final Long taskId;

    public TaskExecutionFailedException(Long taskId, String message) {
        super(message);
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}

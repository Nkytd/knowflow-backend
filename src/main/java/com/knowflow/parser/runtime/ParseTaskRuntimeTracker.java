package com.knowflow.parser.runtime;

public interface ParseTaskRuntimeTracker {

    void markQueued(Long taskId, String transport);

    void markDispatchFailed(Long taskId, String errorMessage);

    boolean tryAcquireWorkerLock(Long taskId, String workerId);

    void releaseWorkerLock(Long taskId, String workerId);

    void forceReleaseWorkerLock(Long taskId);

    void markDequeued(Long taskId, String workerId);

    void markParsing(Long taskId, String workerId);

    void markPersisting(Long taskId, String workerId, int chunkCount);

    void markSuccess(Long taskId, String workerId, int chunkCount, long durationMs);

    void markFailure(Long taskId, String workerId, String errorMessage);

    ParseTaskRuntimeSnapshot getSnapshot(Long taskId);

    void clearRuntime(Long taskId);
}

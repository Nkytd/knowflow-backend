package com.knowflow.parser.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "local", matchIfMissing = true)
public class NoOpParseTaskRuntimeTracker implements ParseTaskRuntimeTracker {

    @Override
    public void markQueued(Long taskId, String transport) {
    }

    @Override
    public void markDispatchFailed(Long taskId, String errorMessage) {
    }

    @Override
    public boolean tryAcquireWorkerLock(Long taskId, String workerId) {
        return true;
    }

    @Override
    public void releaseWorkerLock(Long taskId, String workerId) {
    }

    @Override
    public void forceReleaseWorkerLock(Long taskId) {
    }

    @Override
    public void markDequeued(Long taskId, String workerId) {
    }

    @Override
    public void markParsing(Long taskId, String workerId) {
    }

    @Override
    public void markPersisting(Long taskId, String workerId, int chunkCount) {
    }

    @Override
    public void markSuccess(Long taskId, String workerId, int chunkCount, long durationMs) {
    }

    @Override
    public void markFailure(Long taskId, String workerId, String errorMessage) {
    }

    @Override
    public ParseTaskRuntimeSnapshot getSnapshot(Long taskId) {
        return null;
    }

    @Override
    public void clearRuntime(Long taskId) {
    }
}

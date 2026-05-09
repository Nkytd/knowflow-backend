package com.knowflow.parser.service;

import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.parser.entity.ParseTaskEntity;

import java.time.LocalDateTime;

public interface KnowledgeIndexTaskExecutionService {

    IndexExecutionContext startProcessing(Long taskId);

    void completeSuccess(Long taskId, LocalDateTime attemptStartedAt, int indexedChunkCount, long durationMs);

    void completeFailure(Long taskId, LocalDateTime attemptStartedAt, String errorMessage, long durationMs);

    boolean isCompletable(Long taskId, LocalDateTime attemptStartedAt);

    record IndexExecutionContext(ParseTaskEntity task, KnowledgeDocumentEntity document) {
    }
}

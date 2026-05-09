package com.knowflow.parser.service;

import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.model.ParsedChunk;

import java.time.LocalDateTime;
import java.util.List;

public interface ParseTaskExecutionService {

    ParseExecutionContext startProcessing(Long taskId);

    void completeSuccess(Long taskId, LocalDateTime attemptStartedAt, List<ParsedChunk> chunks, long durationMs);

    void completeFailure(Long taskId, LocalDateTime attemptStartedAt, String errorMessage, long durationMs);

    boolean isCompletable(Long taskId, LocalDateTime attemptStartedAt);

    record ParseExecutionContext(ParseTaskEntity task, KnowledgeDocumentEntity document) {
    }
}

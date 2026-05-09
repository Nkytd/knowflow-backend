package com.knowflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.entity.KnowledgeChunkEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeChunkMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.parser.bootstrap.ParseTaskRecoveryBootstrap;
import com.knowflow.parser.config.ParseTaskMessagingProperties;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.entity.ParseTaskGovernanceEventEntity;
import com.knowflow.parser.governance.mapper.ParseTaskGovernanceEventMapper;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.messaging.ParseTaskDispatchGateway;
import com.knowflow.parser.model.ParsedChunk;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.service.KnowledgeIndexTaskExecutionService;
import com.knowflow.parser.service.ParseTaskExecutionService;
import com.knowflow.parser.support.ParseTaskStateMachine;
import com.knowflow.parser.support.TaskWorkerRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ParseTaskAttemptIdempotencyIntegrationTests {

    @Autowired
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private ParseTaskMapper parseTaskMapper;

    @Autowired
    private ParseTaskGovernanceEventMapper governanceEventMapper;

    @Autowired
    private ParseTaskGovernanceEventService governanceEventService;

    @Autowired
    private ParseTaskExecutionService parseTaskExecutionService;

    @Autowired
    private KnowledgeIndexTaskExecutionService knowledgeIndexTaskExecutionService;

    @Test
    void shouldIgnoreOldParseAttemptWhenTaskHasBeenRestarted() throws Exception {
        KnowledgeDocumentEntity document = createDocument("parse-attempt.txt", ParseTaskStateMachine.PENDING, ParseTaskStateMachine.PENDING);
        ParseTaskEntity task = createTask(document.getId(), TaskWorkerRouter.TASK_TYPE_PARSE);

        ParseTaskExecutionService.ParseExecutionContext firstAttempt = parseTaskExecutionService.startProcessing(task.getId());
        LocalDateTime firstStartedAt = firstAttempt.task().getStartedAt();

        resetProcessingTaskToPending(task.getId());
        Thread.sleep(5L);
        ParseTaskExecutionService.ParseExecutionContext secondAttempt = parseTaskExecutionService.startProcessing(task.getId());
        LocalDateTime secondStartedAt = secondAttempt.task().getStartedAt();

        parseTaskExecutionService.completeFailure(task.getId(), firstStartedAt, "old worker failure", 10L);
        ParseTaskEntity afterOldFailure = parseTaskMapper.selectById(task.getId());
        assertThat(afterOldFailure.getStatus()).isEqualTo(ParseTaskStateMachine.PROCESSING);
        assertThat(afterOldFailure.getStartedAt()).isEqualTo(secondStartedAt);
        assertThat(afterOldFailure.getErrorMessage()).isNull();

        parseTaskExecutionService.completeSuccess(
                task.getId(),
                firstStartedAt,
                List.of(ParsedChunk.builder().chunkNo(1).content("old attempt chunk must not be persisted").build()),
                11L
        );
        assertThat(countChunks(document.getId())).isZero();
        assertThat(countGovernanceEvents(task.getId(), ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED))
                .isEqualTo(2L);

        parseTaskExecutionService.completeSuccess(
                task.getId(),
                secondStartedAt,
                List.of(ParsedChunk.builder().chunkNo(1).content("new attempt chunk should be persisted").build()),
                20L
        );

        ParseTaskEntity completed = parseTaskMapper.selectById(task.getId());
        KnowledgeDocumentEntity completedDocument = knowledgeDocumentMapper.selectById(document.getId());
        assertThat(completed.getStatus()).isEqualTo(ParseTaskStateMachine.SUCCESS);
        assertThat(completed.getDurationMs()).isEqualTo(20L);
        assertThat(countChunks(document.getId())).isEqualTo(1L);
        assertThat(completedDocument.getParseStatus()).isEqualTo(ParseTaskStateMachine.SUCCESS);
        assertThat(countGovernanceEvents(task.getId(), ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED))
                .isEqualTo(2L);
    }

    @Test
    void shouldIgnoreOldIndexAttemptWhenTaskHasBeenRestarted() throws Exception {
        KnowledgeDocumentEntity document = createDocument("index-attempt.txt", ParseTaskStateMachine.SUCCESS, ParseTaskStateMachine.PENDING);
        createChunk(document.getId());
        ParseTaskEntity task = createTask(document.getId(), TaskWorkerRouter.TASK_TYPE_INDEX_VECTOR);

        KnowledgeIndexTaskExecutionService.IndexExecutionContext firstAttempt = knowledgeIndexTaskExecutionService.startProcessing(task.getId());
        LocalDateTime firstStartedAt = firstAttempt.task().getStartedAt();

        resetProcessingTaskToPending(task.getId());
        Thread.sleep(5L);
        KnowledgeIndexTaskExecutionService.IndexExecutionContext secondAttempt = knowledgeIndexTaskExecutionService.startProcessing(task.getId());
        LocalDateTime secondStartedAt = secondAttempt.task().getStartedAt();

        knowledgeIndexTaskExecutionService.completeFailure(task.getId(), firstStartedAt, "old index failure", 10L);
        ParseTaskEntity afterOldFailure = parseTaskMapper.selectById(task.getId());
        KnowledgeDocumentEntity afterOldFailureDocument = knowledgeDocumentMapper.selectById(document.getId());
        assertThat(afterOldFailure.getStatus()).isEqualTo(ParseTaskStateMachine.PROCESSING);
        assertThat(afterOldFailure.getStartedAt()).isEqualTo(secondStartedAt);
        assertThat(afterOldFailure.getErrorMessage()).isNull();
        assertThat(afterOldFailureDocument.getIndexStatus()).isEqualTo(ParseTaskStateMachine.PROCESSING);
        assertThat(countGovernanceEvents(task.getId(), ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED))
                .isEqualTo(1L);

        knowledgeIndexTaskExecutionService.completeSuccess(task.getId(), secondStartedAt, 1, 20L);

        ParseTaskEntity completed = parseTaskMapper.selectById(task.getId());
        KnowledgeDocumentEntity completedDocument = knowledgeDocumentMapper.selectById(document.getId());
        assertThat(completed.getStatus()).isEqualTo(ParseTaskStateMachine.SUCCESS);
        assertThat(completed.getDurationMs()).isEqualTo(20L);
        assertThat(completedDocument.getIndexStatus()).isEqualTo(ParseTaskStateMachine.SUCCESS);
    }

    @Test
    void shouldRecoverStaleProcessingTaskOnStartup() {
        KnowledgeDocumentEntity staleDocument = createDocument("stale-processing.txt", ParseTaskStateMachine.PROCESSING, ParseTaskStateMachine.PROCESSING);
        ParseTaskEntity staleTask = createTask(staleDocument.getId(), TaskWorkerRouter.TASK_TYPE_PARSE);
        LocalDateTime staleStartedAt = LocalDateTime.now().minusMinutes(30);
        parseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getId, staleTask.getId())
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .set(ParseTaskEntity::getStartedAt, staleStartedAt)
        );

        KnowledgeDocumentEntity pendingDocument = createDocument("pending-startup.txt", ParseTaskStateMachine.PENDING, ParseTaskStateMachine.PENDING);
        ParseTaskEntity pendingTask = createTask(pendingDocument.getId(), TaskWorkerRouter.TASK_TYPE_PARSE);

        ParseTaskDispatchGateway dispatchGateway = mock(ParseTaskDispatchGateway.class);
        ParseTaskRuntimeTracker runtimeTracker = mock(ParseTaskRuntimeTracker.class);
        ParseTaskMessagingProperties properties = new ParseTaskMessagingProperties();
        properties.setStaleProcessingMinutes(10);
        properties.setStartupRecoveryLimit(100);

        ParseTaskRecoveryBootstrap recoveryBootstrap = new ParseTaskRecoveryBootstrap(
                parseTaskMapper,
                knowledgeDocumentMapper,
                dispatchGateway,
                runtimeTracker,
                properties,
                governanceEventService
        );
        recoveryBootstrap.recover();

        ParseTaskEntity recoveredTask = parseTaskMapper.selectById(staleTask.getId());
        KnowledgeDocumentEntity recoveredDocument = knowledgeDocumentMapper.selectById(staleDocument.getId());
        assertThat(recoveredTask.getStatus()).isEqualTo(ParseTaskStateMachine.PENDING);
        assertThat(recoveredTask.getStartedAt()).isNull();
        assertThat(recoveredDocument.getParseStatus()).isEqualTo(ParseTaskStateMachine.PENDING);
        assertThat(recoveredDocument.getIndexStatus()).isEqualTo(ParseTaskStateMachine.PENDING);
        assertThat(countGovernanceEvents(staleTask.getId(), ParseTaskGovernanceEventService.STARTUP_STALE_TASK_RECOVERED))
                .isEqualTo(1L);
        verify(runtimeTracker).forceReleaseWorkerLock(staleTask.getId());
        verify(dispatchGateway).dispatch(staleTask.getId());
        verify(dispatchGateway).dispatch(pendingTask.getId());
    }

    private KnowledgeDocumentEntity createDocument(String docName, String parseStatus, String indexStatus) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setTenantId(1L);
        document.setKnowledgeBaseId(1L);
        document.setDocCode(CodeGenerator.documentCode());
        document.setDocName(docName);
        document.setSourceType("UPLOAD");
        document.setStorageType("local");
        document.setStoragePath("target/test-uploads/" + docName);
        document.setFileType("txt");
        document.setFileSize(128L);
        document.setVersionNo(1);
        document.setStatus("ENABLED");
        document.setParseStatus(parseStatus);
        document.setIndexStatus(indexStatus);
        document.setChunkCount(0);
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    private ParseTaskEntity createTask(Long documentId, String taskType) {
        ParseTaskEntity task = new ParseTaskEntity();
        task.setTenantId(1L);
        task.setDocumentId(documentId);
        task.setTaskNo(CodeGenerator.parseTaskCode());
        task.setTaskType(taskType);
        task.setStatus(ParseTaskStateMachine.PENDING);
        task.setRetryCount(0);
        parseTaskMapper.insert(task);
        return task;
    }

    private void createChunk(Long documentId) {
        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
        chunk.setTenantId(1L);
        chunk.setKnowledgeBaseId(1L);
        chunk.setDocumentId(documentId);
        chunk.setChunkNo(1);
        chunk.setContent("index attempt chunk");
        chunk.setCharCount(19);
        chunk.setTokenCount(4);
        chunk.setStatus("ENABLED");
        knowledgeChunkMapper.insert(chunk);
    }

    private void resetProcessingTaskToPending(Long taskId) {
        parseTaskMapper.update(
                null,
                new LambdaUpdateWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getId, taskId)
                        .eq(ParseTaskEntity::getStatus, ParseTaskStateMachine.PROCESSING)
                        .set(ParseTaskEntity::getStatus, ParseTaskStateMachine.PENDING)
                        .set(ParseTaskEntity::getStartedAt, null)
                        .set(ParseTaskEntity::getFinishedAt, null)
                        .set(ParseTaskEntity::getDurationMs, null)
                        .set(ParseTaskEntity::getErrorMessage, null)
        );
    }

    private long countChunks(Long documentId) {
        return knowledgeChunkMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeChunkEntity>()
                        .eq(KnowledgeChunkEntity::getDocumentId, documentId)
        );
    }

    private long countGovernanceEvents(Long taskId, String eventType) {
        return governanceEventMapper.selectCount(
                new LambdaQueryWrapper<ParseTaskGovernanceEventEntity>()
                        .eq(ParseTaskGovernanceEventEntity::getTaskId, taskId)
                        .eq(ParseTaskGovernanceEventEntity::getEventType, eventType)
        );
    }
}

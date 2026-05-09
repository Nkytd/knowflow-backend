package com.knowflow.parser.worker;

import com.knowflow.parser.model.ParsedDocument;
import com.knowflow.parser.deadletter.support.TaskExecutionFailedException;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.service.DocumentParsingService;
import com.knowflow.parser.service.ParseTaskExecutionService;
import com.knowflow.parser.runtime.ParseTaskRuntimeTracker;
import com.knowflow.parser.support.ParseWorkerIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DocumentParseWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseWorker.class);

    private final ParseTaskExecutionService parseTaskExecutionService;
    private final DocumentParsingService documentParsingService;
    private final ParseTaskRuntimeTracker parseTaskRuntimeTracker;
    private final ParseWorkerIdentityProvider parseWorkerIdentityProvider;
    private final ParseTaskGovernanceEventService governanceEventService;

    public DocumentParseWorker(ParseTaskExecutionService parseTaskExecutionService,
                               DocumentParsingService documentParsingService,
                               ParseTaskRuntimeTracker parseTaskRuntimeTracker,
                               ParseWorkerIdentityProvider parseWorkerIdentityProvider,
                               ParseTaskGovernanceEventService governanceEventService) {
        this.parseTaskExecutionService = parseTaskExecutionService;
        this.documentParsingService = documentParsingService;
        this.parseTaskRuntimeTracker = parseTaskRuntimeTracker;
        this.parseWorkerIdentityProvider = parseWorkerIdentityProvider;
        this.governanceEventService = governanceEventService;
    }

    public void process(Long taskId, boolean deadLetterOnFailure) {
        String workerId = parseWorkerIdentityProvider.getWorkerId();
        if (!parseTaskRuntimeTracker.tryAcquireWorkerLock(taskId, workerId)) {
            governanceEventService.record(taskId,
                    ParseTaskGovernanceEventService.DUPLICATE_CONSUMPTION_SKIPPED,
                    "Worker lock already held; duplicated parse task message was skipped.",
                    workerId,
                    null);
            log.info("Skip duplicated parse task consumption. taskId={}, workerId={}", taskId, workerId);
            return;
        }

        long start = System.currentTimeMillis();
        LocalDateTime attemptStartedAt = null;
        try {
            ParseTaskExecutionService.ParseExecutionContext context = parseTaskExecutionService.startProcessing(taskId);
            if (context == null) {
                governanceEventService.record(taskId,
                        ParseTaskGovernanceEventService.NON_PENDING_MESSAGE_SKIPPED,
                        "Parse task is no longer PENDING; message was skipped.",
                        workerId,
                        null);
                return;
            }
            attemptStartedAt = context.task().getStartedAt();

            parseTaskRuntimeTracker.markDequeued(taskId, workerId);
            parseTaskRuntimeTracker.markParsing(taskId, workerId);
            ParsedDocument parsedDocument = documentParsingService.parse(context.document());
            parseTaskRuntimeTracker.markPersisting(taskId, workerId, parsedDocument.getChunks().size());
            long durationMs = System.currentTimeMillis() - start;
            parseTaskExecutionService.completeSuccess(taskId, attemptStartedAt, parsedDocument.getChunks(), durationMs);
            parseTaskRuntimeTracker.markSuccess(taskId, workerId, parsedDocument.getChunks().size(), durationMs);
            log.info("Parse task finished successfully. taskId={}, workerId={}, chunkCount={}",
                    taskId,
                    workerId,
                    parsedDocument.getChunks().size());
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - start;
            boolean completable = attemptStartedAt != null && parseTaskExecutionService.isCompletable(taskId, attemptStartedAt);
            if (completable) {
                parseTaskExecutionService.completeFailure(taskId, attemptStartedAt, ex.getMessage(), durationMs);
                parseTaskRuntimeTracker.markFailure(taskId, workerId, ex.getMessage());
            } else if (attemptStartedAt != null) {
                governanceEventService.record(taskId,
                        ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED,
                        "Parse attempt failure was ignored because the attempt is no longer completable: " + ex.getMessage(),
                        workerId,
                        attemptStartedAt);
            }
            log.error("Parse task failed. taskId={}, workerId={}", taskId, workerId, ex);
            if (deadLetterOnFailure && completable) {
                throw new TaskExecutionFailedException(taskId, ex.getMessage());
            }
        } finally {
            parseTaskRuntimeTracker.releaseWorkerLock(taskId, workerId);
        }
    }
}

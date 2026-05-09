package com.knowflow.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.exception.BizException;
import com.knowflow.common.exception.ErrorCode;
import com.knowflow.common.response.PageResponse;
import com.knowflow.common.security.CurrentUser;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.common.util.CodeGenerator;
import com.knowflow.knowledge.dto.CreateKnowledgeBaseRequest;
import com.knowflow.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.knowflow.knowledge.entity.KnowledgeBaseEntity;
import com.knowflow.knowledge.entity.KnowledgeDocumentEntity;
import com.knowflow.knowledge.mapper.KnowledgeBaseMapper;
import com.knowflow.knowledge.mapper.KnowledgeDocumentMapper;
import com.knowflow.knowledge.service.KnowledgeBaseService;
import com.knowflow.knowledge.vo.KnowledgeBaseFailedDocumentVO;
import com.knowflow.knowledge.vo.KnowledgeBaseFailureReasonVO;
import com.knowflow.knowledge.vo.KnowledgeBaseOptionVO;
import com.knowflow.knowledge.vo.KnowledgeBaseStatsVO;
import com.knowflow.knowledge.vo.KnowledgeBaseVO;
import com.knowflow.parser.deadletter.entity.DeadLetterMessageEntity;
import com.knowflow.parser.deadletter.mapper.DeadLetterMessageMapper;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.tenant.service.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TASK_TYPE_PARSE = "PARSE";
    private static final String TASK_TYPE_INDEX_VECTOR = "INDEX_VECTOR";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;
    private final TenantService tenantService;
    private final CurrentUserProvider currentUserProvider;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    ParseTaskMapper parseTaskMapper,
                                    DeadLetterMessageMapper deadLetterMessageMapper,
                                    TenantService tenantService,
                                    CurrentUserProvider currentUserProvider) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.parseTaskMapper = parseTaskMapper;
        this.deadLetterMessageMapper = deadLetterMessageMapper;
        this.tenantService = tenantService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        tenantService.validateTenantExists(currentUser.tenantId());

        String kbCode = StringUtils.hasText(request.getKbCode()) ? request.getKbCode() : CodeGenerator.knowledgeBaseCode();
        if (existsByCode(currentUser.tenantId(), kbCode, null)) {
            throw new BizException(ErrorCode.CONFLICT, "Knowledge base code already exists");
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setTenantId(currentUser.tenantId());
        entity.setKbCode(kbCode);
        entity.setKbName(request.getKbName());
        entity.setDescription(request.getDescription());
        entity.setStatus(STATUS_ENABLED);
        entity.setDocCount(0);
        knowledgeBaseMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public PageResponse<KnowledgeBaseVO> page(Integer pageNo, Integer pageSize, String keyword, String status) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Page<KnowledgeBaseEntity> page = knowledgeBaseMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, currentUser.tenantId())
                        .like(StringUtils.hasText(keyword), KnowledgeBaseEntity::getKbName, keyword)
                        .eq(StringUtils.hasText(status), KnowledgeBaseEntity::getStatus, status)
                        .orderByDesc(KnowledgeBaseEntity::getCreatedAt)
        );
        return PageResponse.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList()
        );
    }

    @Override
    public List<KnowledgeBaseOptionVO> listEnabledOptions() {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseEntity>()
                                .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                                .eq(KnowledgeBaseEntity::getStatus, STATUS_ENABLED)
                                .orderByDesc(KnowledgeBaseEntity::getDocCount)
                                .orderByAsc(KnowledgeBaseEntity::getKbName)
                ).stream()
                .map(entity -> KnowledgeBaseOptionVO.builder()
                        .id(entity.getId())
                        .kbCode(entity.getKbCode())
                        .kbName(entity.getKbName())
                        .description(entity.getDescription())
                        .docCount(entity.getDocCount())
                        .build())
                .toList();
    }

    @Override
    public KnowledgeBaseVO getById(Long id) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeBaseEntity entity = getEntityById(currentUser.tenantId(), id);
        return toDetailVO(entity);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(Long id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBaseEntity entity = getEntityById(currentUserProvider.getCurrentUser().tenantId(), id);
        entity.setKbName(request.getKbName());
        entity.setDescription(request.getDescription());
        knowledgeBaseMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        KnowledgeBaseEntity entity = getEntityById(currentUserProvider.getCurrentUser().tenantId(), id);
        entity.setStatus(status);
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    public void validateKnowledgeBaseExists(Long tenantId, Long knowledgeBaseId) {
        getEntityById(tenantId, knowledgeBaseId);
    }

    @Override
    @Transactional
    public void refreshDocumentCount(Long tenantId, Long knowledgeBaseId) {
        Long count = knowledgeDocumentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, tenantId)
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, knowledgeBaseId)
        );
        KnowledgeBaseEntity entity = getEntityById(tenantId, knowledgeBaseId);
        entity.setDocCount(count.intValue());
        knowledgeBaseMapper.updateById(entity);
    }

    private boolean existsByCode(Long tenantId, String kbCode, Long excludeId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                        .eq(KnowledgeBaseEntity::getKbCode, kbCode)
                        .ne(Objects.nonNull(excludeId), KnowledgeBaseEntity::getId, excludeId)
                        .last("limit 1")
        );
        return entity != null;
    }

    private KnowledgeBaseEntity getEntityById(Long tenantId, Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getId, id)
                        .eq(KnowledgeBaseEntity::getTenantId, tenantId)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge base not found");
        }
        return entity;
    }

    private KnowledgeBaseVO toVO(KnowledgeBaseEntity entity) {
        return baseBuilder(entity)
                .docCount(entity.getDocCount())
                .build();
    }

    private KnowledgeBaseVO toDetailVO(KnowledgeBaseEntity entity) {
        List<KnowledgeDocumentEntity> documents = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getTenantId, entity.getTenantId())
                        .eq(KnowledgeDocumentEntity::getKnowledgeBaseId, entity.getId())
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
        );

        List<KnowledgeDocumentEntity> failedDocuments = documents.stream()
                .filter(this::hasFailure)
                .sorted(this::compareDocumentsByFreshness)
                .toList();

        Map<Long, ParseTaskEntity> latestFailedTaskByDocument = loadLatestFailedTaskByDocument(entity.getTenantId(), failedDocuments);
        Map<Long, Integer> openDeadLetterCountByDocument = loadOpenDeadLetterCountByDocument(entity.getTenantId(), failedDocuments);

        List<KnowledgeBaseFailedDocumentVO> failedDocumentVOs = failedDocuments.stream()
                .map(document -> toFailedDocumentVO(
                        document,
                        latestFailedTaskByDocument.get(document.getId()),
                        openDeadLetterCountByDocument.getOrDefault(document.getId(), 0)
                ))
                .toList();

        int openDeadLetterCount = openDeadLetterCountByDocument.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        return baseBuilder(entity)
                .docCount(documents.size())
                .stats(buildStats(documents, openDeadLetterCount))
                .topFailureReasons(buildFailureReasons(failedDocumentVOs))
                .failedDocuments(failedDocumentVOs)
                .build();
    }

    private KnowledgeBaseVO.KnowledgeBaseVOBuilder baseBuilder(KnowledgeBaseEntity entity) {
        return KnowledgeBaseVO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .kbCode(entity.getKbCode())
                .kbName(entity.getKbName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());
    }

    private KnowledgeBaseStatsVO buildStats(List<KnowledgeDocumentEntity> documents, int openDeadLetterCount) {
        int enabledDocuments = 0;
        int disabledDocuments = 0;
        int parsePendingCount = 0;
        int parseProcessingCount = 0;
        int parseSuccessCount = 0;
        int parseFailedCount = 0;
        int indexPendingCount = 0;
        int indexProcessingCount = 0;
        int indexSuccessCount = 0;
        int indexFailedCount = 0;
        int failedDocumentCount = 0;
        int totalChunks = 0;

        for (KnowledgeDocumentEntity document : documents) {
            if (STATUS_ENABLED.equals(document.getStatus())) {
                enabledDocuments += 1;
            } else if (STATUS_DISABLED.equals(document.getStatus())) {
                disabledDocuments += 1;
            }

            switch (normalizeStatus(document.getParseStatus())) {
                case STATUS_PENDING -> parsePendingCount += 1;
                case STATUS_PROCESSING -> parseProcessingCount += 1;
                case STATUS_SUCCESS -> parseSuccessCount += 1;
                case STATUS_FAILED -> parseFailedCount += 1;
                default -> {
                }
            }

            switch (normalizeStatus(document.getIndexStatus())) {
                case STATUS_PENDING -> indexPendingCount += 1;
                case STATUS_PROCESSING -> indexProcessingCount += 1;
                case STATUS_SUCCESS -> indexSuccessCount += 1;
                case STATUS_FAILED -> indexFailedCount += 1;
                default -> {
                }
            }

            if (hasFailure(document)) {
                failedDocumentCount += 1;
            }
            totalChunks += document.getChunkCount() == null ? 0 : document.getChunkCount();
        }

        return KnowledgeBaseStatsVO.builder()
                .totalDocuments(documents.size())
                .enabledDocuments(enabledDocuments)
                .disabledDocuments(disabledDocuments)
                .parsePendingCount(parsePendingCount)
                .parseProcessingCount(parseProcessingCount)
                .parseSuccessCount(parseSuccessCount)
                .parseFailedCount(parseFailedCount)
                .indexPendingCount(indexPendingCount)
                .indexProcessingCount(indexProcessingCount)
                .indexSuccessCount(indexSuccessCount)
                .indexFailedCount(indexFailedCount)
                .failedDocumentCount(failedDocumentCount)
                .totalChunks(totalChunks)
                .openDeadLetterCount(openDeadLetterCount)
                .build();
    }

    private Map<Long, ParseTaskEntity> loadLatestFailedTaskByDocument(Long tenantId, List<KnowledgeDocumentEntity> failedDocuments) {
        if (failedDocuments.isEmpty()) {
            return Map.of();
        }
        List<Long> documentIds = failedDocuments.stream().map(KnowledgeDocumentEntity::getId).toList();
        List<ParseTaskEntity> failedTasks = parseTaskMapper.selectList(
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(ParseTaskEntity::getTenantId, tenantId)
                        .in(ParseTaskEntity::getDocumentId, documentIds)
                        .eq(ParseTaskEntity::getStatus, STATUS_FAILED)
                        .orderByDesc(ParseTaskEntity::getCreatedAt)
        );
        Map<Long, ParseTaskEntity> latestFailedTaskByDocument = new LinkedHashMap<>();
        for (ParseTaskEntity failedTask : failedTasks) {
            latestFailedTaskByDocument.putIfAbsent(failedTask.getDocumentId(), failedTask);
        }
        return latestFailedTaskByDocument;
    }

    private Map<Long, Integer> loadOpenDeadLetterCountByDocument(Long tenantId, List<KnowledgeDocumentEntity> failedDocuments) {
        if (failedDocuments.isEmpty()) {
            return Map.of();
        }
        List<Long> documentIds = failedDocuments.stream().map(KnowledgeDocumentEntity::getId).toList();
        List<DeadLetterMessageEntity> deadLetters = deadLetterMessageMapper.selectList(
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(DeadLetterMessageEntity::getTenantId, tenantId)
                        .in(DeadLetterMessageEntity::getDocumentId, documentIds)
                        .isNull(DeadLetterMessageEntity::getResolvedAt)
        );
        Map<Long, Integer> countByDocument = new LinkedHashMap<>();
        for (DeadLetterMessageEntity deadLetter : deadLetters) {
            if (deadLetter.getDocumentId() == null) {
                continue;
            }
            countByDocument.merge(deadLetter.getDocumentId(), 1, Integer::sum);
        }
        return countByDocument;
    }

    private KnowledgeBaseFailedDocumentVO toFailedDocumentVO(KnowledgeDocumentEntity document,
                                                             ParseTaskEntity latestFailedTask,
                                                             int deadLetterCount) {
        return KnowledgeBaseFailedDocumentVO.builder()
                .documentId(document.getId())
                .docCode(document.getDocCode())
                .docName(document.getDocName())
                .status(document.getStatus())
                .parseStatus(document.getParseStatus())
                .indexStatus(document.getIndexStatus())
                .chunkCount(document.getChunkCount())
                .latestTaskId(latestFailedTask == null ? null : latestFailedTask.getId())
                .latestTaskType(resolveFailureTaskType(document, latestFailedTask))
                .latestTaskStatus(latestFailedTask == null ? inferTaskStatus(document) : latestFailedTask.getStatus())
                .retryCount(latestFailedTask == null ? 0 : latestFailedTask.getRetryCount())
                .errorMessage(resolveFailureReason(document, latestFailedTask))
                .deadLetterCount(deadLetterCount)
                .updatedAt(resolveDocumentOrderTime(document))
                .build();
    }

    private List<KnowledgeBaseFailureReasonVO> buildFailureReasons(List<KnowledgeBaseFailedDocumentVO> failedDocuments) {
        if (failedDocuments.isEmpty()) {
            return List.of();
        }

        Map<String, FailureReasonBucket> bucketMap = new LinkedHashMap<>();
        for (KnowledgeBaseFailedDocumentVO failedDocument : failedDocuments) {
            String reason = summarizeReason(failedDocument.getErrorMessage());
            String taskType = StringUtils.hasText(failedDocument.getLatestTaskType())
                    ? failedDocument.getLatestTaskType()
                    : TASK_TYPE_PARSE;
            String key = taskType + "|" + reason;

            FailureReasonBucket bucket = bucketMap.get(key);
            if (bucket == null) {
                bucket = new FailureReasonBucket(reason, taskType, failedDocument.getDocumentId(), failedDocument.getDocName());
                bucketMap.put(key, bucket);
            }
            bucket.increment();
        }

        List<KnowledgeBaseFailureReasonVO> failureReasons = new ArrayList<>();
        for (FailureReasonBucket bucket : bucketMap.values()) {
            failureReasons.add(KnowledgeBaseFailureReasonVO.builder()
                    .reason(bucket.reason())
                    .taskType(bucket.taskType())
                    .documentCount(bucket.documentCount())
                    .sampleDocumentId(bucket.sampleDocumentId())
                    .sampleDocumentName(bucket.sampleDocumentName())
                    .build());
        }

        return failureReasons.stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getDocumentCount(), left.getDocumentCount());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return left.getReason().compareToIgnoreCase(right.getReason());
                })
                .limit(6)
                .toList();
    }

    private boolean hasFailure(KnowledgeDocumentEntity document) {
        return STATUS_FAILED.equals(normalizeStatus(document.getParseStatus()))
                || STATUS_FAILED.equals(normalizeStatus(document.getIndexStatus()));
    }

    private int compareDocumentsByFreshness(KnowledgeDocumentEntity left, KnowledgeDocumentEntity right) {
        LocalDateTime leftTime = resolveDocumentOrderTime(left);
        LocalDateTime rightTime = resolveDocumentOrderTime(right);
        if (leftTime == null && rightTime == null) {
            return Long.compare(right.getId(), left.getId());
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        int timeCompare = rightTime.compareTo(leftTime);
        if (timeCompare != 0) {
            return timeCompare;
        }
        return Long.compare(right.getId(), left.getId());
    }

    private LocalDateTime resolveDocumentOrderTime(KnowledgeDocumentEntity document) {
        return document.getUpdatedAt() != null ? document.getUpdatedAt() : document.getCreatedAt();
    }

    private String resolveFailureReason(KnowledgeDocumentEntity document, ParseTaskEntity latestFailedTask) {
        if (latestFailedTask != null && StringUtils.hasText(latestFailedTask.getErrorMessage())) {
            return sanitizeReason(latestFailedTask.getErrorMessage());
        }
        if (STATUS_FAILED.equals(normalizeStatus(document.getParseStatus()))) {
            return "Parse task failed without a recorded error message";
        }
        if (STATUS_FAILED.equals(normalizeStatus(document.getIndexStatus()))) {
            return "Vector indexing failed without a recorded error message";
        }
        return "Failure reason is not available";
    }

    private String resolveFailureTaskType(KnowledgeDocumentEntity document, ParseTaskEntity latestFailedTask) {
        if (latestFailedTask != null && StringUtils.hasText(latestFailedTask.getTaskType())) {
            return latestFailedTask.getTaskType();
        }
        if (STATUS_FAILED.equals(normalizeStatus(document.getIndexStatus()))
                && !STATUS_FAILED.equals(normalizeStatus(document.getParseStatus()))) {
            return TASK_TYPE_INDEX_VECTOR;
        }
        return TASK_TYPE_PARSE;
    }

    private String inferTaskStatus(KnowledgeDocumentEntity document) {
        if (STATUS_FAILED.equals(normalizeStatus(document.getIndexStatus()))
                && !STATUS_FAILED.equals(normalizeStatus(document.getParseStatus()))) {
            return document.getIndexStatus();
        }
        return document.getParseStatus();
    }

    private String summarizeReason(String reason) {
        String sanitized = sanitizeReason(reason);
        if (sanitized.length() <= 120) {
            return sanitized;
        }
        return sanitized.substring(0, 117) + "...";
    }

    private String sanitizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "Failure reason is not available";
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private static final class FailureReasonBucket {

        private final String reason;
        private final String taskType;
        private final Long sampleDocumentId;
        private final String sampleDocumentName;
        private int documentCount;

        private FailureReasonBucket(String reason, String taskType, Long sampleDocumentId, String sampleDocumentName) {
            this.reason = reason;
            this.taskType = taskType;
            this.sampleDocumentId = sampleDocumentId;
            this.sampleDocumentName = sampleDocumentName;
        }

        private void increment() {
            this.documentCount += 1;
        }

        private String reason() {
            return reason;
        }

        private String taskType() {
            return taskType;
        }

        private int documentCount() {
            return documentCount;
        }

        private Long sampleDocumentId() {
            return sampleDocumentId;
        }

        private String sampleDocumentName() {
            return sampleDocumentName;
        }
    }
}

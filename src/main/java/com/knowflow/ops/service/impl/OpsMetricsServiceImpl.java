package com.knowflow.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.security.CurrentUserProvider;
import com.knowflow.integration.storage.StorageProperties;
import com.knowflow.ops.service.OpsMetricsService;
import com.knowflow.ops.vo.AiModelUsageMetricVO;
import com.knowflow.ops.vo.AiUsageOverviewVO;
import com.knowflow.ops.vo.DeadLetterMetricVO;
import com.knowflow.ops.vo.OpsHealthIssueVO;
import com.knowflow.ops.vo.InfrastructureComponentHealthVO;
import com.knowflow.ops.vo.InfrastructureHealthVO;
import com.knowflow.ops.vo.TaskGovernanceMetricVO;
import com.knowflow.ops.vo.TaskOpsOverviewVO;
import com.knowflow.ops.vo.TaskStatusMetricVO;
import com.knowflow.ops.vo.TaskTypeMetricVO;
import com.knowflow.parser.config.ParseTaskMessagingProperties;
import com.knowflow.parser.deadletter.entity.DeadLetterMessageEntity;
import com.knowflow.parser.deadletter.mapper.DeadLetterMessageMapper;
import com.knowflow.parser.deadletter.service.DeadLetterMessageService;
import com.knowflow.parser.entity.ParseTaskEntity;
import com.knowflow.parser.governance.entity.ParseTaskGovernanceEventEntity;
import com.knowflow.parser.governance.mapper.ParseTaskGovernanceEventMapper;
import com.knowflow.parser.governance.service.ParseTaskGovernanceEventService;
import com.knowflow.parser.mapper.ParseTaskMapper;
import com.knowflow.parser.support.ParseTaskStateMachine;
import com.knowflow.qa.entity.QaMessageEntity;
import com.knowflow.qa.mapper.QaMessageMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OpsMetricsServiceImpl implements OpsMetricsService {

    private final ParseTaskMapper parseTaskMapper;
    private final DeadLetterMessageMapper deadLetterMessageMapper;
    private final ParseTaskGovernanceEventMapper governanceEventMapper;
    private final QaMessageMapper qaMessageMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ParseTaskMessagingProperties messagingProperties;
    private final StorageProperties storageProperties;
    private final HealthEndpoint healthEndpoint;
    private final BigDecimal inputTokenPricePerThousand;
    private final BigDecimal outputTokenPricePerThousand;

    public OpsMetricsServiceImpl(ParseTaskMapper parseTaskMapper,
                                 DeadLetterMessageMapper deadLetterMessageMapper,
                                 ParseTaskGovernanceEventMapper governanceEventMapper,
                                 QaMessageMapper qaMessageMapper,
                                 CurrentUserProvider currentUserProvider,
                                 ParseTaskMessagingProperties messagingProperties,
                                 StorageProperties storageProperties,
                                 HealthEndpoint healthEndpoint,
                                 @Value("${knowflow.ops.ai-cost.input-cny-per-1k:0.002}") BigDecimal inputTokenPricePerThousand,
                                 @Value("${knowflow.ops.ai-cost.output-cny-per-1k:0.006}") BigDecimal outputTokenPricePerThousand) {
        this.parseTaskMapper = parseTaskMapper;
        this.deadLetterMessageMapper = deadLetterMessageMapper;
        this.governanceEventMapper = governanceEventMapper;
        this.qaMessageMapper = qaMessageMapper;
        this.currentUserProvider = currentUserProvider;
        this.messagingProperties = messagingProperties;
        this.storageProperties = storageProperties;
        this.healthEndpoint = healthEndpoint;
        this.inputTokenPricePerThousand = inputTokenPricePerThousand;
        this.outputTokenPricePerThousand = outputTokenPricePerThousand;
    }

    @Override
    public TaskOpsOverviewVO taskOverview(Integer days) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        return buildTaskOverview(days, tenantId);
    }

    @Override
    public TaskOpsOverviewVO globalTaskOverview(Integer days) {
        return buildTaskOverview(days, null);
    }

    @Override
    public InfrastructureHealthVO infrastructureHealth() {
        List<InfrastructureComponentHealthVO> components = new ArrayList<>();
        components.add(component("db", "数据库", "MySQL/H2", true,
                actuatorStatus("db"), "业务数据、租户、文档、任务和工单的主存储。",
                "查看健康端点", "/actuator/health"));
        components.add(component("redis", "Redis", "缓存/分布式锁", messagingProperties.isRabbitMode(),
                actuatorStatus("redis"), messagingProperties.isRabbitMode()
                        ? "用于任务运行态快照、worker 锁和异步链路缓存。"
                        : "当前本地模式未强依赖 Redis，可在 Rabbit 生产模式下启用。",
                "查看解析任务", "/admin/parse-tasks?source=ops-health"));
        components.add(component("rabbit", "RabbitMQ", "消息队列", messagingProperties.isRabbitMode(),
                actuatorStatus("rabbit"), messagingProperties.isRabbitMode()
                        ? "用于文档解析、向量索引和 DLQ 治理的异步消息流。"
                        : "当前解析消息模式为 local，RabbitMQ 未作为必需依赖。",
                "查看死信治理", "/admin/dead-letters?source=ops-health"));
        components.add(component("storage", "文档存储", storageProperties.getType(), true,
                resolveStorageStatus(), resolveStorageDescription(),
                "查看文档管理", "/admin/documents?source=ops-health"));

        long downCount = components.stream()
                .filter(component -> Boolean.TRUE.equals(component.getEnabled()))
                .filter(component -> "DOWN".equals(component.getStatus()) || "UNKNOWN".equals(component.getStatus()))
                .count();
        String overallStatus = downCount == 0 ? "UP" : "DEGRADED";
        String summary = downCount == 0
                ? "核心基础设施依赖当前可用，业务任务链路具备正常运行条件。"
                : "存在 " + downCount + " 个启用中的基础设施依赖异常，请优先检查容器或连接配置。";
        return InfrastructureHealthVO.builder()
                .overallStatus(overallStatus)
                .summary(summary)
                .generatedAt(LocalDateTime.now())
                .components(components)
                .build();
    }

    @Override
    public AiUsageOverviewVO aiUsage(Integer days) {
        Long tenantId = currentUserProvider.getCurrentUser().tenantId();
        int safeDays = sanitizeDays(days);
        LocalDateTime startAt = LocalDateTime.now().minusDays(safeDays);
        List<QaMessageEntity> messages = qaMessageMapper.selectList(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getTenantId, tenantId)
                        .ge(QaMessageEntity::getCreatedAt, startAt)
        );

        UsageCounter total = new UsageCounter();
        Map<String, UsageCounter> byModel = new LinkedHashMap<>();
        for (QaMessageEntity message : messages) {
            total.accept(message);
            String modelName = normalizeModelName(message.getModelName());
            byModel.computeIfAbsent(modelName, ignored -> new UsageCounter()).accept(message);
        }

        List<AiModelUsageMetricVO> modelMetrics = byModel.entrySet().stream()
                .map(entry -> toModelMetric(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AiModelUsageMetricVO::getCallCount).reversed())
                .toList();

        return AiUsageOverviewVO.builder()
                .days(safeDays)
                .totalCallCount(total.callCount)
                .successCallCount(total.successCount)
                .failedCallCount(total.failedCount)
                .noHitCount(total.noHitCount)
                .handoffCount(total.handoffCount)
                .totalInputTokens(total.inputTokens)
                .totalOutputTokens(total.outputTokens)
                .totalTokens(total.inputTokens + total.outputTokens)
                .avgLatencyMs(avg(total.latencies))
                .avgRetrievalLatencyMs(avg(total.retrievalLatencies))
                .avgGenerationLatencyMs(avg(total.generationLatencies))
                .failureRate(rate(total.failedCount, total.callCount))
                .cacheHitRate(rate(total.cacheHitCount, total.callCount))
                .estimatedCostCny(estimateCost(total.inputTokens, total.outputTokens))
                .generatedAt(LocalDateTime.now())
                .modelMetrics(modelMetrics)
                .build();
    }

    private InfrastructureComponentHealthVO component(String key,
                                                     String name,
                                                     String type,
                                                     boolean enabled,
                                                     String status,
                                                     String description,
                                                     String actionText,
                                                     String actionUrl) {
        return InfrastructureComponentHealthVO.builder()
                .key(key)
                .name(name)
                .type(type)
                .enabled(enabled)
                .status(enabled ? status : "DISABLED")
                .description(description)
                .actionText(actionText)
                .actionUrl(actionUrl)
                .build();
    }

    private String actuatorStatus(String componentName) {
        try {
            HealthComponent component = healthEndpoint.healthForPath(componentName);
            if (component == null || component.getStatus() == null) {
                return "UNKNOWN";
            }
            return component.getStatus().getCode();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private String resolveStorageStatus() {
        if ("local".equalsIgnoreCase(storageProperties.getType())) {
            return "UP";
        }
        if ("minio".equalsIgnoreCase(storageProperties.getType())) {
            return probeMinioBucket() ? "UP" : "DOWN";
        }
        return "UNKNOWN";
    }

    private boolean probeMinioBucket() {
        try {
            StorageProperties.Minio minio = storageProperties.getMinio();
            MinioClient client = MinioClient.builder()
                    .endpoint(minio.getEndpoint())
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .build();
            return client.bucketExists(BucketExistsArgs.builder().bucket(minio.getBucket()).build());
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveStorageDescription() {
        if ("minio".equalsIgnoreCase(storageProperties.getType())) {
            return "当前使用 MinIO 托管文档对象，桶名：" + storageProperties.getMinio().getBucket() + "。";
        }
        return "当前使用本地文件系统保存上传文档，路径：" + storageProperties.getLocal().getBasePath() + "。";
    }

    private TaskOpsOverviewVO buildTaskOverview(Integer days, Long tenantId) {
        int safeDays = sanitizeDays(days);
        LocalDateTime startAt = LocalDateTime.now().minusDays(safeDays);
        List<ParseTaskEntity> tasks = parseTaskMapper.selectList(
                new LambdaQueryWrapper<ParseTaskEntity>()
                        .eq(tenantId != null, ParseTaskEntity::getTenantId, tenantId)
                        .ge(ParseTaskEntity::getCreatedAt, startAt)
        );
        List<DeadLetterMessageEntity> deadLetters = deadLetterMessageMapper.selectList(
                new LambdaQueryWrapper<DeadLetterMessageEntity>()
                        .eq(tenantId != null, DeadLetterMessageEntity::getTenantId, tenantId)
                        .ge(DeadLetterMessageEntity::getCreatedAt, startAt)
        );
        List<ParseTaskGovernanceEventEntity> governanceEvents = governanceEventMapper.selectList(
                new LambdaQueryWrapper<ParseTaskGovernanceEventEntity>()
                        .eq(tenantId != null, ParseTaskGovernanceEventEntity::getTenantId, tenantId)
                        .ge(ParseTaskGovernanceEventEntity::getCreatedAt, startAt)
        );

        long totalTaskCount = tasks.size();
        long successTaskCount = countByStatus(tasks, ParseTaskStateMachine.SUCCESS);
        long failedTaskCount = countByStatus(tasks, ParseTaskStateMachine.FAILED);
        long processingTaskCount = countByStatus(tasks, ParseTaskStateMachine.PROCESSING);
        long pendingTaskCount = countByStatus(tasks, ParseTaskStateMachine.PENDING);
        long staleProcessingTaskCount = countStaleProcessingTasks(tasks);
        List<Long> durations = tasks.stream()
                .map(ParseTaskEntity::getDurationMs)
                .filter(Objects::nonNull)
                .filter(duration -> duration >= 0)
                .sorted()
                .toList();

        Double failureRate = rate(failedTaskCount, totalTaskCount);
        Double successRate = rate(successTaskCount, totalTaskCount);
        Long avgDurationMs = avg(durations);
        Long p95DurationMs = percentile(durations, 0.95D);
        DeadLetterMetricVO deadLetterMetrics = buildDeadLetterMetrics(deadLetters);
        TaskGovernanceMetricVO governanceMetrics = buildGovernanceMetrics(governanceEvents);
        List<OpsHealthIssueVO> healthIssues = buildHealthIssues(
                failureRate,
                staleProcessingTaskCount,
                p95DurationMs,
                deadLetterMetrics,
                governanceMetrics
        );
        Integer healthScore = calculateHealthScore(failureRate, staleProcessingTaskCount, p95DurationMs, deadLetterMetrics, governanceMetrics);

        return TaskOpsOverviewVO.builder()
                .days(safeDays)
                .totalTaskCount(totalTaskCount)
                .successTaskCount(successTaskCount)
                .failedTaskCount(failedTaskCount)
                .processingTaskCount(processingTaskCount)
                .pendingTaskCount(pendingTaskCount)
                .staleProcessingTaskCount(staleProcessingTaskCount)
                .failureRate(failureRate)
                .successRate(successRate)
                .avgDurationMs(avgDurationMs)
                .p95DurationMs(p95DurationMs)
                .healthScore(healthScore)
                .healthLevel(resolveHealthLevel(healthScore))
                .healthSummary(resolveHealthSummary(healthScore, healthIssues))
                .generatedAt(LocalDateTime.now())
                .statusMetrics(buildStatusMetrics(tasks))
                .taskTypeMetrics(buildTaskTypeMetrics(tasks))
                .deadLetterMetrics(deadLetterMetrics)
                .governanceMetrics(governanceMetrics)
                .healthIssues(healthIssues)
                .build();
    }

    private AiModelUsageMetricVO toModelMetric(String modelName, UsageCounter counter) {
        return AiModelUsageMetricVO.builder()
                .modelName(modelName)
                .callCount(counter.callCount)
                .successCount(counter.successCount)
                .failedCount(counter.failedCount)
                .noHitCount(counter.noHitCount)
                .totalInputTokens(counter.inputTokens)
                .totalOutputTokens(counter.outputTokens)
                .totalTokens(counter.inputTokens + counter.outputTokens)
                .avgLatencyMs(avg(counter.latencies))
                .avgGenerationLatencyMs(avg(counter.generationLatencies))
                .failureRate(rate(counter.failedCount, counter.callCount))
                .estimatedCostCny(estimateCost(counter.inputTokens, counter.outputTokens))
                .build();
    }

    private String normalizeModelName(String modelName) {
        return modelName == null || modelName.isBlank() ? "unknown" : modelName.trim();
    }

    private BigDecimal estimateCost(long inputTokens, long outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(inputTokenPricePerThousand)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(outputTokenPricePerThousand)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    private static final class UsageCounter {
        private long callCount;
        private long successCount;
        private long failedCount;
        private long noHitCount;
        private long handoffCount;
        private long cacheHitCount;
        private long inputTokens;
        private long outputTokens;
        private final List<Long> latencies = new ArrayList<>();
        private final List<Long> retrievalLatencies = new ArrayList<>();
        private final List<Long> generationLatencies = new ArrayList<>();

        private void accept(QaMessageEntity message) {
            callCount++;
            if ("SUCCESS".equals(message.getAnswerStatus())) {
                successCount++;
            }
            if ("FAILED".equals(message.getAnswerStatus())) {
                failedCount++;
            }
            if ("NO_HIT".equals(message.getAnswerStatus())) {
                noHitCount++;
            }
            if (message.getNeedHumanHandoff() != null && message.getNeedHumanHandoff() == 1) {
                handoffCount++;
            }
            if (message.getRetrievalCacheHit() != null && message.getRetrievalCacheHit() == 1) {
                cacheHitCount++;
            }
            inputTokens += message.getInputTokens() == null ? 0L : message.getInputTokens();
            outputTokens += message.getOutputTokens() == null ? 0L : message.getOutputTokens();
            addLatency(latencies, message.getLatencyMs());
            addLatency(retrievalLatencies, message.getRetrievalLatencyMs());
            addLatency(generationLatencies, message.getGenerationLatencyMs());
        }

        private void addLatency(List<Long> target, Long value) {
            if (value != null && value >= 0) {
                target.add(value);
            }
        }
    }


    private List<OpsHealthIssueVO> buildHealthIssues(Double failureRate,
                                                     long staleProcessingTaskCount,
                                                     Long p95DurationMs,
                                                     DeadLetterMetricVO deadLetterMetrics,
                                                     TaskGovernanceMetricVO governanceMetrics) {
        List<OpsHealthIssueVO> issues = new ArrayList<>();
        if (failureRate >= 10D) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("HIGH_FAILURE_RATE")
                    .severity(failureRate >= 30D ? "CRITICAL" : "WARNING")
                    .title("任务失败率偏高")
                    .description("当前统计窗口任务失败率达到 " + failureRate + "%，建议优先排查失败任务错误原因。")
                    .actionText("查看失败任务")
                    .actionUrl("/admin/parse-tasks?status=FAILED&source=ops-health")
                    .build());
        }
        if (staleProcessingTaskCount > 0) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("STALE_PROCESSING_TASK")
                    .severity("WARNING")
                    .title("存在疑似卡住任务")
                    .description("有 " + staleProcessingTaskCount + " 个任务长时间处于处理中状态，建议检查 worker 心跳或执行人工治理。")
                    .actionText("查看处理中任务")
                    .actionUrl("/admin/parse-tasks?status=PROCESSING&source=ops-health")
                    .build());
        }
        if (deadLetterMetrics.getUnresolvedCount() > 0) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("UNRESOLVED_DLQ")
                    .severity(deadLetterMetrics.getManualRequiredCount() > 0 ? "CRITICAL" : "WARNING")
                    .title("DLQ 存在未解决记录")
                    .description("当前仍有 " + deadLetterMetrics.getUnresolvedCount() + " 条死信未解决，其中 " + deadLetterMetrics.getManualRequiredCount() + " 条需要人工介入。")
                    .actionText("进入死信治理")
                    .actionUrl("/admin/dead-letters?source=ops-health")
                    .build());
        }
        if (p95DurationMs >= 60000L) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("SLOW_TASK_P95")
                    .severity("NOTICE")
                    .title("任务 P95 耗时偏高")
                    .description("当前任务 P95 耗时达到 " + p95DurationMs + " ms，建议关注文档解析、向量化或外部依赖耗时。")
                    .actionText("查看解析任务")
                    .actionUrl("/admin/parse-tasks?source=ops-health")
                    .build());
        }
        if (governanceMetrics.getStaleAttemptCompletionSkippedCount() > 0) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("STALE_ATTEMPT_SKIPPED")
                    .severity("NOTICE")
                    .title("存在过期 attempt 被拦截")
                    .description("近 " + governanceMetrics.getStaleAttemptCompletionSkippedCount() + " 次旧 worker 完成结果被拦截，说明幂等保护正在生效，建议关注 worker 超时或重试频率。")
                    .actionText("查看解析任务")
                    .actionUrl("/admin/parse-tasks?source=ops-health")
                    .build());
        }
        if (governanceMetrics.getStartupStaleTaskRecoveredCount() > 0) {
            issues.add(OpsHealthIssueVO.builder()
                    .code("STARTUP_RECOVERY_OCCURRED")
                    .severity("NOTICE")
                    .title("启动恢复曾接管卡住任务")
                    .description("近 " + governanceMetrics.getStartupStaleTaskRecoveredCount() + " 个卡住任务在应用启动时被恢复并重新投递。")
                    .actionText("查看处理中任务")
                    .actionUrl("/admin/parse-tasks?source=ops-health")
                    .build());
        }
        return issues;
    }

    private Integer calculateHealthScore(Double failureRate,
                                         long staleProcessingTaskCount,
                                         Long p95DurationMs,
                                         DeadLetterMetricVO deadLetterMetrics,
                                         TaskGovernanceMetricVO governanceMetrics) {
        int score = 100;
        score -= Math.min(40, (int) Math.round(failureRate));
        score -= Math.min(25, (int) staleProcessingTaskCount * 8);
        score -= Math.min(25, deadLetterMetrics.getUnresolvedCount().intValue() * 6);
        score -= Math.min(10, governanceMetrics.getStaleAttemptCompletionSkippedCount().intValue() * 2);
        score -= Math.min(10, governanceMetrics.getStartupRecoverySkippedCount().intValue() * 4);
        if (p95DurationMs >= 60000L) {
            score -= Math.min(15, (int) (p95DurationMs / 60000L) * 3);
        }
        return Math.max(0, score);
    }

    private String resolveHealthLevel(Integer score) {
        if (score >= 90) {
            return "HEALTHY";
        }
        if (score >= 70) {
            return "WARNING";
        }
        return "CRITICAL";
    }

    private String resolveHealthSummary(Integer score, List<OpsHealthIssueVO> issues) {
        if (issues.isEmpty()) {
            return "当前异步任务链路整体健康，失败率、卡住任务和 DLQ 堆积均处于可接受范围。";
        }
        if (score < 70) {
            return "当前异步任务链路存在较高运维风险，建议优先处理高严重级别问题。";
        }
        return "当前异步任务链路存在需要关注的运维风险，建议按风险项逐项治理。";
    }    private int sanitizeDays(Integer days) {
        if (days == null || days < 1) {
            return 7;
        }
        return Math.min(days, 90);
    }

    private long countByStatus(List<ParseTaskEntity> tasks, String status) {
        return tasks.stream().filter(task -> status.equals(task.getStatus())).count();
    }

    private long countStaleProcessingTasks(List<ParseTaskEntity> tasks) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(messagingProperties.getStaleProcessingMinutes());
        return tasks.stream()
                .filter(task -> ParseTaskStateMachine.PROCESSING.equals(task.getStatus()))
                .filter(task -> task.getStartedAt() != null && task.getStartedAt().isBefore(staleBefore))
                .count();
    }

    private List<TaskStatusMetricVO> buildStatusMetrics(List<ParseTaskEntity> tasks) {
        Map<String, Long> counter = new LinkedHashMap<>();
        counter.put(ParseTaskStateMachine.PENDING, 0L);
        counter.put(ParseTaskStateMachine.PROCESSING, 0L);
        counter.put(ParseTaskStateMachine.SUCCESS, 0L);
        counter.put(ParseTaskStateMachine.FAILED, 0L);
        for (ParseTaskEntity task : tasks) {
            counter.merge(task.getStatus(), 1L, Long::sum);
        }
        return counter.entrySet().stream()
                .map(entry -> TaskStatusMetricVO.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private List<TaskTypeMetricVO> buildTaskTypeMetrics(List<ParseTaskEntity> tasks) {
        Map<String, List<ParseTaskEntity>> grouped = new LinkedHashMap<>();
        for (ParseTaskEntity task : tasks) {
            grouped.computeIfAbsent(task.getTaskType(), ignored -> new ArrayList<>()).add(task);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareTo)))
                .map(entry -> buildTaskTypeMetric(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TaskTypeMetricVO buildTaskTypeMetric(String taskType, List<ParseTaskEntity> tasks) {
        long total = tasks.size();
        long success = countByStatus(tasks, ParseTaskStateMachine.SUCCESS);
        long failed = countByStatus(tasks, ParseTaskStateMachine.FAILED);
        long processing = countByStatus(tasks, ParseTaskStateMachine.PROCESSING);
        List<Long> durations = tasks.stream()
                .map(ParseTaskEntity::getDurationMs)
                .filter(Objects::nonNull)
                .filter(duration -> duration >= 0)
                .sorted()
                .toList();
        return TaskTypeMetricVO.builder()
                .taskType(taskType)
                .totalCount(total)
                .successCount(success)
                .failedCount(failed)
                .processingCount(processing)
                .failureRate(rate(failed, total))
                .successRate(rate(success, total))
                .avgDurationMs(avg(durations))
                .p95DurationMs(percentile(durations, 0.95D))
                .build();
    }

    private DeadLetterMetricVO buildDeadLetterMetrics(List<DeadLetterMessageEntity> deadLetters) {
        long total = deadLetters.size();
        long resolved = deadLetters.stream()
                .filter(item -> item.getResolvedAt() != null || DeadLetterMessageService.REPLAY_STATUS_RESOLVED.equals(item.getReplayStatus()))
                .count();
        long ready = countDeadLettersByReplayStatus(deadLetters, DeadLetterMessageService.REPLAY_STATUS_READY);
        long manualRequired = countDeadLettersByReplayStatus(deadLetters, DeadLetterMessageService.REPLAY_STATUS_MANUAL_REQUIRED);
        long autoReplayed = countDeadLettersByReplayStatus(deadLetters, DeadLetterMessageService.REPLAY_STATUS_AUTO_REPLAYED);
        long manualReplayed = countDeadLettersByReplayStatus(deadLetters, DeadLetterMessageService.REPLAY_STATUS_MANUAL_REPLAYED);
        return DeadLetterMetricVO.builder()
                .totalCount(total)
                .unresolvedCount(total - resolved)
                .readyCount(ready)
                .manualRequiredCount(manualRequired)
                .replayedCount(autoReplayed + manualReplayed)
                .resolvedCount(resolved)
                .build();
    }

    private TaskGovernanceMetricVO buildGovernanceMetrics(List<ParseTaskGovernanceEventEntity> events) {
        return TaskGovernanceMetricVO.builder()
                .totalEventCount((long) events.size())
                .duplicateConsumptionSkippedCount(countGovernanceEventsByType(events, ParseTaskGovernanceEventService.DUPLICATE_CONSUMPTION_SKIPPED))
                .nonPendingMessageSkippedCount(countGovernanceEventsByType(events, ParseTaskGovernanceEventService.NON_PENDING_MESSAGE_SKIPPED))
                .staleAttemptCompletionSkippedCount(countGovernanceEventsByType(events, ParseTaskGovernanceEventService.STALE_ATTEMPT_COMPLETION_SKIPPED))
                .startupStaleTaskRecoveredCount(countGovernanceEventsByType(events, ParseTaskGovernanceEventService.STARTUP_STALE_TASK_RECOVERED))
                .startupRecoverySkippedCount(countGovernanceEventsByType(events, ParseTaskGovernanceEventService.STARTUP_RECOVERY_SKIPPED))
                .build();
    }

    private long countGovernanceEventsByType(List<ParseTaskGovernanceEventEntity> events, String eventType) {
        return events.stream().filter(event -> eventType.equals(event.getEventType())).count();
    }

    private long countDeadLettersByReplayStatus(List<DeadLetterMessageEntity> deadLetters, String replayStatus) {
        return deadLetters.stream().filter(item -> replayStatus.equals(item.getReplayStatus())).count();
    }

    private Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return Math.round(numerator * 10000D / denominator) / 100D;
    }

    private Long avg(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (Long value : values) {
            sum += value;
        }
        return Math.round(sum * 1.0D / values.size());
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}

package com.knowflow.ops.config;

import com.knowflow.ops.service.OpsMetricsService;
import com.knowflow.ops.vo.TaskOpsOverviewVO;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TaskMetricsBinder {

    private static final int DEFAULT_WINDOW_DAYS = 7;

    private final OpsMetricsService opsMetricsService;

    public TaskMetricsBinder(MeterRegistry meterRegistry, OpsMetricsService opsMetricsService) {
        this.opsMetricsService = opsMetricsService;
        Gauge.builder("knowflow.parse.task.failure.rate", this, TaskMetricsBinder::failureRate)
                .description("Parse task failure rate in the recent observation window")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.avg.duration.ms", this, TaskMetricsBinder::avgDurationMs)
                .description("Average parse task duration in milliseconds")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.p95.duration.ms", this, TaskMetricsBinder::p95DurationMs)
                .description("P95 parse task duration in milliseconds")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.stale.processing.count", this, TaskMetricsBinder::staleProcessingCount)
                .description("Stale processing parse task count")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.dlq.unresolved.count", this, TaskMetricsBinder::unresolvedDeadLetterCount)
                .description("Unresolved dead letter message count")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.duplicate.skipped.count", this, TaskMetricsBinder::duplicateSkippedCount)
                .description("Duplicate parse task consumption skipped count")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.stale.attempt.skipped.count", this, TaskMetricsBinder::staleAttemptSkippedCount)
                .description("Stale parse task attempt completion skipped count")
                .register(meterRegistry);
        Gauge.builder("knowflow.parse.task.startup.recovered.count", this, TaskMetricsBinder::startupRecoveredCount)
                .description("Stale processing parse task recovered during startup")
                .register(meterRegistry);
    }

    private double failureRate() {
        return snapshot().getFailureRate();
    }

    private double avgDurationMs() {
        return snapshot().getAvgDurationMs();
    }

    private double p95DurationMs() {
        return snapshot().getP95DurationMs();
    }

    private double staleProcessingCount() {
        return snapshot().getStaleProcessingTaskCount();
    }

    private double unresolvedDeadLetterCount() {
        return snapshot().getDeadLetterMetrics().getUnresolvedCount();
    }

    private double duplicateSkippedCount() {
        TaskOpsOverviewVO overview = snapshot();
        return overview.getGovernanceMetrics().getDuplicateConsumptionSkippedCount()
                + overview.getGovernanceMetrics().getNonPendingMessageSkippedCount();
    }

    private double staleAttemptSkippedCount() {
        return snapshot().getGovernanceMetrics().getStaleAttemptCompletionSkippedCount();
    }

    private double startupRecoveredCount() {
        return snapshot().getGovernanceMetrics().getStartupStaleTaskRecoveredCount();
    }

    private TaskOpsOverviewVO snapshot() {
        return opsMetricsService.globalTaskOverview(DEFAULT_WINDOW_DAYS);
    }
}

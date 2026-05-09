package com.knowflow.parser.runtime;

import com.knowflow.parser.config.ParseTaskMessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "knowflow.parser.messaging.mode", havingValue = "rabbit")
public class RedisParseTaskRuntimeTracker implements ParseTaskRuntimeTracker {

    private static final String FIELD_TRANSPORT = "transport";
    private static final String FIELD_QUEUE_STATUS = "queueStatus";
    private static final String FIELD_WORKER_ID = "workerId";
    private static final String FIELD_QUEUED_AT = "queuedAt";
    private static final String FIELD_DEQUEUED_AT = "dequeuedAt";
    private static final String FIELD_LAST_HEARTBEAT_AT = "lastHeartbeatAt";
    private static final String FIELD_QUEUE_LATENCY_MS = "queueLatencyMs";
    private static final String FIELD_CHUNK_COUNT = "chunkCount";
    private static final String FIELD_DURATION_MS = "durationMs";
    private static final String FIELD_ERROR_MESSAGE = "errorMessage";

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_DISPATCH_FAILED = "DISPATCH_FAILED";
    private static final String STATUS_CONSUMING = "CONSUMING";
    private static final String STATUS_PARSING = "PARSING";
    private static final String STATUS_PERSISTING = "PERSISTING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ParseTaskMessagingProperties properties;

    public RedisParseTaskRuntimeTracker(StringRedisTemplate stringRedisTemplate,
                                        ParseTaskMessagingProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void markQueued(Long taskId, String transport) {
        Map<String, String> values = new HashMap<>();
        values.put(FIELD_TRANSPORT, transport);
        values.put(FIELD_QUEUE_STATUS, STATUS_QUEUED);
        values.put(FIELD_QUEUED_AT, now());
        values.put(FIELD_LAST_HEARTBEAT_AT, now());
        putAll(taskId, values);
        deleteFields(taskId, FIELD_WORKER_ID, FIELD_DEQUEUED_AT, FIELD_QUEUE_LATENCY_MS, FIELD_CHUNK_COUNT, FIELD_DURATION_MS, FIELD_ERROR_MESSAGE);
    }

    @Override
    public void markDispatchFailed(Long taskId, String errorMessage) {
        Map<String, String> values = new HashMap<>();
        values.put(FIELD_QUEUE_STATUS, STATUS_DISPATCH_FAILED);
        values.put(FIELD_LAST_HEARTBEAT_AT, now());
        putAll(taskId, values);
        putOptional(taskId, FIELD_ERROR_MESSAGE, truncate(errorMessage));
    }

    @Override
    public boolean tryAcquireWorkerLock(Long taskId, String workerId) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey(taskId),
                workerId,
                Duration.ofSeconds(properties.getWorkerLockSeconds())
        );
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseWorkerLock(Long taskId, String workerId) {
        stringRedisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                List.of(lockKey(taskId)),
                workerId
        );
    }

    @Override
    public void forceReleaseWorkerLock(Long taskId) {
        stringRedisTemplate.delete(lockKey(taskId));
    }

    @Override
    public void markDequeued(Long taskId, String workerId) {
        String queuedAtValue = stringRedisTemplate.opsForHash().get(runtimeKey(taskId), FIELD_QUEUED_AT) instanceof String value
                ? value
                : null;

        Map<String, String> values = new HashMap<>();
        values.put(FIELD_QUEUE_STATUS, STATUS_CONSUMING);
        values.put(FIELD_WORKER_ID, workerId);
        values.put(FIELD_DEQUEUED_AT, now());
        values.put(FIELD_LAST_HEARTBEAT_AT, now());
        if (StringUtils.hasText(queuedAtValue)) {
            LocalDateTime queuedAt = parseTime(queuedAtValue);
            if (queuedAt != null) {
                values.put(FIELD_QUEUE_LATENCY_MS, String.valueOf(Duration.between(queuedAt, LocalDateTime.now()).toMillis()));
            }
        }
        putAll(taskId, values);
    }

    @Override
    public void markParsing(Long taskId, String workerId) {
        markStage(taskId, STATUS_PARSING, workerId, null, null);
    }

    @Override
    public void markPersisting(Long taskId, String workerId, int chunkCount) {
        markStage(taskId, STATUS_PERSISTING, workerId, chunkCount, null);
    }

    @Override
    public void markSuccess(Long taskId, String workerId, int chunkCount, long durationMs) {
        markStage(taskId, STATUS_SUCCESS, workerId, chunkCount, durationMs);
        deleteFields(taskId, FIELD_ERROR_MESSAGE);
    }

    @Override
    public void markFailure(Long taskId, String workerId, String errorMessage) {
        markStage(taskId, STATUS_FAILED, workerId, null, null);
        putOptional(taskId, FIELD_ERROR_MESSAGE, truncate(errorMessage));
    }

    @Override
    public ParseTaskRuntimeSnapshot getSnapshot(Long taskId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(runtimeKey(taskId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return ParseTaskRuntimeSnapshot.builder()
                .transport(asString(values.get(FIELD_TRANSPORT)))
                .queueStatus(asString(values.get(FIELD_QUEUE_STATUS)))
                .workerId(asString(values.get(FIELD_WORKER_ID)))
                .queuedAt(parseTime(asString(values.get(FIELD_QUEUED_AT))))
                .dequeuedAt(parseTime(asString(values.get(FIELD_DEQUEUED_AT))))
                .lastHeartbeatAt(parseTime(asString(values.get(FIELD_LAST_HEARTBEAT_AT))))
                .queueLatencyMs(parseLong(asString(values.get(FIELD_QUEUE_LATENCY_MS))))
                .chunkCount(parseInteger(asString(values.get(FIELD_CHUNK_COUNT))))
                .durationMs(parseLong(asString(values.get(FIELD_DURATION_MS))))
                .errorMessage(asString(values.get(FIELD_ERROR_MESSAGE)))
                .build();
    }

    @Override
    public void clearRuntime(Long taskId) {
        List<String> keys = new ArrayList<>();
        keys.add(runtimeKey(taskId));
        keys.add(lockKey(taskId));
        stringRedisTemplate.delete(keys);
    }

    private void markStage(Long taskId, String status, String workerId, Integer chunkCount, Long durationMs) {
        Map<String, String> values = new HashMap<>();
        values.put(FIELD_QUEUE_STATUS, status);
        values.put(FIELD_WORKER_ID, workerId);
        values.put(FIELD_LAST_HEARTBEAT_AT, now());
        if (chunkCount != null) {
            values.put(FIELD_CHUNK_COUNT, String.valueOf(chunkCount));
        }
        if (durationMs != null) {
            values.put(FIELD_DURATION_MS, String.valueOf(durationMs));
        }
        putAll(taskId, values);
    }

    private void putAll(Long taskId, Map<String, String> values) {
        stringRedisTemplate.opsForHash().putAll(runtimeKey(taskId), values);
        touch(taskId);
    }

    private void deleteFields(Long taskId, String... fields) {
        stringRedisTemplate.opsForHash().delete(runtimeKey(taskId), (Object[]) fields);
        touch(taskId);
    }

    private void putOptional(Long taskId, String field, String value) {
        if (StringUtils.hasText(value)) {
            stringRedisTemplate.opsForHash().put(runtimeKey(taskId), field, value);
        } else {
            stringRedisTemplate.opsForHash().delete(runtimeKey(taskId), field);
        }
        touch(taskId);
    }

    private void touch(Long taskId) {
        stringRedisTemplate.expire(runtimeKey(taskId), Duration.ofHours(properties.getRuntimeCacheTtlHours()));
    }

    private String runtimeKey(Long taskId) {
        return "knowflow:parse-task:%d:runtime".formatted(taskId);
    }

    private String lockKey(Long taskId) {
        return "knowflow:parse-task:%d:lock".formatted(taskId);
    }

    private String now() {
        return LocalDateTime.now().toString();
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

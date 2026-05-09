# 运维可观测性设计

## 1. 目标

KnowFlow 的文档解析、向量索引、DLQ 治理都是异步链路。异步系统如果只有“页面能点”，但没有指标，就很难回答这些问题：

- 最近任务失败率是多少？
- 平均解析耗时和 P95 耗时是多少？
- 有没有长时间卡在 `PROCESSING` 的任务？
- DLQ 里还有多少未处理消息？
- 哪类任务更容易失败？
- 重复消息、旧 worker 晚返回、启动恢复这些“被系统兜住的问题”有没有发生？

所以本次补了运维统计接口和 Actuator 指标，让项目更接近生产系统。

## 2. 管理端统计接口

接口：

```http
GET /api/v1/admin/ops/tasks/overview?days=7
```

权限：

- `TENANT_ADMIN`
- `KNOWLEDGE_OPERATOR`

返回核心字段：

| 字段 | 含义 |
|---|---|
| `totalTaskCount` | 最近 N 天任务总数 |
| `successTaskCount` | 成功任务数 |
| `failedTaskCount` | 失败任务数 |
| `processingTaskCount` | 执行中任务数 |
| `pendingTaskCount` | 排队中任务数 |
| `staleProcessingTaskCount` | 超过阈值仍在处理的疑似卡死任务数 |
| `failureRate` | 失败率 |
| `successRate` | 成功率 |
| `avgDurationMs` | 平均耗时 |
| `p95DurationMs` | P95 耗时 |
| `statusMetrics` | 按状态聚合 |
| `taskTypeMetrics` | 按任务类型聚合 |
| `deadLetterMetrics` | DLQ 聚合指标 |
| `governanceMetrics` | 幂等治理事件指标 |

`governanceMetrics` 用于统计异步链路里的防御性事件：

| 字段 | 含义 |
|---|---|
| `duplicateConsumptionSkippedCount` | worker 锁冲突导致的重复消费跳过次数 |
| `nonPendingMessageSkippedCount` | 任务已不再是 `PENDING` 时旧消息被跳过次数 |
| `staleAttemptCompletionSkippedCount` | 旧 attempt 晚返回后完成结果被拦截次数 |
| `startupStaleTaskRecoveredCount` | 应用启动时恢复卡住任务次数 |
| `startupRecoverySkippedCount` | 启动恢复扫描后状态已变化、因此跳过恢复次数 |

示例用途：

- 后台运维页展示解析任务健康度
- 面试时展示“我不仅做了功能，还做了治理和观测”
- 排查任务失败率升高、DLQ 堆积、处理耗时变长等问题

## 3. Actuator 指标

项目通过 Micrometer 注册了这些 Gauge：

| 指标名 | 含义 |
|---|---|
| `knowflow.parse.task.failure.rate` | 最近 7 天解析任务失败率 |
| `knowflow.parse.task.avg.duration.ms` | 最近 7 天平均任务耗时 |
| `knowflow.parse.task.p95.duration.ms` | 最近 7 天 P95 任务耗时 |
| `knowflow.parse.task.stale.processing.count` | 疑似卡死任务数量 |
| `knowflow.parse.task.dlq.unresolved.count` | 未解决 DLQ 数量 |
| `knowflow.parse.task.duplicate.skipped.count` | 重复消费和非待处理消息跳过数量 |
| `knowflow.parse.task.stale.attempt.skipped.count` | 过期 attempt 完成结果被拦截数量 |
| `knowflow.parse.task.startup.recovered.count` | 启动恢复接管卡住任务数量 |

默认 `application.yml` 只暴露了 `health` 和 `info`。如果要在本地查看 Actuator metrics，可以把暴露项改成：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

然后访问：

```text
/actuator/metrics/knowflow.parse.task.failure.rate
/actuator/metrics/knowflow.parse.task.avg.duration.ms
/actuator/metrics/knowflow.parse.task.p95.duration.ms
/actuator/metrics/knowflow.parse.task.stale.processing.count
/actuator/metrics/knowflow.parse.task.dlq.unresolved.count
/actuator/metrics/knowflow.parse.task.duplicate.skipped.count
/actuator/metrics/knowflow.parse.task.stale.attempt.skipped.count
/actuator/metrics/knowflow.parse.task.startup.recovered.count
```

## 4. 面试讲法

推荐这样讲：

> 异步任务不是只要能消费就可以，真实系统还要能观测。我给解析任务链路补了运维统计接口，按租户统计最近 N 天任务总数、成功数、失败数、失败率、平均耗时、P95 耗时、卡住的 PROCESSING 任务和 DLQ 数量。同时，我把重复消息跳过、旧 attempt 完成结果被拦截、启动恢复接管卡住任务这些防御性动作沉淀成治理事件，运维页和 Micrometer 指标都能看到。后续可以接 Prometheus 或 Grafana 做监控看板。

如果面试官追问“为什么要看 P95 而不是只看平均值”，可以回答：

> 平均值容易掩盖长尾问题。比如大部分小文档解析很快，但少数大文档或异常文档可能耗时很长。P95 能更好反映用户实际感受到的长尾延迟。

如果面试官追问“如何发现任务卡死”，可以回答：

> 我用 `startedAt` 和配置里的 `staleProcessingMinutes` 判断。如果任务处于 PROCESSING，并且开始时间早于阈值，就统计为 stale processing task。后续可以加定时扫描，把这类任务自动标失败或进入人工治理。

## 5. 后续演进

- 接入 Prometheus registry，暴露 `/actuator/prometheus`
- 管理台增加“运维健康”页面
- 对失败率、P95、DLQ 堆积设置告警阈值
- 增加任务耗时趋势图
- 增加 worker 实例维度指标
- 增加治理事件明细页，支持按任务、worker、事件类型钻取

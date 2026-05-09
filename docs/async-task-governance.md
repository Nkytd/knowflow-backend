# 异步解析任务治理设计

## 1. 为什么要做任务治理

文档解析和向量索引都属于耗时操作，如果直接放在上传接口里同步执行，会带来几个问题：

- 上传接口响应慢
- 大文件解析容易超时
- 解析失败不容易追踪
- 重试和人工回放没有统一入口
- 重复消息可能导致状态错乱

所以 KnowFlow 把文档处理拆成异步任务链路：

```text
文档上传 -> 创建 parse_task -> 投递消息 -> Worker 消费 -> 状态推进 -> 失败治理 / 成功后继续索引
```

## 2. 任务类型

当前解析链路有两类任务：

| 任务类型 | 说明 | 产物 |
|---|---|---|
| `PARSE` | 读取文档内容并切分 chunk | `knowledge_chunk` |
| `INDEX_VECTOR` | 对 chunk 做向量化并建立索引 | `knowledge_chunk_index` |

这样拆分的好处是：

- 解析和索引可以独立失败、独立重试
- 文档状态可以区分 `parseStatus` 和 `indexStatus`
- 后续可以把索引任务迁移到独立 worker 服务

## 3. 状态机设计

任务状态只允许按固定路径推进：

```text
PENDING -> PROCESSING -> SUCCESS
PENDING -> PROCESSING -> FAILED
FAILED  -> PENDING     -> PROCESSING -> SUCCESS / FAILED
```

状态含义：

| 状态 | 含义 | 是否终态 | 是否可重试 |
|---|---|---:|---:|
| `PENDING` | 已创建，等待消费 | 否 | 否 |
| `PROCESSING` | Worker 正在执行 | 否 | 否 |
| `SUCCESS` | 执行成功 | 是 | 否 |
| `FAILED` | 执行失败 | 是 | 是 |

核心约束集中在 `ParseTaskStateMachine`：

- 只有 `PENDING` 可以开始执行
- 只有 `PROCESSING` 可以完成成功或失败
- 只有 `FAILED` 可以被重试
- `SUCCESS` 不允许重试，避免重复消费破坏结果

## 4. 幂等设计

异步消息系统必须考虑重复投递、重复消费、超时重试等情况。

当前项目用了两层保护：

### 4.1 数据库状态 CAS

Worker 开始执行任务时，会带着状态条件更新：

```text
where id = ? and status = 'PENDING'
```

只有更新成功的 worker 才能继续处理。这样即使同一个任务消息被投递两次，也只有第一次能把任务从 `PENDING` 改成 `PROCESSING`。

### 4.2 Redis Worker Lock

在 RabbitMQ 模式下，Worker 处理前会尝试获取运行时锁：

```text
knowflow:parse-task:{taskId}:lock
```

如果锁已存在，说明已有 worker 正在处理，重复消费会被跳过。

### 4.3 Attempt 级别完成栅栏

只做 `PENDING -> PROCESSING` 的 CAS 还不够。真实异步系统里还可能出现这种情况：

```text
旧 worker 执行很慢 -> 任务被恢复为 PENDING -> 新 worker 重新开始 -> 旧 worker 最后才返回失败
```

如果旧 worker 的失败结果被写入数据库，就会把新 attempt 的状态污染掉。

KnowFlow 现在把每次执行 attempt 的 `started_at` 当作完成栅栏：

```text
where id = ?
  and status = 'PROCESSING'
  and started_at = 当前 worker 开始执行时看到的 started_at
```

只有仍然处于同一次执行 attempt 的 worker 才能写入 `SUCCESS` 或 `FAILED`。如果任务已经被恢复、重试或重新开始，旧 worker 的完成结果会被直接忽略。

这层保护让 Redis 锁从“唯一正确性来源”变成“运行时优化手段”，最终一致性仍由数据库状态机兜底。

同时，这些被兜住的异常不会静默消失。系统会写入 `parse_task_governance_event`，记录重复消费跳过、非待处理消息跳过、过期 attempt 完成结果被拦截、启动恢复接管卡住任务等事件。运维健康页会聚合这些事件，用来判断异步链路是否存在消息重复、worker 超时或恢复频繁的问题。

## 5. 重复消息保护

一个典型问题是：

```text
任务已经 SUCCESS，但 RabbitMQ 又投递了一条旧消息
```

如果没有状态机保护，旧消息进入 worker 后可能会抛异常，并错误地把成功任务改成失败。

现在的处理策略是：

- `startProcessing()` 发现状态不是 `PENDING` 时返回 `null`
- worker 收到 `null` 后直接结束
- catch 分支只有在任务仍是 `PROCESSING` 时才允许写失败
- 成功/失败落库时必须匹配本次 attempt 的 `started_at`
- 已经 `SUCCESS` 或 `FAILED` 的任务不会被旧消息覆盖

这让任务处理具备基本幂等性。

启动恢复链路也使用相同思路：扫描长时间卡在 `PROCESSING` 的任务时，恢复更新会同时校验 `id + status + started_at`。如果扫描之后任务已经被别的 worker 完成，就不会被误改回 `PENDING`。

## 6. 失败治理链路

失败任务会保留：

- `retry_count`
- `error_message`
- `started_at`
- `finished_at`
- `duration_ms`

RabbitMQ 模式下，如果任务执行失败，会进入 DLQ 治理链路：

```text
Worker 失败 -> RabbitMQ DLQ -> dead_letter_message -> 治理页查看 -> 重试 / 回放
```

治理页能按任务、文档维度联动查看失败原因，方便定位问题。

## 7. 面试讲法

推荐你这样讲：

> 我没有把文档解析直接做成同步接口，而是拆成 parse task，通过 RabbitMQ 异步消费。任务状态使用 PENDING、PROCESSING、SUCCESS、FAILED 四个状态，并用状态机约束流转。Worker 开始执行时用数据库条件更新做 CAS，RabbitMQ 模式下再结合 Redis 锁避免重复消费。只有 FAILED 任务允许重试，SUCCESS 任务不会被旧消息覆盖，这样可以避免重复消息导致状态回滚或错误失败。

如果面试官继续问“如何保证消息不重复处理”，可以回答：

> 消息队列一般只能做到至少一次投递，所以消费者侧必须做幂等。我这里做了三层：第一层是 Redis worker lock，避免同一时刻多个 worker 处理同一任务；第二层是数据库状态 CAS，只有 PENDING 才能改 PROCESSING；第三层是 attempt 完成栅栏，完成时必须同时匹配 PROCESSING 和 started_at。即使锁失效、消息重复、旧 worker 晚返回，数据库状态也能兜底。

如果面试官问“失败后怎么恢复”，可以回答：

> 失败后任务会进入 FAILED，并记录错误原因和耗时。RabbitMQ 模式下失败消息会进 DLQ，后台治理页可以查看失败原因并手动回放。重试时只允许 FAILED -> PENDING，重新投递消息后由 worker 再次消费。

## 8. 后续演进

后续可以继续补强：

- 增加最大重试次数和指数退避
- 增加任务超时扫描，把长时间 `PROCESSING` 的任务标记为失败
- 增加 Prometheus 指标：任务耗时、失败率、DLQ 数量、重试成功率
- 引入独立 worker 服务，后端 API 和解析 worker 分开部署
- 增加治理事件明细页，支持按 worker、任务和事件类型钻取问题来源

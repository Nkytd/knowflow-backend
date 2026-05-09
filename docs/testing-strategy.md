# KnowFlow 测试策略与验证矩阵

## 1. 目标

这份文档用于说明 KnowFlow 当前如何证明核心链路是可靠的。

项目的测试目标不是单纯追求覆盖率数字，而是围绕企业级知识服务平台最容易出问题、也最容易被面试追问的边界建立证据链：

- 鉴权是否真的拦住匿名请求。
- RBAC 是否真的防止越权访问。
- 多租户数据是否不会串租户。
- 异步解析任务是否能抵抗重复消息、旧 worker 回写和非法状态流转。
- 文档解析是否能保留 Markdown 标题层级和 FAQ 问答对语义。
- 知识库、问答、工单、知识回流这些业务闭环是否能跑通。
- RAG 检索质量是否能被固定样本持续评估。
- dev 容器环境是否能通过 smoke test 证明真实依赖可用。

## 2. 当前测试矩阵

| 测试文件 / 文档 | 验证重点 | 对应风险 |
|---|---|---|
| `SecurityBoundaryIntegrationTests` | 匿名 API 拒绝、平台接口 RBAC、租户角色越权、跨租户知识库隐藏 | 只靠前端隐藏菜单、接口越权、租户数据泄漏 |
| `AuthIntegrationTests` | 登录、JWT、当前用户信息、用户/角色管理基础链路 | 鉴权链路失效、用户上下文错误 |
| `CoreModulesIntegrationTests` | 知识库、文档、解析任务、DLQ、QA、工单、草稿、看板、审计等核心业务闭环 | 只完成孤立 CRUD，核心业务链路断裂 |
| `ParseTaskAttemptIdempotencyIntegrationTests` | 状态机约束、CAS 开始执行、attempt 完成栅栏、启动恢复 | 重复消费、旧 worker 回写、成功任务被旧消息覆盖 |
| `SimpleDocumentParsingServiceTests` | Markdown 标题上下文保留、FAQ 问答对语义切块 | 文档切片丢失章节语境、问题和答案被拆散导致召回证据质量下降 |
| `QaRetrievalIntegrationTests` | 问答召回、来源展示、未命中兜底 | RAG 只有生成没有证据、低置信度误答 |
| `RetrievalEvaluationIntegrationTests` | 检索评估配置和结果落库 | 无法证明检索策略调整是否变好 |
| `AgentToolIntegrationTests` | Agent 工具调用与业务接口协作 | AI Agent 只停留在演示，不具备业务动作 |
| `docs/SMOKE_TEST.md` | dev 容器形态的健康检查、上传、解析、索引、问答 | 单测能过但真实 MySQL/Redis/RabbitMQ/MinIO 环境不可用 |
| `docs/retrieval-evaluation.md` | 固定评估集、指标解释、报告输出 | 检索优化只能靠主观感觉 |
| `docs/async-task-governance.md` | 异步任务状态机、幂等和失败治理设计 | 队列至少一次投递下的数据一致性风险 |

## 3. 安全边界验证

`SecurityBoundaryIntegrationTests` 是第二组任务新增的核心测试，专门验证“不能只相信前端菜单”的后端安全边界。

当前覆盖：

- 未携带 token 访问 `/api/v1/admin/**` 和 `/api/v1/app/**` 会返回 `40101`。
- 租户管理员访问 `/api/v1/platform/tenants` 会返回 `40301`。
- 知识运营角色访问工单管理接口会返回 `40301`。
- 租户管理员不能给用户分配平台级 `SUPER_ADMIN` 角色。
- 租户 1 用户不能查看或搜索租户 2 的知识库数据。

本轮测试还暴露并修复了一个真实问题：Controller 上的 `@PreAuthorize` 拒绝访问时，异常曾被全局 `Exception` 兜底捕获，导致 HTTP 状态是 `200` 且业务码是 `50001`。现在 `GlobalExceptionHandler` 已显式处理 `AccessDeniedException` 和 `AuthorizationDeniedException`，权限拒绝会返回 HTTP `403` 与业务码 `40301`。

## 4. 异步任务可靠性验证

文档解析链路使用 RabbitMQ 承载异步任务，因此消费者侧必须按“至少一次投递”设计。

当前验证重点：

- 任务只能沿 `PENDING -> PROCESSING -> SUCCESS/FAILED` 推进。
- 只有 `FAILED` 任务允许重试回到 `PENDING`。
- Worker 开始执行时通过数据库条件更新做 CAS。
- 完成任务时校验本次 attempt 的 `started_at`，旧 worker 晚返回不会覆盖新 attempt。
- 启动恢复只接管仍然卡在同一 attempt 的 `PROCESSING` 任务。

这部分测试对应 `ParseTaskAttemptIdempotencyIntegrationTests`，设计说明沉淀在 `docs/async-task-governance.md`。

## 5. 文档解析质量验证

`SimpleDocumentParsingServiceTests` 用来验证文档进入知识库前的切片质量。

当前覆盖：

- Markdown 文档按 `#` 标题层级形成 section，chunk 中保留 `一级标题 > 二级标题` 这样的上下文路径。
- FAQ 文档按 `Q:/A:`、`Question:/Answer:`、`问:/答:`、`问题:/答案:` 问答对切块，避免答案脱离问题独立入库。

这组测试对应 P2 的“文档解析能力增强”，它不替代端到端解析任务测试，而是用更快的单元测试固定解析器本身的语义边界。

## 6. 业务闭环验证

`CoreModulesIntegrationTests` 用来证明项目不是一组分散页面，而是能跑通企业知识服务闭环：

```text
知识库 / 文档 -> 解析任务 -> 知识切片 -> 智能问答 -> 未命中转工单 -> 工单解决 -> 知识草稿 -> 审核回流 -> 运营看板
```

这条链路可以在面试或演示中作为主线：先说明业务价值，再说明每一步背后的后端模块、数据表和工程治理点。

## 7. RAG 检索评估

RAG 链路除了能返回答案，还需要证明召回和兜底策略是可持续优化的。

当前项目提供：

- 固定评估样本：`docs/retrieval-eval-cases.sample.json`。
- 评估脚本：`scripts/evaluate-retrieval.ps1`。
- 说明文档：`docs/retrieval-evaluation.md`。
- 后端评估能力测试：`RetrievalEvaluationIntegrationTests`。

建议后续把样本扩展到 20 到 50 条，并保留 baseline 与优化后的报告，用于展示 Top1 来源准确率、证据关键词覆盖率、无命中识别准确率等指标变化。

## 8. 推荐验证命令

日常修改后建议至少运行：

```powershell
mvn -q -Dtest=SecurityBoundaryIntegrationTests,ParseTaskAttemptIdempotencyIntegrationTests,SimpleDocumentParsingServiceTests test
```

如果本机没有全局 Maven，可以使用 IDEA bundled Maven：

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=SecurityBoundaryIntegrationTests,ParseTaskAttemptIdempotencyIntegrationTests,SimpleDocumentParsingServiceTests test
```

准备演示前建议运行完整测试：

```powershell
mvn -q test
```

如果要验证真实 dev 容器环境，按 `docs/SMOKE_TEST.md` 运行 smoke test。

## 9. 面试讲法

可以这样概括测试策略：

> 我把测试重点放在企业系统最容易出事故的地方：安全边界、多租户隔离、异步幂等、文档解析质量和业务闭环。安全上有专门的边界测试验证匿名访问、RBAC 越权和跨租户数据隔离；异步链路用状态机、数据库 CAS 和 attempt 栅栏保证重复消息不会污染任务状态；解析器测试固定 Markdown 标题上下文和 FAQ 问答对切块；业务上用集成测试覆盖从知识入库到问答、工单、知识回流的闭环；RAG 质量则用固定样本和评估脚本做可重复回归。

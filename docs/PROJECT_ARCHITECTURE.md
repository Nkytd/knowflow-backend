# KnowFlow 系统架构设计文档

## 1. 项目定位

KnowFlow 是一个面向企业内部知识服务场景的 Java 后端项目。它不是单纯的聊天机器人，也不是普通 CRUD 管理系统，而是把“知识库建设、智能问答、未命中转工单、工单解决后知识回流、运营分析”串成闭环。

面试时可以把它定位成：

- 传统 Java 后端项目：多租户、鉴权、权限、业务建模、数据持久化、接口设计
- 工程化项目：异步任务、失败治理、对象存储、容器化、数据库迁移、测试验证
- AI 应用项目：文档切分、召回检索、低置信度兜底、知识回流、未命中分析

## 2. 总体架构

```text
用户侧工作台 / 管理台页面
        |
Spring MVC Controller
        |
Service 业务编排层
        |
MyBatis-Plus Mapper
        |
MySQL / H2 + Flyway

异步链路：
Document Upload -> Parse Task -> RabbitMQ -> Parser Worker -> Knowledge Chunk -> Vector Index

外部集成：
Redis / RabbitMQ / MinIO / OpenAI Compatible LLM
```

核心分层：

- `controller`：承接 HTTP 请求，做参数接收与权限入口控制
- `service`：承载业务规则、状态推进、跨模块编排
- `mapper`：负责数据访问，基于 MyBatis-Plus 落库查询
- `entity/dto/vo`：区分数据库对象、请求对象、响应对象
- `integration`：隔离 LLM、检索、存储等外部能力
- `worker/messaging`：承载异步消息消费和任务处理

## 3. 核心业务模块

| 模块 | 职责 | 面试可讲点 |
|---|---|---|
| tenant | 租户管理 | SaaS 多租户基础模型 |
| auth | 登录、JWT、RBAC | Spring Security 鉴权链路 |
| knowledge | 知识库、文档 | 企业知识资产建模 |
| parser | 解析任务、DLQ | RabbitMQ 异步任务与失败治理 |
| qa | 会话、问答、来源 | RAG 问答链路 |
| ticket | 工单流转 | 未命中问题人工兜底 |
| backflow | 知识草稿 | 工单解决后的知识回流 |
| dashboard | 运营看板 | 业务价值与数据分析 |
| audit | 操作审计 | 可追溯、可治理能力 |

## 4. 关键业务链路

### 4.1 文档入库与解析链路

1. 管理员上传文档到知识库
2. 文档元数据写入 `knowledge_document`
3. 文件内容写入 MinIO 或本地存储
4. 系统创建 `parse_task`
5. RabbitMQ 投递解析消息
6. Parser Worker 消费任务并切分 chunk
7. chunk 写入 `knowledge_chunk`
8. 生成索引并写入 `knowledge_chunk_index`
9. 推进文档 `parse_status` 和 `index_status`

这条链路体现了：

- 异步解耦：上传接口不阻塞等待长耗时解析
- 状态机推进：`PENDING -> PROCESSING -> SUCCESS/FAILED`
- 失败治理：失败进入 DLQ，支持重试和回放

### 4.2 用户问答链路

1. 用户创建 QA 会话
2. 用户提交问题
3. 系统执行查询扩展
4. 通过词法 + 向量混合检索召回 chunk
5. 根据标题、章节、chunk 顺序做加权排序
6. 命中可靠时构造上下文调用 LLM
7. 返回答案、来源和置信度
8. 命中不足时标记人工介入

这条链路体现了：

- RAG 不只是调模型，而是“检索 + 上下文构造 + 置信度治理”
- 低置信度兜底能避免大模型胡编
- 来源追踪能让答案可解释、可追溯

### 4.3 问答转工单链路

1. 问答未命中或用户主动转人工
2. 系统基于 QA 消息创建工单
3. 工单进入待处理状态
4. 支持分配、处理中、解决、关闭
5. 工单与原始问题保持关联

这条链路体现了：

- AI 系统不能只追求自动回答，也要设计人工兜底
- 工单数据反过来能帮助发现知识库缺口

### 4.4 知识回流链路

1. 工单被解决后生成知识草稿
2. 运营人员审核草稿
3. 发布后生成新的知识文档
4. 新文档重新进入解析与索引链路
5. 后续类似问题可被知识库命中

这条链路是项目的业务闭环核心：

```text
未命中问题 -> 工单解决 -> 知识草稿 -> 发布入库 -> 后续命中率提升
```

## 5. 数据模型设计

核心表按业务域划分：

- 租户与权限：`tenant`、`user_account`、`sys_role`、`user_role_rel`
- 知识库：`knowledge_base`、`knowledge_document`、`knowledge_chunk`、`knowledge_chunk_index`
- 异步任务：`parse_task`、`dead_letter_message`
- 问答：`qa_session`、`qa_message`、`qa_message_source`
- 工单：`ticket`、`ticket_comment`
- 知识回流：`knowledge_draft`
- 审计：`audit_log`

设计重点：

- 业务表普遍带 `tenant_id`，支撑租户数据隔离
- 文档表拆分 `parse_status` 和 `index_status`，区分解析与索引两个阶段
- 任务表保留 `retry_count`、`error_message`、`started_at`、`finished_at`，方便排障
- QA 来源表单独存储，方便展示答案引用来源
- 审计日志独立成表，方便追踪关键操作

## 6. 鉴权与多租户隔离

项目采用 Spring Security + JWT + RBAC：

1. 用户登录后获取 JWT
2. `JwtAuthenticationFilter` 解析 token
3. `CurrentUserProvider` 提供当前用户上下文
4. Controller 通过 `@PreAuthorize` 控制角色权限
5. Service 查询数据时基于 `tenant_id` 限制范围

角色设计：

- `SUPER_ADMIN`：平台级租户管理
- `TENANT_ADMIN`：租户管理员
- `KNOWLEDGE_OPERATOR`：知识库运营
- `SUPPORT_AGENT`：工单处理人员
- `END_USER`：普通问答用户

面试重点回答：

- 平台接口只允许 `SUPER_ADMIN`
- 租户侧数据查询必须带当前用户 `tenant_id`
- 不能只依赖前端隐藏菜单，后端接口必须做权限校验
- 测试中需要覆盖无 token、越权访问、跨租户访问等场景

## 7. 异步任务与失败治理

文档解析和向量索引属于长耗时任务，因此不适合同步阻塞在上传接口中完成。

当前设计：

- RabbitMQ：承载解析任务消息
- Redis：承载运行时锁、幂等辅助和状态跟踪
- `parse_task`：持久化任务状态
- `dead_letter_message`：记录失败消息，支持治理页查看、重试、回放

可靠性设计点：

- 任务状态持久化，避免只依赖内存状态
- 失败任务记录错误原因，方便运营和开发定位
- DLQ 治理页能按任务、文档维度联动排障
- 自动重试和人工回放结合，避免失败任务永久沉默

后续可增强点：

- 对消息消费增加更严格的幂等键
- 对同一文档解析增加分布式锁
- 对任务状态流转增加状态机约束
- 增加失败率、重试次数、任务耗时监控

## 8. 检索与 RAG 设计

当前采用轻量级混合检索，而不是一开始就引入复杂向量数据库。

检索流程：

1. 用户问题进入查询扩展
2. 召回候选 chunk
3. 计算 lexical score 和 vector score
4. 对标题、章节、前置 chunk 加权
5. 控制单文档返回数量，避免一个文档垄断结果
6. 组装上下文发送给 LLM
7. 返回答案和来源

这套方案的面试价值：

- 能说明你理解 RAG 的关键不只是大模型调用
- 能说明你知道召回质量、上下文质量会影响答案质量
- 能说明你具备检索评估和持续优化意识

后续可增强点：

- 增加人工标注评测集
- 引入专业向量数据库
- 引入 rerank 模型
- 增加命中率、MRR、Recall@K 等指标

## 9. 工程化设计

项目已有工程化能力：

- Flyway 管理数据库版本
- Dockerfile 构建后端镜像
- Docker Compose 启动 MySQL、Redis、RabbitMQ、MinIO
- Smoke Test 文档沉淀验证流程
- GitHub Actions 后端 CI
- H2 支持本地轻量测试

面试时可以强调：

- 本地开发和容器部署可以分离
- 数据库结构变化通过 Flyway 可追踪
- 核心链路有集成测试支撑
- Smoke Test 能验证真实运行环境

## 10. 测试策略

测试策略详见 `docs/testing-strategy.md`。

当前测试不只验证 CRUD 是否能跑通，而是围绕企业系统的高风险边界建立证据链：

- 安全边界：`SecurityBoundaryIntegrationTests` 覆盖匿名访问拒绝、平台接口 RBAC、租户角色越权、跨租户知识库隔离。
- 鉴权链路：`AuthIntegrationTests` 覆盖登录、JWT、当前用户信息、用户与角色管理基础链路。
- 业务闭环：`CoreModulesIntegrationTests` 覆盖知识库、文档、解析任务、DLQ、QA、工单、知识草稿、看板和审计。
- 异步可靠性：`ParseTaskAttemptIdempotencyIntegrationTests` 覆盖状态机约束、CAS 开始执行、attempt 完成栅栏和启动恢复。
- RAG 链路：`QaRetrievalIntegrationTests`、`RetrievalEvaluationIntegrationTests` 和 `scripts/evaluate-retrieval.ps1` 覆盖召回、来源、低置信度兜底和固定样本评估。
- 真实环境验证：`docs/SMOKE_TEST.md` 覆盖 dev 容器形态下的健康检查、文档上传、解析、索引和问答。

本轮补强还修复了一个权限异常处理问题：Controller 上的 `@PreAuthorize` 拒绝访问时，不再被通用异常处理器错误包装成 HTTP `200`，现在会返回 HTTP `403` 与业务码 `40301`。

后续建议继续补强：

- 把检索评估样本扩展到 20 到 50 条，并保留 baseline / optimized 报告。
- 增加部门、数据范围等更细粒度的数据权限测试。
- 增加更多业务状态非法流转测试，例如工单、草稿审核等状态机约束。

## 11. 面试讲解主线

推荐用下面顺序讲：

1. 先讲业务闭环：知识库问答解决不了的问题转工单，工单解决后回流知识库
2. 再讲后端架构：Spring Boot 分层、多租户、RBAC、Flyway、MyBatis-Plus
3. 再讲异步链路：RabbitMQ 解耦解析任务，Redis 辅助运行时治理，DLQ 兜底失败
4. 再讲 AI 链路：文档 chunk、混合检索、来源追踪、低置信度兜底
5. 最后讲工程化：Docker、Smoke Test、CI、集成测试

一句话总结：

> 这个项目的重点不是做一个能聊天的页面，而是做一个企业内部知识服务闭环，并用 Java 后端工程能力保证它可管理、可追踪、可治理、可持续优化。

## 12. 当前不足与演进路线

已完成补强：

- 架构文档、交接文档、最终演示脚本已经补齐。
- 多租户隔离、RBAC 越权、匿名访问拒绝已经由 `SecurityBoundaryIntegrationTests` 覆盖。
- 核心业务闭环已有 `CoreModulesIntegrationTests` 支撑，异步幂等已有 `ParseTaskAttemptIdempotencyIntegrationTests` 支撑。

P0：

- 把项目讲解材料整理成简历和面试可用版本
- 继续做全页面交互细节巡检，尤其是导航、返回链路、空状态和异常降级提示

P1：

- 扩展检索评测集和质量指标
- 增加工单、草稿审核等更多业务状态机非法流转测试
- 增加更多可观测性指标

P2：

- 接入专业向量数据库
- 引入 rerank 模型
- 增加限流、熔断、灰度发布等生产级能力

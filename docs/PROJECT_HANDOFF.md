# KnowFlow 项目交接文档

> 用途：当聊天上下文过长或开启新会话时，先让 Codex/助手阅读本文件，即可快速恢复项目背景、当前完成度、架构思路、运行方式和下一步待办。

## 1. 项目定位

KnowFlow 是一个面向 Java 后端求职展示的「企业级智能知识服务与工单协同平台」。

项目目标不是只做一个简单聊天页面，而是构建一套更像企业内部系统的完整闭环：

- 知识库接入：知识库、文档上传、解析、切片、索引。
- 智能问答 RAG：用户提问、检索知识片段、调用大模型生成回答、展示引用来源。
- 问答转工单：低置信度、未命中或用户主动转人工时创建工单。
- 工单回流知识：工单解决后生成知识草稿，审核后沉淀为知识。
- 运营与监控：运营看板、问答记录、未命中分析、任务健康、DLQ 治理、审计日志。
- 工程化展示：多租户、权限、异步任务、Redis/RabbitMQ/MinIO、Flyway、Actuator、Smoke Test、Docker 验证。

面试表达重点：

- 这是一个「知识服务平台」，不是单纯 CRUD。
- 业务闭环是：文档进入系统 -> 解析切片 -> RAG 问答 -> 工单兜底 -> 知识回流 -> 运营观测。
- 技术闭环是：Spring Boot + MySQL + Redis + RabbitMQ + MinIO + Flyway + JWT + 管理台 + 大模型接口。

## 2. 当前推荐运行方式

项目路径：

```text
E:\WorkSpace\java\开发\knowflow-backend
```

推荐日常演示使用 IDEA 运行：

```text
Run Configuration: KnowFlow - Dev (8081 Fallback)
Main Class: com.knowflow.KnowFlowApplication
Profile: dev
Port: 8081
```

依赖容器：

- `knowflow-mysql`
- `knowflow-redis`
- `knowflow-rabbitmq`
- `knowflow-minio`

推荐访问入口：

```text
http://127.0.0.1:8081/admin/dashboard
```

用户侧问答入口：

```text
http://127.0.0.1:8081/assistant
```

常用账号：

```text
tenant.admin / Tenant@123
knowledge.operator / Tenant@123
```

注意：

- 不要把真实大模型 API Key 写入文档或提交到远程仓库。
- IDEA `.run` 配置里如果存在真实 Key，开源或发给别人前必须替换为占位符。
- Docker 日常关闭建议使用 `docker compose stop`，不要随意使用 `docker compose down -v`，后者会删除数据卷。

## 3. 核心技术选型与关键决策

### 3.1 后端技术栈

- Java 21。
- Spring Boot 3.3。
- Spring MVC 提供 REST API。
- Spring Security + JWT 做登录鉴权。
- MyBatis-Plus 做数据访问。
- Flyway 做数据库版本管理。
- MySQL 作为 dev 环境主数据库。
- H2 作为 local 快速启动内存库。
- Redis 用于运行态缓存、任务治理辅助、检索缓存等。
- RabbitMQ 用于文档解析/索引异步任务消息流。
- MinIO 用于接管文档对象存储。
- Actuator 暴露健康检查与运维观测能力。

### 3.2 前端技术栈

- 当前前端是 Spring Boot 静态资源模式。
- 页面位于 `src/main/resources/static/console`。
- 使用原生 HTML/CSS/JavaScript。
- 统一使用 `common.js` 渲染全局导航、用户菜单、页面入口。
- 管理台页面统一胶囊导航风格。

### 3.3 数据库与迁移

Flyway 脚本位于：

```text
src/main/resources/db/migration
```

当前主要迁移：

- `V1__init_core_tables.sql`：租户、知识库、文档、解析任务等核心表。
- `V2__init_auth_tables.sql`：用户、角色、用户角色关系等鉴权表。
- `V3__add_knowledge_chunk_table.sql`：知识切片表。
- `V4__init_qa_tables.sql`：问答记录、来源等。
- `V5__init_ticket_tables.sql`：工单系统。
- `V6__init_knowledge_draft_table.sql`：知识草稿。
- `V7__init_audit_log_table.sql`：操作审计日志。
- `V8__init_dead_letter_and_chunk_index_tables.sql`：死信治理与 chunk 索引。
- `V9__add_qa_retrieval_debug.sql`：检索调试数据。
- `V10__add_user_profile_fields.sql`：用户资料字段。
- `V11__add_qa_performance_fields.sql`：问答性能字段。
- `V12__init_qa_retrieval_evaluation.sql`：检索评估。
- `V13__init_parse_task_governance_event.sql`：解析任务治理事件。
- `V14__add_sla_ai_agent_ops.sql`：SLA、AI Agent、运营相关补充。

### 3.4 环境分层

配置文件：

- `application.yml`：通用配置，默认 `local`。
- `application-local.yml`：H2 内存库，无强依赖 MySQL/Redis/RabbitMQ/MinIO，适合快速启动。
- `application-dev.yml`：真实 MySQL + Redis + RabbitMQ + MinIO，适合完整演示。

推荐演示使用 `dev`，因为它能体现真实工程化能力。

## 4. 已完成模块

### 4.1 用户与权限系统

已完成：

- 登录接口。
- JWT 鉴权链路。
- 用户信息接口。
- 用户/角色/权限菜单基础能力。
- 租户管理员、知识运营、客服等角色。
- 用户中心页面。
- 用户资料展示与可编辑字段。
- 修改密码相关页面能力。
- 操作审计日志。

重要目录：

```text
src/main/java/com/knowflow/auth
src/main/java/com/knowflow/tenant
src/main/java/com/knowflow/audit
src/main/resources/static/console/profile.html
src/main/resources/static/console/profile.js
```

### 4.2 知识库管理

已完成：

- 知识库列表。
- 知识库详情。
- 知识库启停。
- 文档统计卡片。
- 解析失败统计。
- 从知识库详情跳转文档/解析任务/死信治理。
- 批量导入资料入口。
- 文档失败原因可视化。

重要目录：

```text
src/main/java/com/knowflow/knowledge
src/main/resources/static/console/knowledge-bases.html
src/main/resources/static/console/knowledge-bases.js
```

### 4.3 文档管理与解析任务

已完成：

- 文档上传。
- MinIO 存储接管。
- 文档列表。
- 文档详情。
- 文档预览/下载入口。
- 批量操作。
- 解析任务创建。
- RabbitMQ 异步解析消息流。
- Redis 运行态缓存。
- 解析任务状态推进。
- `knowledge_chunk` 切片落库。
- Markdown 按标题层级保留上下文切块，FAQ 按问答对保留语义切块。
- 失败任务跳转死信治理。
- 任务详情运行态快照。

重要目录：

```text
src/main/java/com/knowflow/parser
src/main/resources/static/console/documents.html
src/main/resources/static/console/documents.js
src/main/resources/static/console/parse-tasks.html
src/main/resources/static/console/parse-tasks.js
```

### 4.4 智能问答 RAG

已完成：

- 用户侧智能问答入口。
- 类 ChatGPT 固定高度对话布局。
- 左侧历史会话。
- 知识库选择。
- 模型选择。
- 推荐问题选择。
- 点击发送后立即乐观展示用户消息。
- 异步生成中状态。
- 检索知识片段。
- 大模型生成回答。
- 引用来源展示。
- Markdown 渲染与污染信息过滤优化。
- 检索调试面板。
- query variants 展示。
- lexical/vector/final score 展示。
- 问答记录管理台。
- 从问答入口跳转问答记录详情。

重要目录：

```text
src/main/java/com/knowflow/qa
src/main/java/com/knowflow/integration
src/main/resources/static/console/workbench.html
src/main/resources/static/console/workbench.js
src/main/resources/static/console/qa-records.html
src/main/resources/static/console/qa-records.js
```

### 4.5 工单系统

已完成：

- 问答无法解决时创建工单。
- 用户在智能问答页可手动创建工单。
- 工单列表。
- 工单详情。
- 工单状态流转。
- 处理记录。
- 工单与问答记录关联跳转。
- 从智能问答入口打开关联工单。
- 从问答记录定位到对应问题和详情。

重要目录：

```text
src/main/java/com/knowflow/ticket
src/main/resources/static/console/tickets.html
src/main/resources/static/console/tickets.js
```

### 4.6 知识回流

已完成：

- 工单解决后生成知识草稿。
- 知识草稿列表。
- 知识草稿详情。
- 草稿审核/发布链路。
- 问答记录、工单、草稿之间的跳转联动。

重要目录：

```text
src/main/java/com/knowflow/backflow
src/main/resources/static/console/knowledge-drafts.html
src/main/resources/static/console/knowledge-drafts.js
```

### 4.7 运营看板与未命中分析

已完成：

- 运营看板。
- 热点问题。
- 未命中问题分析。
- 问答趋势。
- 工单/草稿转化链路。
- 看板深链跳转到问答记录、工单、草稿。
- 页面标题悬浮说明。
- 与其他页面统一的胶囊导航样式。

重要目录：

```text
src/main/java/com/knowflow/dashboard
src/main/resources/static/console/dashboard.html
src/main/resources/static/console/dashboard.js
src/main/resources/static/console/dashboard.css
```

### 4.8 运维健康与治理

已完成：

- 运维健康页。
- 任务成功率、失败率、P95 耗时。
- DLQ 数量。
- AI 调用统计、Token、成本估算。
- 依赖健康检查。
- 失败任务治理入口。
- 死信治理入口。
- 解析任务链路入口。
- 从治理页面返回运维健康页。
- Actuator 健康检查入口。
- 部分接口失败时页面降级提示。

重要目录：

```text
src/main/java/com/knowflow/ops
src/main/resources/static/console/ops-health.html
src/main/resources/static/console/ops-health.js
```

### 4.9 死信治理与自动重试

已完成：

- 死信记录表。
- DLQ 管理页。
- 失败任务查看。
- 手动回放。
- 自动重试配置。
- 解析任务治理事件。
- 从死信返回运维健康页。

重要目录：

```text
src/main/resources/static/console/dead-letters.html
src/main/resources/static/console/dead-letters.js
src/main/java/com/knowflow/parser
```

### 4.10 工程化与验证

已完成：

- Docker Compose 启动 MySQL、Redis、RabbitMQ、MinIO。
- Spring Boot package 验证。
- Docker 镜像构建验证。
- 容器启动 smoke test。
- Windows/PowerShell 乱码问题修复文档。
- Smoke Test 文档。
- 测试策略与验证矩阵文档。
- 检索评估文档。
- 运维可观测性文档。

重要文件：

```text
docker-compose.yml
Dockerfile
LOCAL_SETUP.md
docs/SMOKE_TEST.md
docs/FINAL_DEMO_SCRIPT.md
docs/PROJECT_ARCHITECTURE.md
docs/testing-strategy.md
docs/ops-observability.md
docs/retrieval-evaluation.md
docs/rag-latency-tuning.md
```

## 5. 最近重要前端调整记录

### 5.1 全局导航统一

核心文件：

```text
src/main/resources/static/console/common.js
src/main/resources/static/console/workbench.css
```

已处理：

- 移除冗余“当前：xxx”胶囊按钮。
- 统一全局导航顺序：
  - 运营看板
  - 智能问答入口
  - 知识运营
  - 工单协同
  - 系统治理
  - 全部页面
  - 用户信息
- 用户信息固定靠右。
- 全部页面菜单统一为下拉/弹出式。
- 页面说明统一改成标题悬浮提示。

### 5.2 智能问答入口页面

核心文件：

```text
src/main/resources/static/console/workbench.html
src/main/resources/static/console/workbench.js
src/main/resources/static/console/workbench.css
```

已处理：

- 固定聊天窗口高度。
- 对话内容内部滚动。
- 左侧历史会话可切换。
- 输入框内集成知识库选择、模型选择、推荐问题。
- 外部点击自动收起下拉菜单。
- 添加滚动到底部按钮。
- 发送后立即清空输入框并展示用户消息。
- 回答 Markdown 渲染优化。
- 过滤回答中的污染信息。
- 工单与问答记录跳转打通。

### 5.3 知识库管理台页面

核心文件：

```text
src/main/resources/static/console/knowledge-bases.html
src/main/resources/static/console/knowledge-bases.js
src/main/resources/static/console/workbench.css
```

最近修复：

- 空的 `statusBanner` 不再占位。
- 成功提示自动隐藏。
- 页面标题说明改为悬浮提示。
- 导航栏与其他页面统一。

### 5.4 运营看板页面

核心文件：

```text
src/main/resources/static/console/dashboard.html
src/main/resources/static/console/dashboard.css
src/main/resources/static/console/dashboard.js
```

最近修复：

- 标题统一为“运营看板”。
- 原“智能知识运营指挥台”改为标题悬浮说明。
- Dashboard 独立 CSS 与其他页面导航风格对齐。
- 用户菜单样式补齐。

### 5.5 运维健康页面

核心文件：

```text
src/main/resources/static/console/ops-health.html
src/main/resources/static/console/ops-health.js
src/main/resources/static/console/workbench.css
```

最近修复：

- 新增治理闭环动作区。
- 查看问答记录、查看失败任务、进入死信治理时携带 `returnUrl`。
- 目标页出现“返回运维健康监控台”按钮。
- AI 用量接口失败时不拖垮整页。
- 依赖健康、Actuator 入口显示优化。

### 5.6 P0 复合页面视觉收口

核心文件：

```text
src/main/resources/static/console/common.js
src/main/resources/static/console/workbench.css
src/main/resources/static/console/dashboard.css
src/main/resources/static/console/dashboard.js
src/main/resources/static/console/knowledge-bases.js
src/main/resources/static/console/ops-health.js
src/main/resources/static/console/workbench.js
docs/interview-storyline.md
```

最近修复：

- 复合页面巡检已覆盖 `dashboard`、`knowledge-bases`、`ops-health`、`workbench`。
- 未发现“当前：xxx”胶囊回归，当前页仍通过导航按钮高亮表达。
- 用户菜单继续由统一 shell 渲染并固定在右侧账户入口。
- `common.js` 的 `renderStateBlock()` 新增 `compact` 选项，适配表格、图表、对话侧栏等密集区域。
- `workbench.css` 与 `dashboard.css` 补齐 `state-block-error`、`state-block-warning`、`state-block-loading`、`state-block-success`、`state-block-compact` 等语义样式。
- `dashboard`、`knowledge-bases`、`ops-health`、`workbench` 的空态、加载态、错误态已接入统一状态块。
- `docs/interview-storyline.md` 已补充 2 到 3 分钟面试压缩版和 30 秒追问版。
- 已验证关键脚本 `node --check` 通过，`mvn -q -DskipTests compile` 通过。

### 5.7 P1 面试加分项推进记录

核心文件：

```text
src/main/java/com/knowflow/ticket/service/impl/TicketServiceImpl.java
src/main/java/com/knowflow/agent/service/impl/AgentToolServiceImpl.java
src/main/java/com/knowflow/agent/vo/AgentKnowledgeDraftSuggestionVO.java
src/main/java/com/knowflow/qa/evaluation
src/main/java/com/knowflow/dashboard/controller/DashboardPageController.java
src/main/java/com/knowflow/common/config/SecurityConfig.java
src/main/java/com/knowflow/auth/service/impl/AuthServiceImpl.java
src/main/resources/static/console/tickets.html
src/main/resources/static/console/tickets.js
src/main/resources/static/console/retrieval-evaluations.html
src/main/resources/static/console/retrieval-evaluations.js
src/main/resources/static/console/common.js
src/main/resources/static/console/workbench.css
src/test/java/com/knowflow/AgentToolIntegrationTests.java
src/test/java/com/knowflow/RetrievalEvaluationIntegrationTests.java
```

最近新增：

- 工单 SLA 增强：
  - 后端支持 `AT_RISK` 计算态筛选，按“距离截止不足 2 小时”识别临近超时。
  - `BREACHED` 筛选会覆盖已经过期但定时任务还没来得及落库的开放工单。
  - 工单页支持 SLA 倒计时、进度条、已超时/临近超时/已达成状态展示和筛选。
- Agent 工具调用深化：
  - 新增 `SUGGEST_KNOWLEDGE_DRAFT_FROM_TICKET` 工具。
  - Agent 可基于已解决/已关闭工单、来源问答、处理评论、目标知识库生成知识草稿建议。
  - 工单详情页“生成知识草稿”卡片新增“Agent 建议草稿”按钮，可展示推荐标题、问题、答案摘要、推荐知识库、置信度和理由，并自动填入草稿表单。
  - 该工具只生成建议，不直接落库，保留人工审核与运营边界。
- 检索评估自动化管理台：
  - 新增 `/admin/retrieval-evaluations` 页面。
  - 支持维护评估样本：知识库、问题、预期状态、预期文档、关键词、TopK。
  - 支持一键运行评估，展示通过率、Recall@K、Top1 命中率、无命中准确率。
  - 支持查看每条 case 的结果、失败原因、召回证据、query variants。
  - 已加入“知识运营”模块导航、页面路由、安全白名单和用户菜单。
- 验证记录：
  - `node --check src/main/resources/static/console/tickets.js` 通过。
  - `node --check src/main/resources/static/console/retrieval-evaluations.js` 通过。
  - `node --check src/main/resources/static/console/common.js` 通过。
  - `mvn -q -DskipTests compile` 通过。
  - `mvn -q -Dtest=AgentToolIntegrationTests test` 通过。
  - `mvn -q -Dtest=RetrievalEvaluationIntegrationTests test` 通过。

### 5.8 P2 文档解析能力增强第一轮

核心文件：

```text
src/main/java/com/knowflow/parser/service/impl/SimpleDocumentParsingService.java
src/main/java/com/knowflow/knowledge/service/impl/DocumentServiceImpl.java
src/test/java/com/knowflow/parser/service/impl/SimpleDocumentParsingServiceTests.java
docs/testing-strategy.md
```

最近新增：

- Markdown 文档不再只按普通空行切块，而是按 `#` 标题层级组织 section，并在 chunk 中保留类似 `一级标题 > 二级标题` 的上下文路径。
- FAQ 文档新增 `.faq` 支持，解析时按 `Q:/A:`、`Question:/Answer:`、`问:/答:`、`问题:/答案:` 问答对切块，避免问题和答案被分散到不同 chunk。
- 普通文本、PDF、CSV、JSON、YAML、日志文件继续沿用原有解析路径，保持兼容。
- 文档预览白名单新增 `.faq`，管理台可直接预览 FAQ 文本。
- 新增 `SimpleDocumentParsingServiceTests`，覆盖 Markdown 标题上下文保留和 FAQ 问答对语义切块。
- 验证记录：
  - `mvn -q -Dtest=SimpleDocumentParsingServiceTests test` 通过。
  - `mvn -q -DskipTests compile` 通过。

## 6. 当前待办事项

### 已完成的近期补强

1. 已清理 `.run` 配置里的真实 API Key 风险。
   - dev 运行配置使用占位符，避免把真实密钥写入仓库或交接材料。

2. 已完成全局导航第一轮重构。
   - 顶部导航只保留业务模块入口。
   - “知识运营”和“智能问答入口”不再重复显示指向同页的按钮。
   - 用户中心入口回归右上角账户信息下拉菜单。
   - 模块内子页面通过更紧凑的下拉方式组织。

3. 已补齐最终演示脚本。
   - 演示链路覆盖文档上传、智能问答、转工单、知识草稿回流、运营看板与运维健康。

4. 已补强第二组安全与可靠性测试。
   - 新增 `SecurityBoundaryIntegrationTests`，覆盖匿名访问、RBAC 越权、平台/租户边界和跨租户知识库隔离。
   - 修复 `@PreAuthorize` 权限拒绝被全局异常处理器包装成 HTTP `200` 的问题，现在返回 HTTP `403` 和业务码 `40301`。
   - 测试策略沉淀到 `docs/testing-strategy.md`。

5. 已完成主要列表详情页状态块统一。
   - `common.js` 提供 `renderStateBlock()` 统一空态、加载态、错误态。
   - `documents.js`、`qa-records.js`、`parse-tasks.js`、`dead-letters.js`、`tickets.js`、`knowledge-drafts.js`、`audit-logs.js` 已接入统一状态块。
   - `frontend-layout-audit.md` 已记录本轮交互细节收口。

6. 已完成 P0 复合页面视觉巡检与收口。
   - 重点页面 `dashboard`、`knowledge-bases`、`ops-health`、`workbench` 已巡检。
   - 未发现“当前：xxx”胶囊回归，用户菜单继续靠右。
   - 复合卡片、图表区、对话区、运维降级提示已统一到 `renderStateBlock()` 语义状态块。
   - `common.js`、`workbench.css`、`dashboard.css` 已补齐 compact 与错误/警告/加载/成功语义样式。

7. 已整理简历和面试讲解版本。
   - 把业务闭环、安全边界、异步幂等、RAG 评估和工程化验证压缩成 2 到 3 分钟讲法。
   - `docs/interview-storyline.md` 已新增 2 到 3 分钟面试压缩版和 30 秒追问版。

8. 已完成 P1 第一轮面试加分项。
   - SLA 超时提醒增强已落地到后端筛选、工单列表和工单详情页。
   - Agent 工具调用已从“查/建工单”深化到“根据已解决工单建议知识草稿”。
   - 检索评估自动化已从文档/脚本扩展为管理台页面，可维护样本、运行评估、查看指标和证据明细。

9. 已完成 P2 文档解析能力增强第一轮。
   - Markdown 入库时保留标题层级上下文，提升知识片段的可读性和召回证据表达。
   - FAQ 入库时按问答对切块，降低问题和答案被拆散导致的低质量召回风险。
   - `.faq` 文件已纳入解析和预览支持范围，并补充专项单元测试。

### P0：当前状态

- P0 已完成，后续除非发现演示回归，不建议继续在 P0 上反复打磨。
- P1 第一轮加分项也已完成，下一步可选择继续补 AI 成本细化，或进入 P2 长期优化。

### P1：面试加分项

1. Agent 工具调用深化。
   - 已完成：查询工单进度、创建工单、查询知识库、从已解决工单生成知识草稿、建议知识草稿。
   - 仍可继续做：“根据失败任务建议修复动作”“根据运维健康自动生成治理建议”。

2. SLA 超时提醒增强。
   - 已完成：SLA 倒计时、临近超时、已超时、已达成状态展示和筛选。
   - 仍可继续做：站内提醒、邮件/消息通知、SLA 策略配置化。

3. AI 成本和 Token 统计细化。
   - 当前运维页已有 AI 用量区。
   - 可进一步落到模型调用日志、用户维度、知识库维度。

4. 检索评估自动化。
   - 已完成：管理台页面、样本维护、运行评估、指标卡片、结果明细、召回证据和 query variants 展示。
   - 仍建议把样本扩展到 20 到 50 条，并保留 baseline / optimized 对比报告。

### P2：长期优化

1. 前端工程化。
   - 当前是原生静态资源。
   - 后续可迁移到 Vue/React，形成前后端分离。

2. 多租户数据权限深化。
   - 当前已具备租户字段、角色权限和跨租户隔离测试。
   - 后续可增加部门、数据范围等更细粒度权限模型。

3. 文档解析能力增强。
   - 已完成第一轮：Markdown 标题层级感知切块、FAQ 问答对语义切块、`.faq` 预览支持。
   - 后续可继续扩展 Word、扫描版 PDF OCR、网页内容接入。
   - 网页抓取需注意版权和站点协议，不建议无授权批量复制第三方内容。

4. 线上部署方案。
   - 可增加 Nginx、Docker Compose 全量部署、环境变量模板、日志目录挂载。

## 7. 新会话恢复指令

如果开启新会话，可以直接发送下面这段：

```text
项目路径：E:\WorkSpace\java\开发\knowflow-backend
请先阅读 docs/PROJECT_HANDOFF.md、LOCAL_SETUP.md、docs/PROJECT_ARCHITECTURE.md、docs/testing-strategy.md、docs/SMOKE_TEST.md。
当前项目是 KnowFlow：企业级智能知识服务与工单协同平台。
请基于现有代码继续开发，不要重建项目。
运行方式优先使用 IDEA 的 KnowFlow - Dev (8081 Fallback)，入口是 http://127.0.0.1:8081/admin/dashboard。
注意不要输出或提交真实 API Key。
```

## 8. 常用页面索引

```text
运营看板          /admin/dashboard
智能问答入口      /assistant
知识库管理        /admin/knowledge-bases
文档管理          /admin/documents
解析任务          /admin/parse-tasks
检索评估          /admin/retrieval-evaluations
问答记录          /admin/qa-records
工单管理          /admin/tickets
知识草稿          /admin/knowledge-drafts
运维健康          /admin/ops-health
死信治理          /admin/dead-letters
审计日志          /admin/audit-logs
用户中心          /admin/profile
Swagger           /swagger-ui.html
Actuator Health   /actuator/health
```

## 9. 常用命令

在项目目录执行：

```powershell
cd E:\WorkSpace\java\开发\knowflow-backend
```

启动基础设施：

```powershell
$env:KNOWFLOW_MYSQL_HOST_PORT="13306"
$env:KNOWFLOW_REDIS_HOST_PORT="16379"
docker compose -f .\docker-compose.yml up -d
```

停止基础设施但保留数据：

```powershell
docker compose stop
```

编译：

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests compile
```

打包：

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -DskipTests package
```

P1 相关专项验证：

```powershell
node --check .\src\main\resources\static\console\tickets.js
node --check .\src\main\resources\static\console\retrieval-evaluations.js
node --check .\src\main\resources\static\console\common.js
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=AgentToolIntegrationTests test
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=RetrievalEvaluationIntegrationTests test
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' -q -Dtest=SimpleDocumentParsingServiceTests test
```

查看容器：

```powershell
docker ps
```

## 10. 面试讲解主线

建议讲项目时按这个顺序：

1. 背景：企业内部知识散落，客服/员工重复提问，人工压力大。
2. 目标：做一个知识服务和工单协同平台，让问题能够被知识库自动回答，不能回答时进入人工闭环。
3. 用户链路：上传文档 -> 解析切片 -> 提问 -> RAG 回答 -> 未解决转工单 -> 工单解决 -> 生成知识草稿。
4. 工程链路：MySQL 存业务数据，MinIO 存文件，RabbitMQ 解耦解析任务，Redis 存运行态/缓存，Flyway 管理表结构。
5. 质量保障：检索调试、召回分数、问答记录、未命中分析、检索评估管理台、运维健康、DLQ、审计日志。
6. P1 加分点：SLA 倒计时和超时筛选、Agent 根据已解决工单建议知识草稿、检索评估样本和运行报告。
7. 成长点：如果继续做，会补 AI 成本细化、前后端分离、多租户权限深化、Word/OCR 解析和线上部署。

## 11. 风险提醒

- 不要在文档或远程仓库暴露真实 API Key。
- 不要用 `docker compose down -v` 清理环境，除非明确想删除数据库和对象存储数据。
- 如果页面样式修改后浏览器无变化，先强刷，再确认 Maven 编译是否把静态资源复制到 `target/classes`。
- 如果端口冲突，优先使用 `8081`，不要和旧的 `8080` Java 进程混用。
- 如果 Redis 连接异常，确认项目使用的是 `16379`，避免连接到本机已有的 `6379`。

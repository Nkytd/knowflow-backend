# KnowFlow Backend

KnowFlow 是一个面向企业知识服务场景的 Java 后端项目，核心目标是把“知识库问答、未命中转工单、工单解决后回流知识草稿、运营分析”串成完整闭环。

这个项目适合作为求职中的中大型业务项目，能够同时体现：

- 传统 Java 后端开发能力
- 企业级权限、多租户、异步任务编排能力
- AI 大模型应用开发中的 RAG、知识回流、运营分析能力

## 项目定位

项目主题：企业级智能知识服务与工单协同平台

典型业务链路：

1. 管理员维护租户、用户、角色、知识库、文档
2. 文档进入解析任务，异步切分为 chunk 并建立向量索引
3. 用户在问答工作台发起问题
4. 系统执行混合检索，命中则生成回答，未命中则转人工工单
5. 工单解决后生成知识草稿，审核发布后重新进入知识库
6. 运营看板分析未命中问题、工单转化、知识回流效果

## 当前能力

已完成的核心模块：

- 租户、用户、角色、JWT 鉴权、多租户隔离
- 知识库、文档、解析任务、解析失败治理
- RabbitMQ + Redis 驱动的异步解析与索引流程
- MinIO 文档存储接入
- QA 会话、问答记录、来源追踪、反馈提交
- 未命中转工单、工单处理、知识草稿回流
- 运营看板、未命中问题分析、审计日志
- 管理台页面、用户侧问答工作台

本轮强化内容：

- 检索质量增强：查询扩展、词法+向量混合召回、文档去重、标题/章节/前置 chunk 加权
- 命中判定增强：强词法命中、低置信度兜底、上下文格式优化
- 工程化补齐：Dockerfile、CI、可执行评估脚本、面试讲述文档

## 技术栈

- Java 17 / Spring Boot 3.3
- Spring Security + JWT
- MyBatis-Plus
- Flyway
- MySQL / H2
- Redis
- RabbitMQ
- MinIO
- OpenAI Compatible LLM / Embedding API
- Swagger OpenAPI

## 检索方案

当前问答链路采用轻量级混合检索方案：

- 文档解析后生成 `knowledge_chunk`
- 向量索引写入 `knowledge_chunk_index`
- 查询阶段进行 deterministic query expansion
- 同时计算 lexical score 和 vector score
- 结合标题匹配、章节匹配、早期 chunk boost 做重排
- 控制单文档最大返回 chunk 数，提升结果多样性

这套方案的价值不是“替代专业向量数据库”，而是用较低工程复杂度，把项目从“普通 CRUD + 调模型”提升到“有检索设计、有优化思路、有评估方法”的层次。

## 本地启动

### 1. 本地轻量模式

使用 `local` profile 时，系统默认使用 H2，不强依赖 Redis、RabbitMQ、MySQL。

```powershell
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
```

或者：

```powershell
java -jar .\target\knowflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### 2. 完整基础设施模式

先启动基础设施：

```powershell
$env:KNOWFLOW_MYSQL_HOST_PORT="13306"
$env:KNOWFLOW_REDIS_HOST_PORT="16379"
docker compose -f .\docker-compose.yml up -d
```

再启动后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-ai.ps1 `
  -ApiKey "<your-api-key>" `
  -Profile dev `
  -DbPort 13306 `
  -RedisPort 16379 `
  -RabbitMqPort 5672
```

更详细的启动说明见 [LOCAL_SETUP.md](./LOCAL_SETUP.md)。

## Docker 运行

构建镜像：

```powershell
docker build -t knowflow-backend:local .
```

运行容器：

```powershell
docker run --rm -p 8080:8080 --name knowflow-backend knowflow-backend:local
```

如果你要使用真实 MySQL / Redis / RabbitMQ / MinIO / LLM，需要在运行容器时额外挂载对应环境变量。

## 页面入口

管理台入口：

- `http://127.0.0.1:8081/admin/dashboard`

用户侧问答工作台：

- `http://127.0.0.1:8081/workbench`

常用演示账号：

- `tenant.admin / Tenant@123`
- `knowledge.operator / Tenant@123`

## 测试

运行后端测试：

```powershell
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test
```

如果你的 IDEA Maven 路径不同，替换为你本机的 `mvn.cmd` 即可。

项目当前包含：

- 鉴权与权限集成测试
- 知识库/文档/解析任务集成测试
- QA 检索与未命中转人工集成测试
- RBAC 越权访问集成测试
- 查询扩展单元测试
- 解析/索引任务 attempt 幂等回归测试

## 面试材料

为了便于你后续写简历和准备面试，这个仓库额外提供了文档：

- [docs/PROJECT_ARCHITECTURE.md](./docs/PROJECT_ARCHITECTURE.md)
- [docs/async-task-governance.md](./docs/async-task-governance.md)
- [docs/ops-observability.md](./docs/ops-observability.md)
- [docs/job-hunt-project-guide.md](./docs/job-hunt-project-guide.md)
- [docs/interview-storyline.md](./docs/interview-storyline.md)
- [docs/retrieval-evaluation.md](./docs/retrieval-evaluation.md)
- [docs/retrieval-evaluation-backend.md](./docs/retrieval-evaluation-backend.md)
- [docs/SMOKE_TEST.md](./docs/SMOKE_TEST.md)

推荐面试前按这个顺序复习：

1. 先读 `PROJECT_ARCHITECTURE.md`，建立系统全局架构和核心链路
2. 再读 `interview-storyline.md`，准备 1 分钟、3 分钟、10 分钟讲述版本
3. 再读 `retrieval-evaluation.md`，准备 RAG 与检索质量相关追问
4. 再读 `async-task-governance.md`，准备 RabbitMQ、Redis、幂等和状态机追问
5. 再读 `ops-observability.md`，准备任务失败率、P95、DLQ 和 Actuator 追问
6. 最后跑一遍 `SMOKE_TEST.md`，保证现场演示链路可用

## 后续建议

如果继续把项目打磨到更强的求职状态，建议优先补：

1. 检索评估样本集扩充，形成前后对比指标
2. 接入更稳定的向量存储或召回层
3. 增加接口限流、幂等、监控指标和链路观测
4. 前后端联调演示脚本和截图素材

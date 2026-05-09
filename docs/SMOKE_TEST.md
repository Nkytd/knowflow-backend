# Smoke Test Guide

这份文档用于验证 KnowFlow 在 `dev` 容器形态下的完整可用性，覆盖以下链路：

1. 基础依赖是否正常启动
2. MySQL / Redis / RabbitMQ / MinIO 是否可连接
3. 后端健康检查是否通过
4. 管理台与用户侧入口是否可访问
5. 自动化 smoke test 是否通过
6. 文档上传、解析、索引、问答是否打通
7. 结果报告是否落盘，便于复盘和演示

## 预置条件

执行前请先确认：

- Docker Desktop 已启动
- `knowflow-mysql` 已运行并映射到 `13306`
- `knowflow-redis` 已运行并映射到 `16379`
- `knowflow-rabbitmq` 已运行并映射到 `5672`
- `knowflow-minio` 已运行并映射到 `9000`
- `knowflow-backend-dev-test` 最终会暴露在 `http://127.0.0.1:18081`

如果你要重新启动 `dev` 容器，可以执行：

```powershell
$env:DOCKER_CONFIG='E:\WorkSpace\java\开发\knowflow-backend\.docker-temp'
docker run -d --name knowflow-backend-dev-test -p 18081:8080 `
  -e SPRING_PROFILES_ACTIVE=dev `
  -e KNOWFLOW_DB_HOST=host.docker.internal `
  -e KNOWFLOW_DB_PORT=13306 `
  -e KNOWFLOW_DB_USERNAME=root `
  -e KNOWFLOW_DB_PASSWORD=root `
  -e KNOWFLOW_REDIS_HOST=host.docker.internal `
  -e KNOWFLOW_REDIS_PORT=16379 `
  -e KNOWFLOW_RABBITMQ_HOST=host.docker.internal `
  -e KNOWFLOW_RABBITMQ_PORT=5672 `
  -e KNOWFLOW_RABBITMQ_USERNAME=guest `
  -e KNOWFLOW_RABBITMQ_PASSWORD=guest `
  -e KNOWFLOW_STORAGE_TYPE=minio `
  -e KNOWFLOW_MINIO_ENDPOINT=http://host.docker.internal:9000 `
  -e KNOWFLOW_MINIO_ACCESS_KEY=minioadmin `
  -e KNOWFLOW_MINIO_SECRET_KEY=minioadmin `
  -e KNOWFLOW_MINIO_BUCKET=knowflow `
  -e KNOWFLOW_MINIO_AUTO_CREATE_BUCKET=true `
  -e KNOWFLOW_LLM_OPENAI_ENABLED=false `
  knowflow-backend:local
```

## 验证步骤

### 1. 健康检查

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:18081/actuator/health
```

预期结果：

- `status = UP`
- `db = UP`
- `redis = UP`
- `rabbit = UP`

### 2. 页面入口检查

浏览器访问：

- `http://127.0.0.1:18081/admin/dashboard`
- `http://127.0.0.1:18081/workbench`

### 3. 执行自动化 smoke test

建议使用 `-NoProfile` 运行，避免本机 PowerShell profile 干扰：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test-dev-container.ps1
```

如果你的服务不是跑在 `18081`，可以指定 `BaseUrl`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test-dev-container.ps1 `
  -BaseUrl "http://127.0.0.1:18081"
```

## 脚本会自动完成什么

自动化脚本会按顺序完成下面这些动作：

1. 登录系统，账号为 `tenant.admin / Tenant@123`
2. 创建一个临时知识库
3. 生成一份用于演示的 Markdown 文档
4. 上传文档
5. 轮询直到 `parseStatus = SUCCESS` 且 `indexStatus = SUCCESS`
6. 创建一个问答会话
7. 发起一次基于知识库的问答
8. 拉取问答详情和召回来源
9. 输出结果到 `target/smoke-test-report.json`

## 通过标准

满足以下条件即可认为 smoke test 通过：

- `parseStatus = SUCCESS`
- `indexStatus = SUCCESS`
- `answerStatus = SUCCESS`
- `needHumanHandoff = false`
- `sourceCount >= 1`
- `firstSourceDocument` 非空

## 结果产物

执行完成后会生成：

- 报告文件：`target/smoke-test-report.json`
- 临时测试文档目录：`target/smoke-test/`

## 常见问题

如果脚本执行失败，优先检查以下几项：

- Docker Desktop 是否正常运行
- MySQL / Redis / RabbitMQ / MinIO 容器是否都在运行
- `knowflow-backend-dev-test` 是否已经成功启动
- `http://127.0.0.1:18081/actuator/health` 是否可以访问
- 是否使用了 `powershell -NoProfile` 运行脚本，避免 profile 输出干扰日志

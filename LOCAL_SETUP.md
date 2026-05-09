# Local Setup

## Real LLM Configuration

The backend now supports an OpenAI-compatible chat provider. If the provider configuration is incomplete, the system automatically falls back to the local template answer generator.

Set these environment variables before starting the app:

```powershell
$env:KNOWFLOW_LLM_OPENAI_ENABLED="true"
$env:KNOWFLOW_LLM_OPENAI_BASE_URL="https://api.longcat.chat/openai"
$env:KNOWFLOW_LLM_OPENAI_API_KEY="<your-api-key>"
$env:KNOWFLOW_LLM_OPENAI_CHAT_MODEL="LongCat-Flash-Thinking-2601"
```

Optional tuning:

```powershell
$env:KNOWFLOW_LLM_OPENAI_CONNECT_TIMEOUT_MS="5000"
$env:KNOWFLOW_LLM_OPENAI_READ_TIMEOUT_MS="60000"
$env:KNOWFLOW_LLM_OPENAI_MAX_CONTEXT_CHUNKS="5"
```

## Local Run

The `local` profile is designed to run without mandatory Redis or RabbitMQ dependencies.

```powershell
java -jar .\target\knowflow-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

If you want to use Maven:

```powershell
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
```

You can also use the helper script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-ai.ps1 -ApiKey "<your-api-key>"
```

## Dev Run With Real Infrastructure

Docker Desktop is installed and was verified working on 2026-04-25.

Start the local infrastructure:

```powershell
$env:KNOWFLOW_MYSQL_HOST_PORT="13306"
$env:KNOWFLOW_REDIS_HOST_PORT="16379"
docker compose -f .\docker-compose.yml up -d
```

Recommended host ports on this machine:

- MySQL: `13306`
- Redis: `16379`
- RabbitMQ: `5672`
- RabbitMQ Management: `15672`
- MinIO API: `9000`
- MinIO Console: `9001`

Why Redis uses `16379` here:

- This machine already has a local `redis-server` listening on `127.0.0.1:6379`.
- Docker can still publish `0.0.0.0:6379`, but Spring Boot connecting to `localhost:6379` may hit the local Redis first.
- Using `16379` avoids that conflict and makes `/actuator/health` report `redis: UP`.

Start the backend in the real `dev` profile:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-ai.ps1 `
  -ApiKey "<your-api-key>" `
  -Profile dev `
  -DbPort 13306 `
  -RedisPort 16379 `
  -RabbitMqPort 5672
```

## IntelliJ IDEA Run Workflow

If you prefer running the project from IntelliJ IDEA instead of repeatedly typing commands, use the shared run configurations under:

`knowflow-backend/.run/`

Available configurations:

- `KnowFlow - Dev (8080)`
- `KnowFlow - Dev (8081 Fallback)`

Recommended choice on this machine:

- Use `KnowFlow - Dev (8081 Fallback)` first.
- A previous Java process has already been observed occupying `127.0.0.1:8080`, so `8081` is the safer demo entry.

In IntelliJ IDEA:

1. Open the `knowflow-backend` project.
2. Open `Run -> Edit Configurations`.
3. Select `KnowFlow - Dev (8081 Fallback)`.
4. In `Environment variables`, replace only:
   - `KNOWFLOW_LLM_OPENAI_API_KEY=PASTE_YOUR_LONGCAT_API_KEY_HERE`
5. Keep the other environment variables as-is.
6. Run the configuration.

What successful startup should look like:

- Active profile is `dev`
- Tomcat starts on port `8081`
- MySQL, Redis, RabbitMQ, and MinIO connect without startup failure

Primary demo entry after startup:

- `http://127.0.0.1:8081/admin/dashboard`
- `http://127.0.0.1:8081/workbench`

Other pages:

- `http://127.0.0.1:8081/admin/qa-records`
- `http://127.0.0.1:8081/admin/tickets`
- `http://127.0.0.1:8081/admin/knowledge-drafts`
- `http://127.0.0.1:8081/admin/dead-letters`
- `http://127.0.0.1:8081/admin/audit-logs`

Demo login:

- `tenant.admin / Tenant@123`
- `knowledge.operator / Tenant@123`

If you must use `8080`, stop the old process that is already listening on that port before starting a new instance.

## Conversation Handoff

If the chat context becomes too large, open a new conversation and tell Codex:

- project path: `E:\WorkSpace\java\开发\knowflow-backend`
- read: `LOCAL_SETUP.md`
- current goal: continue IDEA-based local demo startup or troubleshoot page access

This is enough context to resume quickly without re-explaining the whole project history.

## Knowledge Documents

The current demo knowledge files are located at:

`E:\WorkSpace\java\开发\knowledge document`

Current examples include:

- `java基础.md`
- `Redis.md`
- `Flask开发.md`
- `World Model.md`

These files are suitable for document upload, parsing, chunking, retrieval, and QA demo flows.

You can batch import them and run a QA smoke test with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo-import-and-smoke-test.ps1
```

The current demo environment already imported 11 markdown files into a knowledge base created on 2026-04-25.

One confirmed end-to-end QA smoke test used this question:

```text
flask run --host=0.0.0.0 --port=5050
```

That request returned:

- `answerStatus = SUCCESS`
- `modelName = longcat-flash-thinking-2601-platform`
- retrieval sources from the imported `Flask开发.md`

## Packaging Note

If `mvn package` fails during the Spring Boot `repackage` step, an older running `java` process may still be holding the jar file in `target/`.

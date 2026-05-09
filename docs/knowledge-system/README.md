# KnowFlow 项目知识体系总览

这套文档不是泛泛的八股整理，而是围绕 `KnowFlow` 这个项目的真实技术栈，帮你建立一套适合求职和后续复习的知识框架。

目标有三个：

1. 帮你补齐 Java 后端开发的基础认知
2. 让你知道每个技术在项目里到底解决了什么问题
3. 让你后续可以把这批文档继续导入知识库，作为自己的复习资料

## 建议学习顺序

如果你现在基础还比较薄弱，建议按下面顺序看：

1. [01-java-basic.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\01-java-basic.md)
2. [02-spring-boot.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\02-spring-boot.md)
3. [03-mysql-flyway-mybatis-plus.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\03-mysql-flyway-mybatis-plus.md)
4. [04-security-jwt-rbac.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\04-security-jwt-rbac.md)
5. [05-redis.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\05-redis.md)
6. [06-rabbitmq-async.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\06-rabbitmq-async.md)
7. [07-docker-minio-deploy.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\07-docker-minio-deploy.md)
8. [08-rag-search-knowledge.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\08-rag-search-knowledge.md)
9. [09-project-interview-practice.md](E:\WorkSpace\java\开发\knowflow-backend\docs\knowledge-system\09-project-interview-practice.md)

## 这套文档覆盖哪些能力

- Java 基础语法、集合、异常、并发、IO
- Spring Boot 开发方式、分层设计、配置管理、接口开发
- MySQL 表设计、索引、事务、Flyway 迁移、MyBatis-Plus 持久层
- Spring Security、JWT、RBAC、租户隔离
- Redis 缓存与会话能力
- RabbitMQ 异步消息、重试、死信队列
- Docker、容器化、本地依赖编排、MinIO 文件存储
- RAG、知识切片、检索、问答、知识回流
- 面向求职的项目讲解与面试表达

## 你可以怎么用这套文档

### 用法一：按学习路线逐篇阅读

适合你现在这种“想建立完整框架”的阶段。

### 用法二：边看边对照项目代码

建议对照这些模块目录：

- `src/main/java/com/knowflow/auth`
- `src/main/java/com/knowflow/tenant`
- `src/main/java/com/knowflow/knowledge`
- `src/main/java/com/knowflow/parser`
- `src/main/java/com/knowflow/qa`
- `src/main/java/com/knowflow/ticket`
- `src/main/java/com/knowflow/backflow`
- `src/main/java/com/knowflow/dashboard`
- `src/main/java/com/knowflow/integration`

### 用法三：导入到你自己的知识库

后续你可以把 `docs/knowledge-system` 目录里的文档上传到平台中，形成你自己的学习知识库。这样你就可以：

- 自己问自己面试题
- 回看某个知识点对应的项目实现
- 用问答工作台做复习

## 建议的复习方法

每篇文档都建议你按下面四步来复习：

1. 先看“这项技术在项目里解决什么问题”
2. 再看“核心概念”
3. 然后看“项目中的真实落点”
4. 最后练“面试题 + 小练习”

## 你的现阶段重点

如果你的目标是先找 Java 开发实习，最先要掌握的是：

- Java 基础
- Spring Boot
- MySQL
- 接口开发
- 登录鉴权
- 异步任务
- Docker 本地启动

RAG、大模型、向量检索这些内容可以作为你项目的亮点加分项，但不建议一开始把主要精力全部压在这上面。

## 下一步建议

看完这套文档后，你可以继续让我帮你做三件事：

1. 把每篇文档继续扩成“详细版学习笔记”
2. 基于这些文档给你出一套“面试问答题库”
3. 按周给你排一个“Java 后端求职学习计划”

# Spring Boot 知识框架

## 1. Spring Boot 在项目里解决什么问题

如果没有 Spring Boot，这个项目会非常麻烦：

- 需要自己管理对象创建
- 需要自己处理请求分发
- 需要手动整合数据库、Redis、RabbitMQ
- 配置管理会非常混乱

Spring Boot 的价值就是：

- 快速搭建 Web 后端
- 管理 Bean 生命周期
- 提供自动配置能力
- 统一整合各种中间件

## 2. 你需要建立的理解

不要把 Spring Boot 理解成“写注解就能跑”的黑盒。

你至少要理解它做了三层事情：

1. 管理对象
2. 接收 HTTP 请求
3. 把配置和基础设施装配起来

## 3. 核心知识地图

### 3.1 IOC 与 DI

- IOC：控制反转
- DI：依赖注入

本质：

- 对象不是你手动 `new`
- 容器帮你创建对象并注入依赖

常见注解：

- `@Component`
- `@Service`
- `@Repository`
- `@RestController`

### 3.2 配置类与自动配置

- `application.yml`
- `application-local.yml`
- `application-dev.yml`
- `@ConfigurationProperties`
- `@Bean`

项目里这部分很重要，因为你接了很多基础设施：

- MySQL
- Redis
- RabbitMQ
- MinIO
- LLM 配置

### 3.3 Web 开发

- `@RestController`
- `@RequestMapping`
- `@GetMapping`
- `@PostMapping`
- `@RequestBody`
- `@PathVariable`
- `@RequestParam`

你的平台本质上就是一组 HTTP API 加前端页面。

### 3.4 参数校验

- `@Valid`
- `@NotNull`
- `@NotBlank`
- `@Size`

作用：

- 在请求进入业务层前先校验合法性
- 让接口更安全

### 3.5 全局异常处理

- 统一错误返回
- 统一状态码和 message
- 区分业务异常和系统异常

这能让前后端联调更稳定。

### 3.6 AOP

- 切面
- 横切逻辑
- 审计、日志、统一拦截

你现在项目里已经有审计相关能力，这类能力很适合用 AOP 思维去理解。

### 3.7 Actuator

- 健康检查
- 监控端点

你在 Docker 验证时已经用过：

- `/actuator/health`

### 3.8 异步处理

- `@Async`
- 线程池

项目中的解析任务、索引任务、消息消费，本质上都和异步思想有关。

## 4. Spring Boot 项目标准分层

你要建立这种典型分层认知：

- `controller`：接请求、回响应
- `service`：业务逻辑
- `mapper/repository`：数据访问
- `entity`：数据库实体
- `dto`：入参
- `vo`：出参

这是 Java 后端项目里最重要的工程习惯之一。

## 5. 在 KnowFlow 里的真实落点

### 模块划分

- `auth`：登录、JWT、用户鉴权
- `tenant`：租户相关业务
- `knowledge`：知识库、文档管理
- `parser`：解析任务、切片、异步处理
- `qa`：问答与检索
- `ticket`：工单
- `backflow`：知识回流
- `dashboard`：运营看板
- `audit`：审计日志

### 配置文件

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-dev.yml`

### 静态页面

- `src/main/resources/static/console`

说明这个项目当前是：

- 后端主导
- 静态资源前端管理台
- REST API 驱动业务

## 6. 你面试时要能讲清的点

### 这个项目为什么选 Spring Boot

- 上手快
- 生态成熟
- 整合 MySQL、Redis、RabbitMQ、Security 比较顺
- 适合做企业级后台系统

### 这个项目怎么分层

- Controller 负责路由和参数接收
- Service 负责业务编排
- Mapper 负责数据库访问
- Integration 负责对接外部能力，如对象存储、检索、模型

### 你在里面做了什么

你可以这样讲：

“我在这个项目里不仅做了业务模块，比如知识库、文档解析、问答转工单、知识回流，也逐步理解了 Spring Boot 的分层开发方式，包括配置文件管理、接口设计、异常处理、鉴权链路和异步任务整合。”

## 7. 常见面试题

- Spring Boot 和 Spring 有什么关系
- `@Component`、`@Service`、`@Controller` 的区别是什么
- Bean 是怎么被管理的
- `@Autowired` 背后做了什么
- Spring Boot 自动配置原理是什么
- 请求是怎么从 Controller 走到 Service 的
- 为什么要做全局异常处理
- `application.yml` 和多环境配置怎么设计

## 8. 小练习

1. 自己写一个简单的 `UserController`
2. 写一个 `UserService`
3. 写一个 `UserDTO` 和 `UserVO`
4. 给接口入参加上校验注解
5. 写一个统一异常处理器
6. 增加一个 `health` 测试接口

## 9. 学到什么程度算过关

- 能自己写一个增删改查接口
- 知道请求如何进入业务层
- 能解释 Spring Boot 分层结构
- 能看懂项目里大多数 controller 和 service 代码

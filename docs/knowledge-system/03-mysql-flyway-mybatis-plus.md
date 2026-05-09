# MySQL + Flyway + MyBatis-Plus 知识框架

## 1. 这三者在项目里的分工

- MySQL：真正存业务数据
- Flyway：管理数据库表结构演进
- MyBatis-Plus：让 Java 更方便地操作数据库

这是很多企业 Java 项目的常见组合。

## 2. MySQL 你要掌握什么

### 2.1 基础能力

- 库、表、行、列
- 主键、外键、唯一键
- `varchar`
- `bigint`
- `datetime`
- `decimal`
- `tinyint`

### 2.2 SQL 基础

- `select`
- `insert`
- `update`
- `delete`
- `where`
- `order by`
- `group by`
- `limit`

### 2.3 索引

- 主键索引
- 普通索引
- 联合索引

你至少要知道：

- 索引是为了加快查询
- 索引不是越多越好
- 写入会受索引数量影响

### 2.4 事务

- 原子性
- 一致性
- 隔离性
- 持久性

项目里很多操作要放在事务里，比如：

- 问答转工单
- 工单关闭后回流知识草稿
- 任务状态推进

## 3. Flyway 为什么重要

如果没有 Flyway，团队开发会有这些问题：

- 每个人本地表结构不一致
- 不知道数据库改过几次
- 上线时容易漏表或漏字段

Flyway 的作用就是：

- 用脚本管理数据库结构版本
- 保证数据库变更可追踪、可回放

## 4. Flyway 在项目里的落点

迁移脚本目录：

- `src/main/resources/db/migration`

当前项目已经有：

- `V1__init_core_tables.sql`
- `V2__init_auth_tables.sql`
- `V3__add_knowledge_chunk_table.sql`
- `V4__init_qa_tables.sql`
- `V5__init_ticket_tables.sql`
- `V6__init_knowledge_draft_table.sql`
- `V7__init_audit_log_table.sql`
- `V8__init_dead_letter_and_chunk_index_tables.sql`

这正是你面试里可以讲的工程化亮点：

- 不是直接手改数据库
- 是用版本化 SQL 迁移脚本管理演进

## 5. MyBatis-Plus 你要掌握什么

### 5.1 它解决什么问题

JDBC 太底层，手写很麻烦。

MyBatis-Plus 帮你减少样板代码，比如：

- 基本 CRUD
- 条件构造器
- 分页

### 5.2 常见概念

- `Mapper`
- `Entity`
- `LambdaQueryWrapper`
- `Page`

### 5.3 你在项目中会频繁看到的写法

- `selectById`
- `insert`
- `updateById`
- `selectList`
- `selectPage`
- `LambdaQueryWrapper`

## 6. 数据建模能力

你要从“会写 SQL”升级到“会设计表”。

### 当前项目的核心业务表

- 租户表
- 用户表
- 角色表
- 用户角色关联表
- 知识库表
- 文档表
- 解析任务表
- 知识分片表
- 问答会话表
- 问答消息表
- 检索记录表
- 工单表
- 知识草稿表
- 死信消息表
- 审计日志表

### 表设计时要考虑

- 主键怎么设计
- 状态字段怎么设计
- 逻辑删除是否需要
- 创建时间和更新时间是否保留
- 是否有租户隔离字段

## 7. 项目中的关键设计思想

### 7.1 多租户字段

很多表里会有 `tenant_id`。

它的意义是：

- 同一套系统服务多个租户
- 数据隔离要靠租户维度约束

### 7.2 状态流转

很多业务表不是只存一条静态数据，而是有状态变化：

- 文档解析状态
- 文档索引状态
- 工单状态
- 死信回放状态

### 7.3 审计和追踪

企业级后台通常会保留：

- `created_by`
- `updated_by`
- `created_at`
- `updated_at`

## 8. 面试常问什么

- MySQL 索引底层原理是什么
- 联合索引最左前缀原则是什么
- 事务的四大特性是什么
- 什么情况下会产生慢 SQL
- Flyway 是做什么的
- MyBatis 和 MyBatis-Plus 有什么区别
- 你为什么不用 JPA
- 分页查询怎么做

## 9. 你怎么结合项目回答

可以这样说：

“这个项目的数据层采用 MySQL + Flyway + MyBatis-Plus。MySQL 负责存储租户、知识库、文档、解析任务、问答、工单和知识回流数据；Flyway 用于版本化管理表结构；MyBatis-Plus 则用于提升 CRUD 和分页开发效率。因为项目有多租户和任务状态流转，所以在表设计上我比较关注 `tenant_id`、状态字段、审计字段和索引设计。”

## 10. 小练习

1. 自己设计一个 `user` 表
2. 再设计一个 `user_role` 表
3. 给常用查询字段补索引
4. 写一条 Flyway 迁移脚本
5. 写一个 `UserMapper`
6. 用 `LambdaQueryWrapper` 查某个用户名

## 11. 过关标准

- 能看懂当前项目的核心表结构
- 能解释为什么要用 Flyway
- 能自己写简单 SQL 和分页查询
- 能看懂 MyBatis-Plus 的常见查询代码

# Java 基础知识框架

## 1. 为什么先学 Java 基础

Spring Boot、Redis、RabbitMQ、MySQL 这些技术虽然重要，但它们都建立在 Java 基础之上。

如果 Java 基础不稳，常见问题会很多：

- 看得懂业务代码，但改不动
- 知道注解怎么写，但不理解对象是怎么流转的
- 接口能跑起来，但定位不了空指针、线程问题、集合问题
- 面试时只能背框架，不会解释语言层面的原理

所以 Java 基础不是“可选项”，而是整个后端开发的底座。

## 2. 学习目标

你至少要达到下面这个水平：

- 能独立看懂项目中的实体类、DTO、VO、Service、Controller
- 能理解集合、异常、泛型、IO、线程这些基础能力
- 能自己写出常见的业务处理逻辑
- 面试时能把“代码为什么这样写”说清楚

## 3. 核心知识地图

### 3.1 基础语法

- 变量、数据类型、运算符、流程控制
- 方法定义、参数传递、返回值
- 引用类型和值类型的区别

你要重点理解：

- `int`、`long`、`double`、`boolean`
- `String`
- `null`
- `==` 和 `equals`

### 3.2 面向对象

- 类与对象
- 封装、继承、多态
- 接口与抽象类
- 构造方法
- `public / private / protected`

项目里最常见的体现：

- DTO、VO、Entity 都是类
- Service 接口和实现类体现接口编程
- 不同模块之间通过对象传递数据

### 3.3 常用类

- `String`
- `StringBuilder`
- `BigDecimal`
- `LocalDateTime`
- `Optional`
- `Objects`

在后端项目里很常用：

- 金额或分值建议用 `BigDecimal`
- 时间一般用 `LocalDateTime`
- 拼接文本用 `StringBuilder`

### 3.4 集合框架

- `List`
- `Set`
- `Map`
- `ArrayList`
- `HashMap`
- `HashSet`

必须掌握：

- 什么时候用 `List`
- 什么时候用 `Map`
- 去重为什么常用 `Set`
- 遍历集合的几种方式

### 3.5 泛型

- `List<String>`
- `Map<Long, String>`
- 泛型方法
- 通配符基础理解

你至少要知道：

- 泛型是为了类型安全
- 不写泛型容易在运行期报类型转换错误

### 3.6 异常处理

- `try-catch-finally`
- `throw`
- `throws`
- 受检异常与运行时异常
- 自定义业务异常

这个项目里你要重点观察：

- `BizException`
- 不同错误码如何返回给前端
- 什么时候抛业务异常而不是直接返回 `null`

### 3.7 IO 与编码

- 文件读写
- 字节流与字符流
- UTF-8 编码
- BOM

这次项目中你已经真实碰到过：

- 中文乱码
- BOM 污染
- 控制台编码不一致

所以这一块不只是理论，已经是你的真实项目经验。

### 3.8 并发基础

- 线程和进程
- `synchronized`
- 线程池
- 异步任务
- 并发安全

你现在不需要一开始学得特别深，但至少要理解：

- 为什么异步处理能提升吞吐
- 为什么多线程下要注意共享数据

### 3.9 Lambda 与 Stream

- Lambda 表达式
- `map`
- `filter`
- `collect`
- `toList`

Spring Boot 项目里非常常见，尤其在：

- 列表转换
- 查询结果映射
- 聚合统计

## 4. 这些知识在项目里的落点

### 常见对象模型

- `entity`：数据库实体
- `dto`：入参对象
- `vo`：返回对象

### 常见语言能力落点

- `StringBuilder`：拼接问答上下文
- `List`：承载检索结果、任务列表、分页结果
- `Map`：配置映射、统计聚合
- `LocalDateTime`：任务时间、审计日志时间
- 异常：鉴权失败、数据不存在、解析失败

## 5. 你当前最该掌握的 Java 基础点

按优先级排序：

1. 类、对象、封装
2. 集合 `List / Map / Set`
3. `String` 和 `StringBuilder`
4. 异常处理
5. 泛型
6. 时间类
7. Stream 基础
8. IO 与 UTF-8
9. 并发基础

## 6. 面试怎么问

常见问题：

- `==` 和 `equals` 的区别是什么
- `ArrayList` 和 `LinkedList` 的区别是什么
- `HashMap` 为什么查询快
- `String` 为什么不可变
- `try-catch-finally` 的执行顺序是什么
- `checked exception` 和 `runtime exception` 有什么区别
- `List`、`Set`、`Map` 的使用场景是什么
- Java 里为什么要有泛型

## 7. 面试怎么结合项目回答

示例：

“在我的知识服务平台项目里，我大量用到了 Java 集合和对象建模。比如检索结果会先封装成 `List<KnowledgeSearchHit>`，再映射成前端展示对象；解析任务、工单、知识草稿这些业务对象也都是通过 Entity、DTO、VO 分层处理的。另外我在文档解析和 smoke test 中还处理过 UTF-8 BOM 和乱码问题，所以对 Java 的 IO 和编码也有比较直接的实践。”

## 8. 小练习

建议你自己练：

1. 自己写一个 `User` 类和 `Role` 类
2. 用 `List` 保存多个用户
3. 用 `Map<Long, User>` 通过 id 查用户
4. 写一个方法过滤出管理员用户
5. 写一个自定义异常 `UserNotFoundException`
6. 用 UTF-8 读写一个 Markdown 文件

## 9. 学完后的判断标准

如果你能做到下面这些，就算入门过关：

- 看懂项目中的大部分 Java 代码
- 能独立新增一个简单接口
- 能定位基本的空指针和集合问题
- 能说清楚常见 Java 基础面试题

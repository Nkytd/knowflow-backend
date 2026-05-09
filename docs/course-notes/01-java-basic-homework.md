# 第 1 课作业：Java 基础

## 0. 这份作业怎么做

这份作业建议你按下面顺序来做：

1. 先独立完成，不要立即看答案
2. 每道题都自己在 IDEA 里运行
3. 做完后再对照参考答案
4. 如果答案能看懂但自己写不出来，就隔一天再重做一遍

这份作业分成四档：

- A 级：语法入门题
- B 级：面向对象题
- C 级：集合、异常、IO 题
- D 级：贴近 `KnowFlow` 项目的综合题

## 1. A 级：语法入门题

### A1. 变量与输出

要求：

- 定义变量 `name`、`age`、`score`
- 输出一句完整的话，例如：
  `我叫 Tom，今年 23 岁，本次成绩 95.5`

### A2. 分支判断

要求：

- 定义一个整数变量 `score`
- 根据分数输出：
  - `优秀`：90 分及以上
  - `良好`：80 到 89
  - `及格`：60 到 79
  - `不及格`：60 以下

### A3. 循环求和

要求：

- 使用 `for` 循环计算 `1 + 2 + 3 + ... + 100`
- 输出最终结果

### A4. 偶数过滤

要求：

- 使用 `for` 循环打印 `1` 到 `20`
- 只打印偶数

### A5. 方法封装

要求：

- 写一个方法 `isEven(int num)`
- 如果传入的是偶数，返回 `true`
- 否则返回 `false`

## 2. B 级：面向对象题

### B1. 编写 `User` 类

要求：

- 定义 `User` 类
- 包含字段：
  - `id`
  - `username`
  - `role`
- 写一个方法 `printInfo()`，输出用户信息

### B2. 增加构造方法

要求：

- 给 `User` 类增加构造方法
- 创建两个不同的用户对象
- 调用 `printInfo()`

### B3. 封装字段

要求：

- 把 `username` 改成 `private`
- 提供 `getUsername()` 和 `setUsername()`
- 在 `setUsername()` 中做校验：
  - 不能为空
  - 不能是空白字符串

### B4. 继承练习

要求：

- 定义父类 `Person`
- 包含字段 `name`
- 定义子类 `Employee`
- 增加字段 `jobTitle`
- 在子类中补充一个打印方法

### B5. 接口练习

要求：

- 定义接口 `MessageSender`
- 定义方法 `send(String content)`
- 创建两个实现类：
  - `EmailSender`
  - `SmsSender`
- 分别实现发送逻辑

## 3. C 级：集合、异常、IO 题

### C1. `List` 基础

要求：

- 创建一个 `List<String>`
- 放入 5 个文档名
- 遍历打印所有文档名

### C2. `Set` 去重

要求：

- 创建一个 `Set<String>`
- 放入几条重复标签
- 输出集合内容
- 观察重复值是否被去掉

### C3. `Map` 查询

要求：

- 创建一个 `Map<Long, String>`
- 键是用户 id，值是用户名
- 放入 3 条数据
- 根据 id 查出某个用户名并打印

### C4. 自定义异常使用

要求：

- 写一个方法 `validateUsername(String username)`
- 如果用户名为空，抛出 `IllegalArgumentException`
- 在 `main` 方法中调用并用 `try-catch` 处理

### C5. 文件写入

要求：

- 使用 UTF-8 把一段中文内容写到 `demo.md`
- 内容可以是：
  `这是我的第一份 Java 文件写入练习。`

### C6. 文件读取

要求：

- 再把 `demo.md` 读出来
- 打印到控制台

### C7. `StringBuilder` 拼接

要求：

- 用 `StringBuilder` 拼接一条日志
- 内容类似：
  `文档 vpn-guide.txt 解析成功，耗时 120 ms`

## 4. D 级：贴近项目的综合题

### D1. 定义 `KnowledgeDocument` 类

要求：

- 定义字段：
  - `id`
  - `docName`
  - `status`
  - `createdAt`
- 提供构造方法
- 提供 `printSummary()` 方法

### D2. 定义文档状态枚举

要求：

- 定义枚举 `DocumentStatus`
- 包含：
  - `PENDING`
  - `PROCESSING`
  - `SUCCESS`
  - `FAILED`

然后把 `KnowledgeDocument` 中的 `status` 从字符串改成枚举。

### D3. 模拟文档列表

要求：

- 创建 `List<KnowledgeDocument>`
- 放入 3 条文档
- 打印所有文档摘要

### D4. 过滤成功文档

要求：

- 使用 `Stream`
- 从文档列表中过滤出状态为 `SUCCESS` 的文档
- 输出这些文档名

### D5. 文档状态校验

要求：

- 为 `KnowledgeDocument` 增加方法 `markSuccess()`
- 如果当前状态已经是 `SUCCESS`，就直接返回
- 如果当前状态是 `FAILED`，允许改成 `SUCCESS`
- 如果对象为空引用，调用时会发生什么？请自己试并解释

### D6. 模拟问答上下文拼接

要求：

- 定义一个方法 `buildContext(List<String> snippets)`
- 使用 `StringBuilder`
- 把多个片段拼成下面类似格式：

```text
1. VPN 登录故障排查
2. 证书导入说明
3. DNS 检查步骤
```

### D7. 模拟参数校验

要求：

- 写一个方法 `createDocument(String docName)`
- 当 `docName` 为空或空白时抛异常
- 正常时返回一个 `KnowledgeDocument`

### D8. 思考题

请不要只写代码，还要写出你自己的理解：

1. 为什么文档状态适合用枚举，而不是随便用字符串？
2. 为什么问答上下文拼接适合用 `StringBuilder`？
3. 为什么文件读写必须明确指定 UTF-8？
4. 为什么项目里大量数据不是用数组，而是用 `List`？
5. 为什么对象比较内容时一般不用 `==`？

## 5. 建议提交方式

如果你要自己练，推荐这样组织代码：

- `A1Demo.java`
- `A2Demo.java`
- `User.java`
- `Employee.java`
- `KnowledgeDocument.java`
- `HomeworkMain.java`

或者你也可以按题目分 package。

## 6. 这份作业的目标

你做完这份作业后，应该达到下面这个程度：

1. 基本语法不再陌生
2. 面向对象有初步感觉
3. 会用 `List`、`Set`、`Map`
4. 知道异常和 UTF-8 在真实开发里为什么重要
5. 能把 Java 基础迁移到 `KnowFlow` 这种项目语境里

## 7. 做完后怎么复盘

建议你按下面三个问题复盘：

1. 哪些题我能独立写出来？
2. 哪些题我看答案能懂，但自己写不出来？
3. 哪些题我连题意都需要重新理解？

如果一题属于第 2、3 种情况，那就说明这一块还没真正掌握。

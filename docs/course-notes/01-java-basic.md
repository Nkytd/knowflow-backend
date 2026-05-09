# 第 1 课：Java 基础

## 0. 这份讲义适合谁

这份讲义默认你是初学者。

也就是说，我会按下面这个标准来写：

- 你可能刚接触 Java
- 你知道一点代码，但知识不成体系
- 你希望后面能看懂并开发 `KnowFlow` 这个项目
- 你希望不仅能“会写一点”，还希望能用于实习面试

这不是一份只给你结论的速查表，而是一份尽量像“老师上课”一样的讲义。

## 1. 学完这一课，你应该掌握什么

学完这份文档后，你应该至少能够做到：

1. 能写出最基本的 Java 程序
2. 理解变量、数据类型、分支、循环、方法
3. 理解类、对象、封装、继承、接口这些面向对象基础
4. 看懂 `List`、`Map`、异常处理、泛型这些项目中常见写法
5. 知道这些知识在 `KnowFlow` 项目里分别落在哪里

## 1.1 建议你怎么使用这份讲义

这份讲义最好的使用方式不是“从头翻到尾”，而是按下面节奏学习：

### 第一遍：建立大框架

目标不是全部记住，而是先知道：

- Java 到底在学什么
- 后端开发里最常用的基础模块有哪些

这一遍你可以：

- 快速读完整份讲义
- 遇到不懂的地方先做标记
- 不要卡死在某一个小知识点上

### 第二遍：边敲边学

这一遍要开始动手。

建议你：

- 每看到一个例子就自己在 IDEA 敲一遍
- 删掉一部分代码后试着自己补全
- 看看如果改错，会报什么错误

### 第三遍：带着项目去回看

这时你已经不是“纯学语言”，而是要开始把语言和项目联系起来。

建议你：

- 回到 `KnowFlow` 项目中找真实代码
- 观察 `Entity / DTO / VO / Service / Controller`
- 思考这份讲义里的每个知识点在项目里怎么出现

## 1.2 学这一课时，你要重点建立的五种能力

很多初学者误以为 Java 基础就是记语法。

其实真正重要的是下面这五种能力：

### 1.2.1 读代码能力

能看懂：

- 变量
- 条件判断
- 循环
- 方法调用
- 对象之间的关系

### 1.2.2 写代码能力

能自己写出：

- 简单类
- 简单方法
- 基本业务判断

### 1.2.3 拆问题能力

比如看到一个需求时，能拆成：

- 需要哪些数据
- 用什么类表示
- 需要哪些方法
- 哪一步要做判断

### 1.2.4 调错能力

遇到报错时，至少能先判断是：

- 语法错误
- 空指针
- 类型错误
- 越界
- 编码问题

### 1.2.5 项目迁移能力

也就是：

- 学到的 Java 基础，不是停留在小练习里
- 而是能迁移到 `KnowFlow` 这样的真实项目中

## 2. Java 是什么

Java 是一门面向对象编程语言，广泛用于：

- 企业后台系统
- Web 服务
- 中间件
- Android 早期开发
- 大型业务系统

你现在做的 `KnowFlow` 项目，本质上就是一个 Java 后端项目。

所以你学 Java，不是为了做题，而是为了做真实系统。

### 2.1 Java 的几个核心特点

如果面试官让你简要介绍 Java，可以从下面几个点切入：

1. 跨平台
2. 面向对象
3. 自动内存管理
4. 强类型
5. 生态成熟

#### 2.1.1 跨平台

Java 最经典的一句话是：

- 一次编写，到处运行

它真正的含义是：

- Java 源码会先编译成平台无关的字节码
- 不同操作系统上有各自的 JVM
- JVM 再把字节码转换成本平台能执行的机器指令

所以要记住：

- 跨平台依赖的是“字节码 + JVM”这套机制

#### 2.1.2 面向对象

Java 从语言设计上非常强调面向对象。

你后面在项目里会大量看到：

- 类
- 对象
- 封装
- 继承
- 接口
- 多态

比如 `KnowFlow` 中的：

- `Entity`
- `DTO`
- `VO`
- `Service`
- `Controller`

本质上都体现了面向对象思想。

#### 2.1.3 自动内存管理

Java 有垃圾回收机制（GC），会自动回收不再使用的对象。

这带来的好处是：

- 降低手动释放内存的复杂度
- 减少很多内存泄漏和野指针问题

但要注意：

- 自动回收不等于可以随便创建对象

如果对象创建过多、集合无限增长、缓存不受控，系统照样会出问题。

#### 2.1.4 强类型

强类型的意思是：

- 变量类型必须明确
- 类型不匹配时，编译器通常会直接报错

例如：

```java
int age = 18;
// age = "hello"; // 编译错误
```

这会让代码更安全、更容易维护。

#### 2.1.5 生态成熟

Java 在企业开发里流行很多年，一个重要原因就是生态成熟。

你现在这个项目已经用到了：

- Spring Boot
- MyBatis-Plus
- Flyway
- Redis
- RabbitMQ
- MinIO

这也是 Java 后端的优势之一。

### 2.2 Java 程序是怎么运行起来的

一个 Java 程序从源码到运行，大致会经历下面几步：

1. 你写 `.java` 源代码
2. 编译器 `javac` 把它编译成 `.class` 字节码
3. JVM 加载这些字节码
4. JVM 用解释器或 JIT 编译器把字节码转成机器指令
5. 程序开始执行

可以把这个过程理解成：

```text
.java 源码
   ↓ javac
.class 字节码
   ↓ JVM
解释执行 / JIT 编译
   ↓
机器指令
   ↓
程序运行
```

### 2.3 JDK、JRE、JVM 三者关系

这是 Java 入门和面试中的经典问题。

#### JVM

JVM = Java Virtual Machine，Java 虚拟机。

职责包括：

- 加载字节码
- 执行字节码
- 管理内存
- 执行垃圾回收

#### JRE

JRE = Java Runtime Environment，Java 运行环境。

它包含：

- JVM
- Java 程序运行所需的基础类库

#### JDK

JDK = Java Development Kit，Java 开发工具包。

它包含：

- JRE
- 编译器 `javac`
- 调试和开发工具

所以关系可以记为：

```text
JDK > JRE > JVM
```

### 2.4 为什么 Java 既像编译型语言，又像解释型语言

先说编译型的一面：

- Java 源码会先经过 `javac` 编译成 `.class`

再说解释型的一面：

- `.class` 不是机器码
- 需要 JVM 来解释执行

再进一步：

- JVM 对热点代码会做 JIT（即时编译）
- 把高频执行代码编译成机器码，提高性能

所以更准确的说法是：

- Java 是“编译 + 解释 + 即时编译”的混合模式

### 2.5 这些概念和项目有什么关系

你现在的 `KnowFlow` 项目里：

- 源代码是 `.java`
- Maven 会把它编译成字节码并打包成 `.jar`
- IDEA 启动项目，本质上是 JVM 在运行这些字节码
- Docker 容器里执行 `java -jar /app/app.jar`，底层仍然是 JVM 在工作

## 3. 为什么 Java 基础很重要

很多同学一开始会觉得：

- 我是不是直接学 Spring Boot 就行了
- 我是不是直接照着教程写接口就行了

答案是：不行。

因为真正写后端时，你每天都在使用 Java 基础：

- Controller 接收入参，本质是对象和方法
- Service 编排业务，本质是类、集合、异常、分支判断
- Mapper 查询数据，本质是对象映射
- 异步任务，本质是线程和并发思想
- 文档读取，本质是 IO 和编码

所以 Java 基础不是“前置理论”，而是你整个开发过程里的地基。

## 4. 第一个 Java 程序

先看一个最简单的例子：

```java
public class HelloJava {
    public static void main(String[] args) {
        System.out.println("Hello, Java!");
    }
}
```

### 4.1 这段代码是什么意思

- `public class HelloJava`
  表示定义了一个公开的类，类名叫 `HelloJava`
- `public static void main(String[] args)`
  表示程序的主入口
- `System.out.println(...)`
  表示向控制台打印内容

### 4.2 为什么 `main` 方法这么重要

因为 Java 程序运行时，需要一个固定入口。

这个入口就是：

```java
public static void main(String[] args)
```

你可以把它理解成：

- 程序从这里开始执行

### 4.3 如果你用 IDEA 运行

只需要：

1. 新建一个 Java 类
2. 写上面的代码
3. 点击运行按钮

### 4.4 如果你用命令行运行

假设文件名叫 `HelloJava.java`

```powershell
javac HelloJava.java
java HelloJava
```

### 4.5 从“写代码”到“程序跑起来”的课堂图解

你可以把 Java 程序运行过程想象成一条加工流水线：

```text
你写的源码（.java）
        ↓
编译器 javac
        ↓
字节码文件（.class）
        ↓
JVM 加载执行
        ↓
程序输出结果
```

这张图的价值在于，它能帮助你理解很多问题：

- 为什么 Java 需要 JDK
- 为什么程序不是直接运行 `.java`
- 为什么 `.jar` 本质上还是给 JVM 跑的
- 为什么“环境问题”常常和 JDK/JVM 有关

### 4.6 初学者最容易混淆的三件事

#### 第一件：我写的是 Java，为什么运行时看到的是 JVM

因为：

- 你写的是源代码
- 真正执行的是 JVM

#### 第二件：我已经安装 IDEA，为什么还要安装 JDK

因为 IDEA 只是开发工具，不是 Java 运行时本身。

真正负责编译和运行 Java 的，是 JDK。

#### 第三件：为什么 Maven 打出来的是 `.jar`

因为 Java 项目交付时，常常不会直接交 `.java` 源码，而是：

- 编译
- 打包
- 交给 JVM 运行

这就是你后面在 Docker 里看到 `java -jar /app/app.jar` 的原因。

## 5. 变量与数据类型

### 5.1 什么是变量

变量就是程序里“存数据的地方”。

例如：

```java
int age = 23;
String name = "Tom";
boolean active = true;
```

上面这三行表示：

- `age` 存年龄
- `name` 存名字
- `active` 存真假状态

### 5.2 Java 的常见数据类型

Java 数据类型分两大类：

1. 基本数据类型
2. 引用数据类型

### 5.3 基本数据类型

最常见的是这几个：

| 类型 | 说明 | 示例 |
|---|---|---|
| `int` | 整数 | `18` |
| `long` | 更大的整数 | `100000L` |
| `double` | 小数 | `95.6` |
| `boolean` | 真或假 | `true` |
| `char` | 单个字符 | `'A'` |

上面这张表对入门够用，但如果你要面向后续面试和工程开发，必须把 8 种基本数据类型补完整。

### 5.3.1 八种基本数据类型完整表

| 类型 | 字节数 | 说明 | 示例 |
|---|---:|---|---|
| `byte` | 1 | 很小的整数 | `100` |
| `short` | 2 | 较小整数 | `20000` |
| `int` | 4 | 最常用整数类型 | `18` |
| `long` | 8 | 更大的整数 | `100000L` |
| `float` | 4 | 单精度小数 | `3.14F` |
| `double` | 8 | 双精度小数 | `95.6` |
| `char` | 2 | 单个字符 | `'A'` |
| `boolean` | 不严格规定 | 真或假 | `true` |

这里有几个必须记住的点：

1. `int` 是整数默认首选
2. `long` 字面量后面通常要加 `L`
3. `float` 字面量后面通常要加 `F`
4. `double` 是小数默认首选
5. `char` 用单引号
6. `String` 不是基本类型，而是引用类型

### 5.3.2 哪些类型最常用

在后端开发里，真正最常见的是：

- `int`
- `long`
- `double`
- `boolean`
- `char`

但在真实业务项目里，`byte` 和 `short` 用得并不多。

所以你学习时要区分：

- 面试知识上要知道它们
- 业务开发中不一定常写它们

### 5.3.3 基本数据类型的取值范围要不要背

你不需要一开始把每个最小值最大值背得滚瓜烂熟，但至少要知道：

- `byte` 很小
- `short` 比 `byte` 大
- `int` 是最常用整数
- `long` 更大
- `float`、`double` 是浮点数，会有精度问题

如果你想进一步熟悉，可以看一眼这些典型范围：

| 类型 | 大致范围 |
|---|---|
| `byte` | `-128 ~ 127` |
| `short` | `-32768 ~ 32767` |
| `int` | 约 `-21 亿 ~ 21 亿` |
| `long` | 非常大，远大于 `int` |

### 5.3.3.1 `float` 和 `double` 怎么选

很多初学者只知道它们都能表示小数，但不知道为什么代码里几乎总是看到 `double`。

你先记住这几点：

- `float` 占 `4` 字节
- `double` 占 `8` 字节
- `double` 精度更高
- Java 里的小数字面量默认就是 `double`

示例：

```java
double score = 95.5;
float rate = 0.85F;
```

注意：

- `float` 字面量后面一般要写 `F`
- 不写 `F`，默认会被当成 `double`

所以在普通业务开发里：

- 小数默认优先使用 `double`
- 只有明确场景才会主动选 `float`

### 5.3.4 为什么要知道“占几个字节”

这个问题在入门时看起来有点“理论”，但其实很有价值。

至少有三个作用：

1. 帮你理解为什么有的数据会溢出
2. 帮你理解为什么有时候必须强转
3. 帮你建立“类型不是随便选的”意识

例如：

```java
byte b = 127;
// b = 128; // 编译或运行结果会有问题
```

因为 `byte` 太小了，装不下更大的值。

### 5.3.5 成员变量的默认值

这个点初学者很容易忽略，但面试中会偶尔问到。

如果一个字段是类的成员变量，那么它有默认值。

例如：

```java
public class DefaultValueDemo {
    int age;
    long total;
    double score;
    boolean active;
    char grade;
    String name;
}
```

这些字段在没有手动赋值时，默认值分别是：

| 类型 | 默认值 |
|---|---|
| `byte` | `0` |
| `short` | `0` |
| `int` | `0` |
| `long` | `0L` |
| `float` | `0.0F` |
| `double` | `0.0` |
| `boolean` | `false` |
| `char` | `'\u0000'` |
| 引用类型 | `null` |

但是要注意：

- 局部变量没有默认值
- 局部变量必须先赋值再使用

示例：

```java
int count = 10;
long total = 3000000000L;
double score = 88.5;
boolean success = true;
char grade = 'A';
```

### 5.4 引用数据类型

你现在先记住这几个：

- `String`
- 数组
- 类
- 接口
- 集合

示例：

```java
String projectName = "KnowFlow";
```

### 5.4.1 基本类型和引用类型最本质的区别

你可以先建立一个非常重要的直觉：

- 基本类型变量里，保存的是值本身
- 引用类型变量里，保存的是对象引用

例如：

```java
int a = 10;
String name = "Tom";
```

这里：

- `a` 里就是整数值 `10`
- `name` 里保存的是一个字符串对象的引用

这个概念会影响你后面对：

- 参数传递
- `==`
- 判空
- 对象修改

这些问题的理解。

### 5.4.2 为什么 `null` 只能赋给引用类型

`null` 表示：

- 当前没有指向任何对象

例如：

```java
String name = null;
```

这是合法的，因为 `name` 是引用类型。

但下面不合法：

```java
// int age = null; // 编译错误
```

因为 `int` 是基本类型，不是对象引用。

### 5.5 `String` 为什么重要

因为后端项目里，字符串到处都是：

- 用户名
- token
- 文档内容
- 接口返回信息
- 状态说明

示例：

```java
String message = "文档解析成功";
System.out.println(message);
```

### 5.5.1 类型转换

Java 中常见的类型转换分两类：

1. 自动类型转换
2. 强制类型转换

#### 自动类型转换

当小范围类型赋值给大范围类型时，通常可以自动转换。

```java
int num = 100;
long bigNum = num;
double score = num;
```

#### 强制类型转换

当大范围类型赋值给小范围类型时，需要手动强转。

```java
double score = 95.8;
int intScore = (int) score;
System.out.println(intScore); // 95
```

这里的小数部分会直接被截掉。

#### 强转带来的风险

主要有两个：

1. 精度丢失
2. 数据溢出

例如：

```java
int largeNum = 300;
byte b = (byte) largeNum;
System.out.println(b);
```

输出不会是 `300`，因为 `byte` 的范围装不下这个值。

### 5.5.1.1 向上转型和向下转型

你这次特别指出这一点，非常对。上一版确实没有系统讲清。

注意：

- 前面讲的是“基本类型转换”
- 这里讲的是“引用类型转换”

这两个概念不要混在一起。

#### 什么是向上转型

向上转型指的是：

- 子类对象赋值给父类引用

示例：

```java
class Animal {
    public void speak() {
        System.out.println("动物发声");
    }
}

class Dog extends Animal {
    public void bark() {
        System.out.println("汪汪汪");
    }
}

public class UpCastDemo {
    public static void main(String[] args) {
        Animal animal = new Dog();
        animal.speak();
    }
}
```

这里：

- `Dog` 是子类
- `Animal` 是父类
- `Animal animal = new Dog();` 就是向上转型

为什么它常见？

因为这正是多态的基础。

#### 向上转型后能访问什么

你要记住一条非常重要的规则：

- 编译时看左边
- 运行时看右边

什么意思？

```java
Animal animal = new Dog();
```

这里变量左边的类型是 `Animal`，所以编译器只允许你访问 `Animal` 中声明的方法和属性。

也就是说：

```java
animal.speak(); // 可以
// animal.bark(); // 不可以，编译报错
```

因为 `bark()` 不是 `Animal` 类型中声明的方法。

#### 什么是向下转型

向下转型指的是：

- 把父类引用再转回子类引用

示例：

```java
Animal animal = new Dog();
Dog dog = (Dog) animal;
dog.bark();
```

这里：

- `(Dog) animal` 就是向下转型

#### 向下转型有什么风险

风险在于：

- 不是所有父类引用都真的指向这个子类对象

例如：

```java
Animal animal = new Animal();
Dog dog = (Dog) animal; // 运行时出错
```

这会抛出：

- `ClassCastException`

因为这个 `animal` 本质上并不是 `Dog` 对象。

#### 向下转型前为什么常用 `instanceof`

为了避免乱转型，通常先判断：

```java
if (animal instanceof Dog) {
    Dog dog = (Dog) animal;
    dog.bark();
}
```

这样更安全。

#### 这一块和项目有什么关系

在你后面的 Spring Boot 项目里，虽然你不一定手写很多显式转型代码，但“面向父接口编程”的思想会非常常见。

例如：

- `ChatModelClient` 接口
- 不同实现类
- `MessageSender` 接口

这些都和向上转型、多态、接口编程有关。

### 5.5.2 为什么很多场景要用 `BigDecimal`

很多初学者会问：

- 既然 `double` 能表示小数，为什么金额计算不用它？

因为 `double` 是浮点数，某些十进制小数无法被精确表示。

例如：

```java
public class DoubleDemo {
    public static void main(String[] args) {
        System.out.println(0.05 + 0.01);
        System.out.println(1.0 - 0.42);
    }
}
```

你可能会看到类似结果：

```text
0.060000000000000005
0.5800000000000001
```

这就是精度误差。

所以涉及以下场景时，要优先考虑 `BigDecimal`：

- 金额
- 价格
- 精确统计
- 分数计算

示例：

```java
import java.math.BigDecimal;

public class BigDecimalDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("0.05");
        BigDecimal b = new BigDecimal("0.01");
        BigDecimal result = a.add(b);
        System.out.println(result); // 0.06
    }
}
```

注意：

- 推荐 `new BigDecimal("0.05")`
- 不推荐 `new BigDecimal(0.05)`

因为后者会把 `double` 的误差一起带进去。

### 5.6 变量命名规范

你要养成好习惯：

- 变量名见名知意
- 使用小驼峰

好的例子：

```java
int pageNo = 1;
String documentName = "vpn-guide.txt";
boolean needHumanHandoff = false;
```

不好的例子：

```java
int a = 1;
String x = "abc";
boolean flag1 = true;
```

### 5.7 局部变量和成员变量

看例子：

```java
public class User {
    String username; // 成员变量

    public void printUser() {
        int age = 18; // 局部变量
        System.out.println(username + ", age=" + age);
    }
}
```

区别：

- 成员变量属于对象
- 局部变量属于方法内部

注意：

- 局部变量必须先赋值再使用
- 成员变量有默认值

### 5.8 给初学者建立一个“内存直觉”

很多同学一开始怕“内存模型”这个词，其实在 Java 基础阶段，你先建立一个朴素直觉就够了。

可以先粗略理解成：

- 方法运行时的一些局部数据，像是临时工作台
- `new` 出来的对象，像是放进了对象仓库
- 变量有时直接放值，有时只保存“去对象仓库找对象的线索”

你不用一开始就把 JVM 内存区讲到特别底层，但一定要有下面这个感觉：

```java
User user = new User();
user.name = "Tom";
```

这里发生了两件事：

1. 创建了一个 `User` 对象
2. 变量 `user` 持有了这个对象的引用

也就是说：

- `user` 不是整个对象本身
- 它更像是“找到这个对象的位置线索”

### 5.9 用图理解“变量、对象、引用”

看这段代码：

```java
User u1 = new User();
u1.name = "Tom";

User u2 = u1;
u2.name = "Jerry";
```

你可以把它想象成：

```text
u1 ─────┐
        ├──> 同一个 User 对象
u2 ─────┘        name = "Jerry"
```

所以最后：

- `u1.name` 也是 `"Jerry"`
- `u2.name` 也是 `"Jerry"`

这不是因为复制了两个对象，而是：

- 两个变量都指向了同一个对象

## 6. 运算符

### 6.1 算术运算符

```java
int a = 10;
int b = 3;

System.out.println(a + b); // 13
System.out.println(a - b); // 7
System.out.println(a * b); // 30
System.out.println(a / b); // 3
System.out.println(a % b); // 1
```

注意：

- `10 / 3` 结果是 `3`
- 因为两个整数相除，结果还是整数

### 6.2 比较运算符

```java
int score = 85;
System.out.println(score > 60);  // true
System.out.println(score == 85); // true
System.out.println(score != 90); // true
```

### 6.3 逻辑运算符

```java
boolean login = true;
boolean admin = false;

System.out.println(login && admin); // false
System.out.println(login || admin); // true
System.out.println(!admin);         // true
```

### 6.4 自增与自减

```java
int count = 1;
count++;
System.out.println(count); // 2
```

## 7. 分支与循环

### 7.1 `if` 语句

```java
int score = 75;

if (score >= 60) {
    System.out.println("及格");
} else {
    System.out.println("不及格");
}
```

### 7.2 `if-else if-else`

```java
int score = 92;

if (score >= 90) {
    System.out.println("优秀");
} else if (score >= 80) {
    System.out.println("良好");
} else if (score >= 60) {
    System.out.println("及格");
} else {
    System.out.println("不及格");
}
```

### 7.3 `switch`

```java
String status = "SUCCESS";

switch (status) {
    case "PENDING":
        System.out.println("待处理");
        break;
    case "SUCCESS":
        System.out.println("处理成功");
        break;
    case "FAILED":
        System.out.println("处理失败");
        break;
    default:
        System.out.println("未知状态");
}
```

在项目里，状态判断很常见，比如：

- 解析任务状态
- 文档状态
- 工单状态

### 7.4 `for` 循环

```java
for (int i = 1; i <= 5; i++) {
    System.out.println("第 " + i + " 次循环");
}
```

### 7.5 `while` 循环

```java
int i = 1;
while (i <= 3) {
    System.out.println(i);
    i++;
}
```

### 7.6 `break` 和 `continue`

```java
for (int i = 1; i <= 5; i++) {
    if (i == 3) {
        continue;
    }
    System.out.println(i);
}
```

输出：

```text
1
2
4
5
```

## 8. 方法

### 8.1 什么是方法

方法就是把一段逻辑封装起来，方便重复使用。

示例：

```java
public class MathDemo {
    public static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        int result = add(3, 5);
        System.out.println(result);
    }
}
```

### 8.2 方法的组成

```java
public static int add(int a, int b)
```

拆开理解：

- `public`：访问修饰符
- `static`：属于类本身
- `int`：返回值类型
- `add`：方法名
- `(int a, int b)`：参数列表

### 8.3 无返回值方法

```java
public static void printMessage(String message) {
    System.out.println(message);
}
```

这里的 `void` 表示没有返回值。

### 8.4 为什么方法很重要

因为后端开发本质上就是：

- Controller 调 Service 的方法
- Service 调 Mapper 的方法
- 各个工具类提供公共方法

### 8.5 Java 是值传递

初学者很容易混淆这个概念。

先记住：

- Java 传递的是“值”

示例：

```java
public class PassValueDemo {
    public static void change(int x) {
        x = 100;
    }

    public static void main(String[] args) {
        int a = 10;
        change(a);
        System.out.println(a); // 10
    }
}
```

为什么还是 `10`？

因为传进去的是 `a` 的值的副本，不会直接改掉原变量本身。

### 8.6 为什么很多人会误以为 Java 有“引用传递”

因为当你把对象传进方法后，方法里可以修改对象的属性。

看例子：

```java
class User {
    String name;
}

public class PassObjectDemo {
    public static void changeName(User user) {
        user.name = "Jerry";
    }

    public static void main(String[] args) {
        User user = new User();
        user.name = "Tom";

        changeName(user);
        System.out.println(user.name); // Jerry
    }
}
```

很多人看到这里会误以为：

- 这说明 Java 是引用传递

其实更准确的理解是：

- 传进去的是“引用值的副本”
- 这个副本和外面的变量，都指向同一个对象
- 所以通过它改对象内容，外部能看到变化

但是，如果你在方法里把参数重新指向一个新对象，外面不会跟着变。

```java
class User {
    String name;
}

public class ReassignDemo {
    public static void reset(User user) {
        user = new User();
        user.name = "New User";
    }

    public static void main(String[] args) {
        User user = new User();
        user.name = "Tom";

        reset(user);
        System.out.println(user.name); // 仍然是 Tom
    }
}
```

所以要把话说准确：

- Java 只有值传递
- 对象参数传递的是“引用值的副本”

## 9. 数组

### 9.1 什么是数组

数组是“固定长度、同一种类型的数据集合”。

```java
int[] scores = {90, 80, 70};
System.out.println(scores[0]); // 90
```

### 9.2 创建数组

```java
int[] nums = new int[3];
nums[0] = 10;
nums[1] = 20;
nums[2] = 30;
```

### 9.3 遍历数组

```java
int[] nums = {10, 20, 30};

for (int i = 0; i < nums.length; i++) {
    System.out.println(nums[i]);
}
```

### 9.3.1 数组的常见操作

初学者不能只会“定义数组”和“for 遍历”，还要知道数组在业务代码里最常见的几个动作。

#### 获取长度

```java
int[] nums = {10, 20, 30};
System.out.println(nums.length); // 3
```

注意：

- `length` 是数组的属性，不是方法
- 所以写法是 `nums.length`
- 不是 `nums.length()`

#### 修改指定位置元素

```java
String[] docs = {"a.txt", "b.txt", "c.txt"};
docs[1] = "b-new.txt";
System.out.println(docs[1]); // b-new.txt
```

#### 使用 `Arrays.toString()` 打印数组

如果你直接打印数组对象：

```java
int[] nums = {1, 2, 3};
System.out.println(nums);
```

你看到的通常不是想要的内容，而是类似对象地址信息。

更推荐这样打印：

```java
import java.util.Arrays;

int[] nums = {1, 2, 3};
System.out.println(Arrays.toString(nums)); // [1, 2, 3]
```

#### 排序

```java
import java.util.Arrays;

int[] scores = {90, 70, 85, 60};
Arrays.sort(scores);
System.out.println(Arrays.toString(scores)); // [60, 70, 85, 90]
```

#### 拷贝

```java
import java.util.Arrays;

int[] source = {1, 2, 3};
int[] target = Arrays.copyOf(source, source.length);

target[0] = 99;
System.out.println(Arrays.toString(source)); // [1, 2, 3]
System.out.println(Arrays.toString(target)); // [99, 2, 3]
```

这说明：

- `target` 是一个新数组
- 改 `target` 不会影响 `source`

### 9.3.2 数组最常见的两个错误

#### 下标越界

```java
int[] nums = {10, 20, 30};
// System.out.println(nums[3]); // ArrayIndexOutOfBoundsException
```

因为合法下标只有：

- `0`
- `1`
- `2`

也就是：

- 最大下标 = `length - 1`

#### 把数组长度和最后一个下标混淆

```java
int[] nums = {10, 20, 30};

for (int i = 0; i < nums.length; i++) {
    System.out.println(nums[i]);
}
```

这里必须是 `< nums.length`，不能写成 `<= nums.length`。

### 9.4 数组的局限

数组有两个明显问题：

- 长度固定
- 操作不够灵活

所以企业项目里更常用的是集合，比如 `List`。

## 10. 面向对象编程

这是 Java 的核心。

### 10.0 先建立一个“面向对象”的世界观

很多初学者学到这里会开始迷糊：

- 为什么前面还是变量、循环、方法
- 到这里突然开始讲类、对象、继承、接口

其实你可以把前面的知识理解成：

- 写程序的基础动作

而面向对象则是在回答：

- 现实世界中的东西，应该怎么在代码里组织

比如在 `KnowFlow` 项目里，现实中的对象有：

- 用户
- 租户
- 文档
- 解析任务
- 工单
- 问答消息

Java 不会让你用一堆零散变量去描述这些东西，而是鼓励你把它们组织成类和对象。

### 10.0.1 面向对象不是“语法”，而是一种组织代码的方法

比如你想描述一份文档：

你当然可以写成这样：

```java
Long id = 1L;
String docName = "vpn-guide.txt";
String status = "SUCCESS";
```

但这样的问题是：

- 这些变量是零散的
- 它们之间没有被明确绑定
- 后面传来传去会越来越乱

所以更好的做法是：

```java
public class Document {
    Long id;
    String docName;
    String status;
}
```

这就是面向对象最朴素的价值：

- 把相关的数据和行为组织在一起

### 10.1 什么是类，什么是对象

类可以理解为“设计图”，对象可以理解为“实例”。

比如：

- 类：用户类 `User`
- 对象：某一个具体用户 `tom`

### 10.2 定义一个类

```java
public class User {
    String username;
    int age;

    public void sayHello() {
        System.out.println("你好，我是 " + username);
    }
}
```

### 10.3 创建对象

```java
public class UserDemo {
    public static void main(String[] args) {
        User user = new User();
        user.username = "Tom";
        user.age = 23;
        user.sayHello();
    }
}
```

### 10.4 构造方法

构造方法用于创建对象时初始化数据。

```java
public class User {
    String username;
    int age;

    public User(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
```

使用：

```java
User user = new User("Tom", 23);
```

### 10.5 `this` 是什么

`this` 代表当前对象本身。

```java
public class User {
    String username;

    public User(String username) {
        this.username = username;
    }
}
```

这里表示：

- 把传进来的参数 `username`
- 赋值给当前对象的 `username`

### 10.6 封装

封装的核心思想是：

- 数据不要随便让外部乱改
- 通过方法控制访问

示例：

```java
public class User {
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        this.username = username;
    }
}
```

### 10.7 继承

继承表示“子类拥有父类的能力”。

```java
public class Person {
    protected String name;

    public void speak() {
        System.out.println("我是 " + name);
    }
}

public class Employee extends Person {
    private String jobTitle;
}
```

### 10.8 多态

多态简单理解就是：

- 父类引用指向子类对象

```java
Person p = new Employee();
```

它的意义是：

- 提高扩展性
- 面向抽象编程

### 10.9 抽象类

```java
public abstract class TaskWorker {
    public abstract void process();
}
```

抽象类特点：

- 不能直接 `new`
- 可以定义抽象方法
- 子类必须实现

### 10.10 接口

```java
public interface MessageSender {
    void send(String content);
}
```

实现类：

```java
public class EmailSender implements MessageSender {
    @Override
    public void send(String content) {
        System.out.println("发送邮件：" + content);
    }
}
```

在企业项目里，接口非常重要，因为它能让系统更容易扩展。

### 10.11 重载和重写

这是 Java 面向对象里的高频概念。

#### 10.11.1 重载（Overload）

重载发生在：

- 同一个类里
- 方法名相同
- 参数列表不同

示例：

```java
public class PrintService {
    public void print(String message) {
        System.out.println(message);
    }

    public void print(int value) {
        System.out.println(value);
    }

    public void print(String message, int times) {
        for (int i = 0; i < times; i++) {
            System.out.println(message);
        }
    }
}
```

#### 10.11.2 重写（Override）

重写发生在：

- 子类和父类之间
- 方法名相同
- 参数列表相同

示例：

```java
class Animal {
    public void makeSound() {
        System.out.println("动物发声");
    }
}

class Dog extends Animal {
    @Override
    public void makeSound() {
        System.out.println("汪汪汪");
    }
}
```

#### 10.11.3 一句话区分

- 重载：同名不同参
- 重写：子类改父类实现

### 10.12 抽象类和接口怎么理解

初学者很容易把抽象类和接口混在一起。

你可以这样记：

- 抽象类更像“半成品模板”
- 接口更像“能力规范”

#### 抽象类适合什么场景

如果一类对象之间：

- 有明显共性
- 还想复用一部分公共实现

那适合抽象类。

#### 接口适合什么场景

如果你更关心：

- 只要实现这个接口，就说明具备某种能力

那适合接口。

#### 一个很实用的经验

在企业项目中：

- 抽公共逻辑，常考虑抽象类
- 做扩展点设计，常考虑接口

## 11. 访问修饰符

Java 常见访问修饰符：

| 修饰符 | 含义 |
|---|---|
| `public` | 所有人都能访问 |
| `private` | 只能本类访问 |
| `protected` | 本类、子类、同包可访问 |
| 默认不写 | 同包可访问 |

初学阶段先记两条：

1. 成员变量尽量 `private`
2. 对外提供方法时，再根据需要决定是否 `public`

## 12. `static`、`final`

### 12.1 `static`

`static` 表示“属于类本身，而不是某个对象”。

```java
public class UserUtil {
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
```

调用：

```java
boolean result = UserUtil.isBlank("abc");
```

### 12.2 `final`

`final` 常见作用：

- 修饰变量：不能再改
- 修饰方法：不能被重写
- 修饰类：不能被继承

```java
final int maxRetry = 3;
```

## 13. `String`、`==`、`equals`

这是 Java 初学者最容易出错的地方之一。

### 13.1 `==` 比较什么

- 基本类型：比较值
- 引用类型：比较地址

### 13.2 `equals` 比较什么

通常比较对象内容是否相同。

示例：

```java
String a = new String("hello");
String b = new String("hello");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

结论：

- 比较字符串内容时，优先用 `equals`

### 13.3 为什么 `String` 常配合 `StringBuilder`

因为字符串拼接过多会产生额外对象。

示例：

```java
StringBuilder builder = new StringBuilder();
builder.append("文档：");
builder.append("vpn-guide.txt");
System.out.println(builder.toString());
```

这在你项目里的问答上下文拼接中就很常见。

### 13.4 为什么 `String` 是不可变的

`String` 不可变，指的是：

- 一个字符串对象创建后
- 它内部的内容不能被修改

看起来像“改了字符串”，其实往往是创建了新对象：

```java
String name = "Tom";
name = name + " Cat";
```

这里不是在原对象上追加，而是生成了新的字符串对象，再让变量重新指向它。

#### 为什么要这么设计

主要好处有：

1. 更安全
2. 适合做常量
3. 可以复用字符串常量池
4. 更适合作为 `HashMap` 的 key

### 13.5 什么是字符串常量池

看例子：

```java
String a = "hello";
String b = "hello";

System.out.println(a == b); // true
```

为什么这里是 `true`？

因为字符串字面量会优先放在字符串常量池里复用。

再看：

```java
String a = new String("hello");
String b = new String("hello");

System.out.println(a == b); // false
```

因为这里显式创建了两个不同对象。

### 13.6 `String`、`StringBuilder`、`StringBuffer` 的区别

这是经典面试题，也是实际开发中的常见知识点。

#### `String`

- 不可变
- 线程安全
- 频繁拼接时效率较低

#### `StringBuilder`

- 可变
- 非线程安全
- 单线程字符串拼接时性能最好

#### `StringBuffer`

- 可变
- 线程安全
- 因为有同步开销，通常慢于 `StringBuilder`

#### 怎么选

- 普通字符串：`String`
- 单线程频繁拼接：`StringBuilder`
- 多线程且确实需要同步：`StringBuffer`

### 13.7 `String` 常用方法与示例

如果只知道“`String` 不可变”，那还远远不够。你至少要会写常见字符串处理。

下面这些方法，在项目和面试里都非常高频。

#### 13.7.1 `length()`

```java
String name = "KnowFlow";
System.out.println(name.length()); // 8
```

作用：

- 返回字符串长度

#### 13.7.2 `charAt(int index)`

```java
String name = "Java";
System.out.println(name.charAt(0)); // J
```

作用：

- 取指定位置字符

注意：

- 下标从 `0` 开始
- 越界会抛异常

#### 13.7.3 `substring()`

```java
String text = "knowledge-base";
System.out.println(text.substring(0, 9)); // knowledge
System.out.println(text.substring(10));   // base
```

规则：

- `substring(beginIndex, endIndex)` 左闭右开
- 包含开始位置
- 不包含结束位置

#### 13.7.4 `equals()` 和 `equalsIgnoreCase()`

```java
String a = "SUCCESS";
String b = "success";

System.out.println(a.equals(b));           // false
System.out.println(a.equalsIgnoreCase(b)); // true
```

什么时候用：

- 严格区分大小写时，用 `equals()`
- 不区分大小写时，用 `equalsIgnoreCase()`

#### 13.7.5 `contains()`、`startsWith()`、`endsWith()`

```java
String fileName = "vpn-guide.txt";

System.out.println(fileName.contains("guide"));   // true
System.out.println(fileName.startsWith("vpn"));   // true
System.out.println(fileName.endsWith(".txt"));    // true
```

适合场景：

- 模糊判断是否包含某段内容
- 判断前缀
- 判断后缀，比如文件扩展名

#### 13.7.6 `indexOf()`

```java
String url = "https://knowflow.local/docs";
System.out.println(url.indexOf("://")); // 5
System.out.println(url.indexOf("docs")); // 25
System.out.println(url.indexOf("abc"));  // -1
```

记忆点：

- 找到返回下标
- 找不到返回 `-1`

#### 13.7.7 `replace()`

```java
String text = "parse_failed";
String result = text.replace("_", "-");
System.out.println(result); // parse-failed
```

注意：

- `replace()` 不会改原字符串
- 它会返回一个新字符串

#### 13.7.8 `trim()` 和 `isBlank()`

```java
String input = "  hello  ";
System.out.println(input.trim()); // hello

String empty = "   ";
System.out.println(empty.isBlank()); // true
```

区别：

- `trim()` 去掉首尾空白
- `isBlank()` 判断是否为空白字符串

#### 13.7.9 `split()`

```java
String tags = "java,redis,rabbitmq";
String[] arr = tags.split(",");

for (String tag : arr) {
    System.out.println(tag);
}
```

用途：

- 按分隔符拆分字符串

#### 13.7.10 项目里最常见的字符串判空写法

```java
if (content == null || content.isBlank()) {
    throw new IllegalArgumentException("内容不能为空");
}
```

你要特别注意顺序：

- 先判断 `null`
- 再调用实例方法

不能写成：

```java
// if (content.isBlank() || content == null) { }
```

因为如果 `content` 是 `null`，前半句就已经空指针了。

### 13.8 `StringBuilder` 的具体用法

`StringBuilder` 不是只会 `append()` 就够了，你至少要知道它是一块“可变字符串缓冲区”。

#### 13.8.1 为什么它适合频繁拼接

```java
StringBuilder builder = new StringBuilder();
builder.append("租户：");
builder.append("演示企业");
builder.append("，知识库：");
builder.append("入职资料");

System.out.println(builder.toString());
```

它的思路是：

- 在同一个可变对象上不断修改
- 最后一次性转成 `String`

#### 13.8.2 常用方法总览

##### `append()`

```java
StringBuilder builder = new StringBuilder();
builder.append("Hello");
builder.append(" ");
builder.append("Java");
System.out.println(builder); // Hello Java
```

##### `insert()`

```java
StringBuilder builder = new StringBuilder("Java");
builder.insert(0, "Hello ");
System.out.println(builder); // Hello Java
```

##### `delete()`

```java
StringBuilder builder = new StringBuilder("Hello Java");
builder.delete(5, 6);
System.out.println(builder); // HelloJava
```

##### `replace()`

```java
StringBuilder builder = new StringBuilder("parse fail");
builder.replace(6, 10, "success");
System.out.println(builder); // parse success
```

##### `reverse()`

```java
StringBuilder builder = new StringBuilder("abc");
builder.reverse();
System.out.println(builder); // cba
```

##### `length()`

```java
StringBuilder builder = new StringBuilder("knowflow");
System.out.println(builder.length()); // 8
```

##### `toString()`

```java
StringBuilder builder = new StringBuilder();
builder.append("ticket-");
builder.append(1001);

String ticketNo = builder.toString();
System.out.println(ticketNo); // ticket-1001
```

#### 13.8.3 一个业务风格更强的示例

```java
public class PromptBuilderDemo {
    public static void main(String[] args) {
        String tenantName = "演示租户";
        String question = "VPN 连不上怎么办？";

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业知识助手。");
        prompt.append("租户：").append(tenantName).append("。");
        prompt.append("用户问题：").append(question).append("。");
        prompt.append("请优先基于知识库回答。");

        System.out.println(prompt.toString());
    }
}
```

这比连续写很多 `+` 更适合复杂拼接场景。

### 13.9 `StringBuffer` 的简单用法

`StringBuffer` 和 `StringBuilder` 的 API 基本一致，只是它的方法大多带同步，线程安全但更重。

```java
StringBuffer buffer = new StringBuffer();
buffer.append("task:");
buffer.append(1001);
buffer.append("-retry");

System.out.println(buffer.toString()); // task:1001-retry
```

你当前阶段记住这句话就够了：

- 日常业务代码，优先 `String`
- 频繁拼接，优先 `StringBuilder`
- 除非明确有并发共享修改需求，否则一般不主动选 `StringBuffer`

### 13.10 这一章初学者最容易犯的错

1. 用 `==` 比较字符串内容
2. 忘记 `String` 不可变，以为 `replace()` 会改原对象
3. 先调用 `isBlank()` 再判断 `null`
4. 频繁拼接长字符串时还一直用 `+`

## 14. 集合框架

后端项目里集合用得非常多。

### 14.0 先解决一个问题：为什么集合这么重要

因为真实项目几乎不可能只处理“单个值”。

你在业务开发里经常面对的是：

- 一批用户
- 一页文档
- 多条工单
- 多个检索结果
- 一组统计数据

所以：

- 变量解决“一个值”的存储
- 集合解决“一组值”的存储

### 14.0.1 用一张表先把 `List / Set / Map` 区分开

| 类型 | 特点 | 典型问题 | 常见场景 |
|---|---|---|---|
| `List` | 有序、可重复 | “我要按顺序保存一批数据” | 文档列表、检索结果列表 |
| `Set` | 无重复 | “我要去重” | 标签集合、id 去重 |
| `Map` | 键值对 | “我要通过 key 快速找 value” | 通过 id 查对象、配置项映射 |

这张表建议你直接记住，因为项目和面试里都会反复用到。

### 14.0.2 先建立集合框架层次感

如果没有结构感，初学者很容易把一堆类背乱。

你先记住这个简化关系：

```text
Collection
├─ List
├─ Set
└─ Queue

Map
```

说明：

- `List`、`Set`、`Queue` 都属于 `Collection` 体系
- `Map` 不属于 `Collection`
- `Map` 是单独的一套键值对结构

### 14.1 `List`

特点：

- 有序
- 可重复

```java
import java.util.ArrayList;
import java.util.List;

public class ListDemo {
    public static void main(String[] args) {
        List<String> documents = new ArrayList<>();
        documents.add("vpn-guide.txt");
        documents.add("wifi-guide.txt");
        documents.add("vpn-guide.txt");

        System.out.println(documents);
    }
}
```

适合场景：

- 文档列表
- 工单列表
- 检索结果列表

### 14.1.1 `List` 常用操作

```java
import java.util.ArrayList;
import java.util.List;

public class ListOperationDemo {
    public static void main(String[] args) {
        List<String> docs = new ArrayList<>();

        docs.add("a.txt");
        docs.add("b.txt");
        docs.add("c.txt");
        docs.add(1, "insert.txt");

        System.out.println(docs.get(0));      // a.txt
        System.out.println(docs.set(2, "b2.txt")); // b.txt
        System.out.println(docs.contains("c.txt")); // true
        System.out.println(docs.size());      // 4

        docs.remove("c.txt");
        docs.remove(0);

        System.out.println(docs.isEmpty());   // false
        System.out.println(docs);             // [insert.txt, b2.txt]
    }
}
```

你要会认出这些核心操作：

- `add()` 追加元素
- `add(index, element)` 指定位置插入
- `get(index)` 取值
- `set(index, element)` 修改
- `remove(index)` 或 `remove(object)` 删除
- `contains()` 判断是否包含
- `size()` 获取元素个数
- `isEmpty()` 判断是否为空
- `clear()` 清空集合

### 14.1.2 `ArrayList` 和 `LinkedList`

#### `ArrayList`

特点：

- 底层是动态数组
- 查询效率高
- 是最常用的 `List` 实现

适用场景：

- 绝大多数普通业务列表
- 分页结果
- 查询结果集合

#### `LinkedList`

特点：

- 底层是链表结构
- 中间插入删除理论上更有优势
- 但日常企业开发里远没有 `ArrayList` 常用

你当前阶段先记住：

- `List` 默认优先想到 `ArrayList`
- 不是性能瓶颈，不要上来就纠结 `LinkedList`

#### `Vector`

它是历史类，线程安全但偏旧。

当前主流业务代码中：

- 很少主动新写 `Vector`

### 14.2 `Set`

特点：

- 不允许重复

```java
import java.util.HashSet;
import java.util.Set;

Set<String> tags = new HashSet<>();
tags.add("vpn");
tags.add("network");
tags.add("vpn");

System.out.println(tags);
```

这个例子最关键的点不是输出什么，而是你要理解：

- 第二次加入 `"vpn"` 不会报错
- 但集合里最终只保留一份

### 14.2.1 `Set` 常用操作

```java
import java.util.HashSet;
import java.util.Set;

Set<String> tags = new HashSet<>();
tags.add("java");
tags.add("redis");
tags.add("rabbitmq");

System.out.println(tags.contains("redis")); // true
System.out.println(tags.size());            // 3

tags.remove("java");
System.out.println(tags.isEmpty());         // false

tags.clear();
System.out.println(tags.isEmpty());         // true
```

### 14.2.2 `HashSet`、`LinkedHashSet`、`TreeSet`

#### `HashSet`

- 最常用
- 无序
- 去重效率高

#### `LinkedHashSet`

- 去重
- 保留插入顺序

示例：

```java
import java.util.LinkedHashSet;
import java.util.Set;

Set<String> tags = new LinkedHashSet<>();
tags.add("b");
tags.add("a");
tags.add("b");

System.out.println(tags); // [b, a]
```

#### `TreeSet`

- 自动排序
- 元素必须可比较

示例：

```java
import java.util.Set;
import java.util.TreeSet;

Set<Integer> scores = new TreeSet<>();
scores.add(90);
scores.add(70);
scores.add(90);
scores.add(80);

System.out.println(scores); // [70, 80, 90]
```

选择建议：

- 普通去重：`HashSet`
- 既要去重又想保留顺序：`LinkedHashSet`
- 需要自动排序：`TreeSet`

### 14.3 `Queue`

虽然你这次主要提到 `List / Set / Map`，但从知识体系完整性来说，集合框架里最好顺手认识一下 `Queue`。

它的核心特点是：

- 按队列模型处理数据
- 常见于任务调度、缓冲、消息处理

示例：

```java
import java.util.LinkedList;
import java.util.Queue;

Queue<String> queue = new LinkedList<>();
queue.offer("task-1");
queue.offer("task-2");

System.out.println(queue.poll()); // task-1
System.out.println(queue.peek()); // task-2
```

常用方法：

- `offer()` 入队
- `poll()` 取出并删除队头
- `peek()` 只看队头，不删除

### 14.4 `Map`

特点：

- 键值对结构

```java
import java.util.HashMap;
import java.util.Map;

Map<Long, String> userMap = new HashMap<>();
userMap.put(1L, "Tom");
userMap.put(2L, "Jerry");

System.out.println(userMap.get(1L)); // Tom
```

适合场景：

- 根据 id 快速查对象
- 配置项映射
- 统计聚合

### 14.4.1 `Map` 常用操作

```java
import java.util.HashMap;
import java.util.Map;

Map<String, Integer> counterMap = new HashMap<>();

counterMap.put("SUCCESS", 10);
counterMap.put("FAILED", 2);
counterMap.put("PENDING", 5);

System.out.println(counterMap.get("SUCCESS"));          // 10
System.out.println(counterMap.getOrDefault("UNKNOWN", 0)); // 0
System.out.println(counterMap.containsKey("FAILED"));   // true
System.out.println(counterMap.containsValue(5));        // true
System.out.println(counterMap.size());                  // 3

counterMap.remove("PENDING");
System.out.println(counterMap);
```

你至少要会这些：

- `put(key, value)`
- `get(key)`
- `getOrDefault(key, defaultValue)`
- `containsKey(key)`
- `containsValue(value)`
- `remove(key)`
- `size()`
- `isEmpty()`
- `clear()`

### 14.4.2 `HashMap`、`LinkedHashMap`、`TreeMap`

#### `HashMap`

- 最常用
- 允许一个 `null key`
- 不保证顺序

#### `LinkedHashMap`

- 保留插入顺序
- 在需要稳定遍历顺序时很实用

#### `TreeMap`

- 按 key 自动排序

示例：

```java
import java.util.Map;
import java.util.TreeMap;

Map<String, Integer> scoreMap = new TreeMap<>();
scoreMap.put("c", 70);
scoreMap.put("a", 90);
scoreMap.put("b", 80);

System.out.println(scoreMap); // {a=90, b=80, c=70}
```

### 14.5 遍历集合

这一节必须讲细一点，因为“会创建集合”和“会安全地遍历集合”不是一回事。

#### 14.5.1 遍历 `List`

##### 增强 `for`

```java
List<String> docs = List.of("a.txt", "b.txt", "c.txt");

for (String doc : docs) {
    System.out.println(doc);
}
```

优点：

- 写法简洁
- 最常用

##### 下标遍历

```java
List<String> docs = List.of("a.txt", "b.txt", "c.txt");

for (int i = 0; i < docs.size(); i++) {
    System.out.println(i + " -> " + docs.get(i));
}
```

适合场景：

- 既要元素又要下标

#### 14.5.2 遍历 `Set`

```java
Set<String> tags = Set.of("java", "redis", "rabbitmq");

for (String tag : tags) {
    System.out.println(tag);
}
```

注意：

- `Set` 没有下标概念

#### 14.5.3 遍历 `Map`

最推荐的方式是 `entrySet()`。

```java
Map<String, Integer> statMap = new HashMap<>();
statMap.put("SUCCESS", 10);
statMap.put("FAILED", 2);

for (Map.Entry<String, Integer> entry : statMap.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}
```

为什么推荐 `entrySet()`：

- 一次就能拿到 `key` 和 `value`
- 比只遍历 `keySet()` 再 `get()` 更直接

#### 14.5.4 `Iterator` 是什么

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

List<String> docs = new ArrayList<>();
docs.add("a.txt");
docs.add("b.txt");

Iterator<String> iterator = docs.iterator();
while (iterator.hasNext()) {
    String doc = iterator.next();
    System.out.println(doc);
}
```

你当前阶段至少要知道：

- `Iterator` 是集合的迭代器
- `hasNext()` 判断还有没有下一个元素
- `next()` 取下一个元素

### 14.6 数组和集合到底是什么关系

很多初学者会问：

- 前面已经学了数组，为什么还要集合

你可以这样理解：

- 数组是更基础的数据结构
- 集合是更适合业务开发的高级封装

#### 数组的优点

- 简单
- 访问快

#### 数组的缺点

- 长度固定
- 不方便动态增删
- 业务开发可读性和可维护性一般

#### 集合的优点

- 更灵活
- 功能更丰富
- 更符合企业开发场景

所以真实后端开发里：

- 数组会用
- 但集合更常见

### 14.7 选型口诀

如果你现在还容易混，就先记住这一版最实用口诀：

1. 一组有顺序、允许重复的数据，用 `List`
2. 一组需要去重的数据，用 `Set`
3. 需要通过 key 查 value，用 `Map`
4. 普通列表默认优先 `ArrayList`
5. 普通去重默认优先 `HashSet`
6. 普通键值存储默认优先 `HashMap`
7. 需要顺序时考虑 `LinkedHashSet` / `LinkedHashMap`
8. 需要排序时考虑 `TreeSet` / `TreeMap`

## 15. 泛型

### 15.1 泛型是什么

泛型就是“把类型也参数化”。

例如：

```java
List<String> names = new ArrayList<>();
```

表示这个列表里只能放 `String`。

### 15.2 为什么要有泛型

没有泛型会怎样？

```java
List list = new ArrayList();
list.add("Tom");
list.add(123);
```

这样就很危险，因为你根本不知道里面混进了什么类型。

### 15.3 泛型的好处

- 编译期类型检查
- 代码更清晰
- 少犯类型转换错误

### 15.4 为什么 Java 集合离不开泛型

没有泛型时，集合会变得很危险。

例如：

```java
List list = new ArrayList();
list.add("Tom");
list.add(123);
```

这样虽然能编译，但后面你读取时可能会出现类型转换错误。

用了泛型后：

```java
List<String> names = new ArrayList<>();
names.add("Tom");
// names.add(123); // 编译错误
```

问题会在编译期直接暴露，而不是拖到运行期。

## 16. 包装类与自动装箱

Java 基本类型有对应包装类：

| 基本类型 | 包装类 |
|---|---|
| `byte` | `Byte` |
| `short` | `Short` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |
| `char` | `Character` |
| `boolean` | `Boolean` |

示例：

```java
Integer count = 10; // 自动装箱
int value = count;  // 自动拆箱
```

为什么重要？

因为集合里不能直接放基本类型，只能放对象类型。

例如：

```java
List<Integer> scores = new ArrayList<>();
```

### 16.1 包装类为什么存在

很多初学者会问：

- 已经有 `int` 了，为什么还要 `Integer`

原因主要有三个：

1. 集合里只能放对象，不能直接放基本类型
2. 泛型要求使用引用类型
3. 包装类提供了很多有用的方法

例如：

```java
Integer value = Integer.valueOf("123");
System.out.println(value);
```

### 16.2 自动装箱和拆箱

```java
Integer a = 10; // 自动装箱
int b = a;      // 自动拆箱
```

编译器大致会帮你做类似转换：

```java
Integer a = Integer.valueOf(10);
int b = a.intValue();
```

### 16.3 `Integer` 缓存要知道什么

Java 中 `Integer` 对 `-128` 到 `127` 范围内的整数做了缓存。

示例：

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b); // true

Integer x = 200;
Integer y = 200;
System.out.println(x == y); // false
```

这说明：

- 比较包装类内容时，不要依赖 `==`
- 更稳妥的方式是用 `equals()`

### 16.4 包装类常用方法

包装类不是只拿来“装箱”的，它们本身也带很多常用能力。

#### 字符串转数字

```java
int pageNo = Integer.parseInt("12");
long tenantId = Long.parseLong("1001");
double score = Double.parseDouble("95.5");
```

注意：

- 字符串内容必须是合法数字
- 否则会抛 `NumberFormatException`

#### 把数字包装成对象

```java
Integer a = Integer.valueOf(10);
Long b = Long.valueOf(100L);
```

#### 比较大小

```java
Integer a = 10;
Integer b = 20;

System.out.println(a.compareTo(b)); // 小于 0
System.out.println(b.compareTo(a)); // 大于 0
System.out.println(a.compareTo(10)); // 0
```

#### 获取最大值最小值

```java
System.out.println(Integer.MAX_VALUE);
System.out.println(Integer.MIN_VALUE);
```

这和前面讲基本数据类型范围时就串起来了。

### 16.5 自动拆箱的空指针陷阱

这一点非常值得初学者提前建立警惕。

```java
Integer count = null;
// int value = count; // NullPointerException
```

为什么？

因为这里会发生自动拆箱，本质类似：

```java
int value = count.intValue();
```

但 `count` 是 `null`，所以就空指针了。

这在项目里尤其常见于：

- 数据库字段允许为空
- 接口参数是包装类
- 你以为一定有值，结果实际没有

### 16.6 基本类型和包装类怎么选

#### 优先用基本类型的场景

- 明确不会为 `null`
- 只做普通数值计算
- 对性能和内存更敏感

例如：

```java
int pageSize = 20;
long total = 100L;
boolean success = true;
```

#### 必须或适合用包装类的场景

- 要放进集合
- 需要表达“值可能不存在”
- 框架要求对象类型

例如：

```java
List<Integer> ids = new ArrayList<>();
Integer score = null;
```

你可以先记住一句话：

- 能确定一定有值时，优先基本类型
- 需要 `null` 语义或集合泛型时，用包装类

## 17. 枚举

项目里很多状态值都适合用枚举表示。

```java
public enum TaskStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}
```

好处：

- 限定可选值范围
- 代码更清晰
- 少写魔法字符串

## 17.1 Object 类与对象比较

Java 中所有类，默认都继承自 `Object`。

也就是说，任何对象天然都具备一些基础方法：

- `toString()`
- `equals()`
- `hashCode()`

### 17.1.1 `toString()`

它的作用是把对象转成字符串，方便打印日志和调试。

示例：

```java
class User {
    Long id;
    String name;

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "'}";
    }
}
```

### 17.1.2 `equals()`

默认情况下，`equals()` 比较的是对象地址。

但在真实业务中，我们更常希望按“内容是否相同”来比较对象。

例如两个用户对象只要 `id` 相同，我们就认为它们代表同一个用户。

```java
import java.util.Objects;

class User {
    Long id;
    String name;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        User other = (User) obj;
        return Objects.equals(id, other.id);
    }
}
```

### 17.1.3 `hashCode()`

如果你重写了 `equals()`，通常也要一起重写 `hashCode()`。

原因是：

- 如果两个对象 `equals()` 相等
- 那么它们的 `hashCode()` 必须相等

否则在这些集合中会出问题：

- `HashMap`
- `HashSet`

示例：

```java
import java.util.Objects;

class User {
    Long id;
    String name;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        User other = (User) obj;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

### 17.1.4 这和项目有什么关系

虽然 `KnowFlow` 当前更多是数据库实体驱动，但你以后只要涉及：

- 用对象做去重
- 把对象放进 `HashSet`
- 用对象做 `Map` 的 key

`equals()` 和 `hashCode()` 就一定要会。

## 18. 异常处理

### 18.1 为什么要有异常

程序运行中可能会出错，比如：

- 参数为空
- 文件不存在
- 数据库连接失败
- 数组下标越界

异常就是 Java 对错误情况的一种表示方式。

### 18.2 最基本的 `try-catch`

```java
public class ExceptionDemo {
    public static void main(String[] args) {
        try {
            int result = 10 / 0;
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("程序出错了：" + e.getMessage());
        }
    }
}
```

### 18.3 `finally`

`finally` 一般用于收尾操作。

```java
try {
    System.out.println("执行业务");
} catch (Exception e) {
    System.out.println("处理异常");
} finally {
    System.out.println("最终一定会执行");
}
```

### 18.4 `throw`

你也可以主动抛异常。

```java
public static void validateName(String name) {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("用户名不能为空");
    }
}
```

### 18.5 运行时异常和受检异常

你先建立一个基础认识就够了：

- 运行时异常：代码问题或非法状态，常见如 `NullPointerException`
- 受检异常：编译器要求你处理，常见如 `IOException`

### 18.5.1 Java 异常体系怎么理解

可以先抓住这条主线：

```text
Throwable
 ├─ Error
 └─ Exception
     ├─ RuntimeException
     └─ 其他受检异常
```

#### `Error`

通常表示系统级严重问题，例如：

- 内存溢出

这类问题一般不是普通业务代码主动处理的重点。

#### `Exception`

这是我们日常开发最常接触的异常。

其中又分：

- `RuntimeException`
- 受检异常

#### `RuntimeException`

常见例子：

- `NullPointerException`
- `IllegalArgumentException`
- `IndexOutOfBoundsException`

这类异常通常说明：

- 代码逻辑有问题
- 入参不合法
- 程序处于非法状态

#### 受检异常

常见例子：

- `IOException`

它通常表示：

- 你做的外部操作可能失败
- 编译器要求你显式处理

### 18.5.2 什么时候用异常，什么时候用 `if-else`

一个很重要的经验是：

- 正常业务分支，用 `if-else`
- 非正常错误状态，用异常

例如：

```java
if (score >= 60) {
    System.out.println("及格");
} else {
    System.out.println("不及格");
}
```

这里不是异常，因为“不及格”是正常业务结果。

但下面适合用异常：

```java
if (username == null || username.isBlank()) {
    throw new IllegalArgumentException("用户名不能为空");
}
```

因为这是非法输入，而不是正常分支。

### 18.6 项目里的异常思想

在 `KnowFlow` 项目里你会看到：

- 业务异常 `BizException`
- 错误码 `ErrorCode`

这是一种更工程化的做法：

- 不只是“报错”
- 还要给前端明确的业务含义

## 19. IO 与 UTF-8 编码

这一部分非常重要，因为你已经在项目中真实遇到过乱码问题。

### 19.1 什么是 IO

IO = Input / Output

简单说就是：

- 读文件
- 写文件
- 读网络数据
- 写网络数据

### 19.2 读取文本文件

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileReadDemo {
    public static void main(String[] args) throws IOException {
        String content = Files.readString(Path.of("demo.txt"), StandardCharsets.UTF_8);
        System.out.println(content);
    }
}
```

### 19.3 写入文本文件

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileWriteDemo {
    public static void main(String[] args) throws IOException {
        Files.writeString(
                Path.of("demo.txt"),
                "你好，KnowFlow",
                StandardCharsets.UTF_8
        );
    }
}
```

### 19.4 为什么 UTF-8 很重要

如果编码不统一，就可能出现乱码。

比如：

- 文件是 UTF-8
- 程序按 GBK 去读

那中文通常就会乱掉。

### 19.5 什么是 BOM

BOM 可以理解成某些 UTF-8 文件开头的一段特殊标记。

有时它会污染文本内容，表现为：

- 字符串开头多出奇怪字符
- 第一段内容匹配不上

这就是为什么你项目里要处理 BOM。

## 20. Lambda 与 Stream 入门

这是现代 Java 很常见的写法。

### 20.1 Lambda

```java
List<String> names = List.of("Tom", "Jerry", "Alice");
names.forEach(name -> System.out.println(name));
```

### 20.2 Stream 过滤

```java
List<String> docs = List.of("vpn-guide.txt", "wifi-guide.txt", "vpn-faq.txt");

List<String> vpnDocs = docs.stream()
        .filter(name -> name.contains("vpn"))
        .toList();

System.out.println(vpnDocs);
```

### 20.3 Stream 映射

```java
List<String> users = List.of("tom", "jerry");

List<String> upperUsers = users.stream()
        .map(String::toUpperCase)
        .toList();
```

### 20.4 为什么项目里会大量出现

因为它非常适合：

- 列表转换
- 过滤数据
- 拼装返回结果

### 20.5 初学者理解 Stream 的一个心法

很多人第一次看到 Stream 会觉得“这是什么链式魔法”。

其实你只要抓住一句话：

- Stream 本质上是在“对一组数据做流水线处理”

例如：

```java
List<String> docs = List.of("vpn-guide.txt", "wifi-guide.txt", "vpn-faq.txt");

List<String> result = docs.stream()
        .filter(name -> name.contains("vpn"))
        .map(String::toUpperCase)
        .toList();
```

可以翻译成人话：

1. 先拿到文档列表
2. 过滤出名称包含 `vpn` 的文档
3. 再把这些名称转成大写
4. 最后收集成新的列表

所以如果你一开始看不懂 Stream，不要急着背语法，先把它拆成自然语言流程。

## 21. 一个更接近项目的小案例

下面我写一个“小型知识文档对象”的例子，把前面学的东西串起来。

```java
import java.time.LocalDateTime;

public class KnowledgeDocument {

    private Long id;
    private String docName;
    private String status;
    private LocalDateTime createdAt;

    public KnowledgeDocument(Long id, String docName, String status) {
        this.id = id;
        this.docName = docName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDocName() {
        return docName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void printSummary() {
        System.out.println("文档ID=" + id
                + ", 名称=" + docName
                + ", 状态=" + status
                + ", 创建时间=" + createdAt);
    }

    public static void main(String[] args) {
        KnowledgeDocument document = new KnowledgeDocument(1L, "vpn-guide.txt", "PENDING");
        document.printSummary();

        document.setStatus("SUCCESS");
        document.printSummary();
    }
}
```

这个例子里已经用到了：

- 类和对象
- 构造方法
- 封装
- `LocalDateTime`
- 异常校验
- 方法封装

这就是你未来写业务代码的缩小版。

## 22. 这些 Java 基础在 KnowFlow 项目里分别对应什么

### 22.1 类与对象

对应：

- `Entity`
- `DTO`
- `VO`
- `ServiceImpl`

### 22.2 集合

对应：

- 检索结果列表
- 文档列表
- 工单列表
- 看板统计结果

### 22.3 异常

对应：

- 业务异常
- 权限校验失败
- 文档解析失败

### 22.4 IO 与编码

对应：

- 文档读取
- 文本规范化
- BOM 处理
- smoke test 中文内容校验

### 22.5 字符串与 `StringBuilder`

对应：

- 问答上下文拼接
- 日志文本
- 返回消息说明

## 23. 初学者最容易犯的错误

### 23.1 用 `==` 比较字符串

错误示例：

```java
if (status == "SUCCESS") {
    // 不推荐
}
```

推荐：

```java
if ("SUCCESS".equals(status)) {
    // 推荐
}
```

### 23.2 忘记判空

```java
String name = null;
System.out.println(name.length()); // 会报空指针
```

### 23.3 局部变量未初始化

```java
int count;
System.out.println(count); // 编译错误
```

### 23.4 集合类型写太随意

不推荐：

```java
List list = new ArrayList();
```

推荐：

```java
List<String> list = new ArrayList<>();
```

### 23.5 读写文件时忽略编码

这会直接导致乱码。

### 23.6 分不清重载和重写

你可以用一句口诀记住：

- 同类同名不同参：重载
- 子类改父类实现：重写

### 23.7 误以为 Java 有引用传递

一定要把这个概念说准：

- Java 只有值传递

对象参数之所以能改到内容，是因为传的是“引用值副本”，不是语言层面的引用传递。

### 23.8 以为 `double` 适合所有小数场景

如果你后面做的是：

- 金额
- 价格
- 精确统计

那就应该优先考虑 `BigDecimal`，而不是直接用 `double`。

## 24. 这一课怎么练

建议你按这个顺序做练习。

### 练习 1：变量与分支

写一个程序：

- 定义一个 `score`
- 根据分数输出“优秀 / 良好 / 及格 / 不及格”

### 练习 2：方法

写一个 `isPass(int score)` 方法，返回是否及格。

### 练习 3：类与对象

写一个 `User` 类，包含：

- `id`
- `username`
- `role`

并写一个打印信息的方法。

### 练习 4：集合

创建一个 `List<User>`，放 3 个用户进去，然后遍历打印。

### 练习 5：Map

创建一个 `Map<Long, User>`，通过 `id` 查询用户。

### 练习 6：异常

写一个方法校验用户名不能为空，空时抛异常。

### 练习 7：文件读写

把一段中文内容写到 `demo.md`，再读出来打印。

## 25. 课后自测题

请你试着不用看答案，自己回答下面的问题。

1. `main` 方法为什么是 Java 程序入口？
2. `int` 和 `long` 的区别是什么？
3. `==` 和 `equals` 的区别是什么？
4. `List`、`Set`、`Map` 的使用场景分别是什么？
5. 为什么要有构造方法？
6. `private` 和 `public` 的区别是什么？
7. 为什么项目里要统一用 UTF-8？
8. 什么是异常？为什么不能全都用 `if-else` 代替？
9. `StringBuilder` 为什么适合拼接字符串？
10. 为什么 Java 项目里大量使用接口？
11. `JDK`、`JRE`、`JVM` 的关系是什么？
12. Java 为什么说是跨平台的？
13. Java 为什么既像编译型语言，又像解释型语言？
14. `double` 为什么不适合金额计算？
15. 什么是自动装箱和拆箱？
16. `equals()` 和 `hashCode()` 为什么通常要一起重写？
17. `String` 为什么设计成不可变？
18. 重载和重写的区别是什么？
19. 抽象类和接口分别更适合什么场景？
20. Java 里到底有没有引用传递？

## 26. 学完这一课后的判断标准

如果你现在能做到下面这些，就说明这一课已经基本过关：

1. 能自己写一个 Java 类并运行
2. 能写变量、分支、循环、方法
3. 能理解类、对象、构造方法、封装
4. 能使用 `List` 和 `Map`
5. 知道如何处理基础异常
6. 知道为什么要注意 UTF-8 编码
7. 再回头看 `KnowFlow` 项目代码时，不会完全陌生

## 27. 给你的学习建议

如果你是第一次学 Java，不要追求一次全懂。

更好的方法是：

1. 先看完这份讲义
2. 每个小节自己敲一遍代码
3. 自己完成练习
4. 再回到项目里找对应写法
5. 遇到不懂的，再回来看这份讲义

这样你会比“只看视频不动手”进步快很多。

## 28. 课堂总结与项目代码阅读方法

到这里，这一课的真正重点不是“你看完了很多页”，而是你脑子里有没有形成一张结构图。

你现在应该逐步形成下面这条主线：

```text
Java 程序运行
    ↓
变量与类型
    ↓
分支、循环、方法
    ↓
类与对象
    ↓
字符串、集合、泛型
    ↓
异常、IO、编码
    ↓
能看懂项目基础代码
```

### 28.1 第一次看项目代码时，应该怎么看

不要一上来就看最复杂的业务流程。

建议你这样看：

#### 第一步：先找“类”

看看有哪些类：

- `User`
- `KnowledgeBase`
- `Document`
- `ParseTask`

先问自己：

- 这个类在现实里代表什么

#### 第二步：再找“字段”

问自己：

- 这个类保存了哪些数据
- 哪些字段是状态
- 哪些字段是时间
- 哪些字段是主键或关联关系

#### 第三步：再找“方法”

问自己：

- 这个方法做了什么
- 它接收什么参数
- 返回什么结果
- 中间做了哪些判断

#### 第四步：再看“集合和流程”

问自己：

- 这里为什么是 `List`
- 为什么这里用 `Map`
- 这里为什么要遍历
- 这里为什么会抛异常

### 28.2 看不懂代码时，不要慌，按这四个问题拆

当你看到一段陌生 Java 代码时，就按下面四个问题拆：

1. 这里有哪些变量
2. 这里有哪些对象
3. 这里做了什么判断
4. 最终返回了什么

这会比你盯着整段代码发呆有效得多。

### 28.3 这一课真正的目标

这门课的真正目标不是让你立刻变成高手，而是让你完成这一步：

- 从“看到 Java 很慌”
- 变成“虽然还不熟，但已经能拆开看、能慢慢读懂”

这一步一旦跨过去，后面 Spring Boot、数据库、鉴权、中间件你才学得进去。

## 29. 下一课预告

当你把这一课吃下来后，下一课最自然的衔接就是：

- Spring Boot 基础

因为你已经有了 Java 的变量、对象、方法、集合、异常这些底座，接下来才能真正理解：

- Controller 是什么
- Service 是什么
- Bean 是什么
- 为什么一个注解就能把对象管理起来

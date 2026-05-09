# 第 1 课配套：Java 基础常见报错与排查手册

## 0. 这份手册是干什么的

很多初学者不是“不会学”，而是容易卡在这些地方：

- 明明照着讲义敲了，为什么运行不了？
- 明明代码看着差不多，为什么编译器报错？
- 为什么中文输出变成乱码？

这份手册就是专门帮你解决：

- 第一次写 Java 时最常见的报错
- IDEA / JDK / 编码 / 语法层面的典型问题

建议你把这份手册和讲义、作业配套着用。

## 1. 先学会区分两类错误

### 1.1 编译错误

特点：

- 程序还没运行起来
- 编译器直接报错

例如：

- 少分号
- 括号没配对
- 类型不匹配
- 找不到方法

### 1.2 运行错误

特点：

- 程序已经开始运行
- 运行到某一步才报错

例如：

- `NullPointerException`
- 数组越界
- 文件不存在
- 编码异常

先学会区分这两类错误，你排查就会快很多。

## 2. 第一次写 Java 最常见的报错

### 2.1 类名和文件名不一致

错误现象：

- 你写了 `public class HelloJava`
- 但文件名不是 `HelloJava.java`

Java 规则是：

- 如果一个类是 `public`
- 那么文件名必须和类名完全一致

正确示例：

```java
public class HelloJava {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

文件名必须是：

```text
HelloJava.java
```

### 2.2 少了分号

错误示例：

```java
int age = 18
System.out.println(age);
```

问题：

- `18` 后面少了 `;`

正确写法：

```java
int age = 18;
System.out.println(age);
```

### 2.3 括号不配对

错误示例：

```java
if (score >= 60 {
    System.out.println("及格");
}
```

问题：

- 缺了一个右括号 `)`

这种错误在初学阶段非常高频。

建议：

- 每写完一对括号，就顺手补另一半

### 2.4 字符串少引号

错误示例：

```java
String name = Tom;
```

问题：

- 字符串字面量必须用双引号包起来

正确写法：

```java
String name = "Tom";
```

### 2.5 字符和字符串混淆

错误示例：

```java
char c = "A";
```

问题：

- `char` 是单个字符
- 要用单引号

正确写法：

```java
char c = 'A';
```

### 2.6 `main` 方法签名写错

标准入口方法是：

```java
public static void main(String[] args)
```

常见错误：

- 少了 `static`
- 参数写错
- 大小写写错

如果 `main` 写错，程序通常就无法正常作为入口启动。

## 3. 变量和类型相关错误

### 3.1 局部变量未初始化

错误示例：

```java
int count;
System.out.println(count);
```

问题：

- 局部变量必须先赋值再使用

正确写法：

```java
int count = 0;
System.out.println(count);
```

### 3.2 类型不匹配

错误示例：

```java
int age = "18";
```

问题：

- `int` 不能直接接收字符串

### 3.3 强转导致精度丢失

```java
double score = 95.8;
int intScore = (int) score;
System.out.println(intScore); // 95
```

这不是报错，但这是一个很常见的“结果和预期不一致”问题。

你要记住：

- 强制类型转换可能丢精度

### 3.4 `double` 精度问题

```java
System.out.println(0.1 + 0.2);
```

如果结果不是你想象中的 `0.3`，不要慌。

这不是 Java 坏了，而是浮点数本身的表示方式导致的。

遇到金额、价格、精确统计：

- 优先考虑 `BigDecimal`

## 4. 面向对象相关错误

### 4.1 忘记 `new`

错误示例：

```java
User user;
user.name = "Tom";
```

问题：

- 变量只是声明了
- 但对象还没创建

正确写法：

```java
User user = new User();
user.name = "Tom";
```

### 4.2 空指针 `NullPointerException`

错误示例：

```java
User user = null;
System.out.println(user.getName());
```

问题：

- `user` 没有指向任何对象

排查思路：

1. 这个变量是不是 `null`
2. 这个对象是不是根本没 `new`
3. 这个方法返回的对象是不是为空

### 4.3 `this` 用错

错误示例：

```java
public class User {
    String name;

    public User(String name) {
        name = name;
    }
}
```

问题：

- 这里把参数自己赋给自己了

正确写法：

```java
public class User {
    String name;

    public User(String name) {
        this.name = name;
    }
}
```

### 4.4 重写方法时签名不一致

错误示例：

```java
class Animal {
    public void speak() {}
}

class Dog extends Animal {
    public void speak(String msg) {}
}
```

这不是重写，而是新定义了一个重载方法。

如果你想重写，方法签名要一致。

## 5. 集合相关错误

### 5.1 用 `==` 比较字符串

错误示例：

```java
if (status == "SUCCESS") {
    System.out.println("成功");
}
```

更推荐：

```java
if ("SUCCESS".equals(status)) {
    System.out.println("成功");
}
```

### 5.2 集合没指定泛型

不推荐：

```java
List list = new ArrayList();
list.add("Tom");
list.add(123);
```

问题：

- 类型不安全
- 运行期更容易出错

推荐：

```java
List<String> list = new ArrayList<>();
```

### 5.3 遍历时下标越界

错误示例：

```java
int[] nums = {1, 2, 3};
System.out.println(nums[3]);
```

问题：

- 数组下标从 `0` 开始
- `nums[3]` 已经越界

### 5.4 把 `null` 放进集合后又直接调用方法

```java
List<String> names = new ArrayList<>();
names.add(null);

for (String name : names) {
    System.out.println(name.length());
}
```

这会导致空指针。

## 6. 异常处理相关错误

### 6.1 `try-catch` 以为能解决一切

有些同学一遇到报错就想：

- 外面套个 `try-catch` 不就好了

这不是好习惯。

你要先判断：

- 报错是逻辑问题，还是外部异常
- 是应该修代码，还是应该捕获异常

### 6.2 抛了异常但自己不知道为什么

例如：

```java
throw new IllegalArgumentException("用户名不能为空");
```

这类代码不是“程序坏了”，而是：

- 代码在主动保护业务约束

你要学会从异常信息里理解程序设计意图。

## 7. IO 和编码相关错误

### 7.1 文件不存在

错误示例：

```java
Files.readString(Path.of("not-exist.txt"), StandardCharsets.UTF_8);
```

可能会报：

- 文件不存在

排查：

1. 文件名是否写对
2. 路径是否正确
3. 运行目录是不是你以为的那个目录

### 7.2 中文乱码

这是你已经真实遇到过的问题。

常见原因：

1. 文件编码和读取编码不一致
2. 控制台编码不一致
3. 文本里混入 BOM

标准建议：

- 读写文件时显式使用 `UTF-8`
- 控制台环境尽量统一 UTF-8

### 7.3 读写文件忘了指定编码

更推荐：

```java
Files.writeString(path, text, StandardCharsets.UTF_8);
```

而不是依赖默认编码。

## 8. IDEA 中常见问题

### 8.1 运行按钮是灰的

常见原因：

1. 当前类没有 `main` 方法
2. 文件还没保存或有语法错误
3. 项目没有正确识别 JDK

### 8.2 提示找不到 JDK

排查方向：

1. 本机是否安装了 JDK
2. IDEA 项目 SDK 是否配置正确
3. 模块 SDK 是否正确

### 8.3 中文显示异常

排查方向：

1. 文件编码是否 UTF-8
2. IDEA 全局编码是否 UTF-8
3. 终端编码是否 UTF-8

## 9. 报错时的标准排查顺序

以后你看到错误，建议按这个顺序排查：

1. 是编译错误还是运行错误
2. 错误信息里提到了哪一行
3. 那一行的变量是不是 `null`
4. 类型是不是写错了
5. 路径、文件名、编码是不是有问题

## 10. 送给初学者的一句话

写程序时，报错不是失败，而是程序在告诉你：

- 你和计算机的约定，哪一处没有对齐

你越早习惯阅读报错、理解报错、定位报错，后面学 Spring Boot 和项目开发就会越轻松。

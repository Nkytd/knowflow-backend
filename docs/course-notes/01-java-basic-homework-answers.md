# 第 1 课作业参考答案：Java 基础

## 0. 先提醒你怎么用答案

这份答案不建议你直接顺着抄。

更好的方式是：

1. 先自己做
2. 卡住时先看题目要求再想 5 分钟
3. 最后再看答案
4. 看完答案后，把答案关掉自己重写一遍

下面的答案以“能帮助你学会”为目标，所以有些地方会比最短写法更啰嗦一点。

## 1. A 级答案

### A1. 变量与输出

```java
public class A1Demo {
    public static void main(String[] args) {
        String name = "Tom";
        int age = 23;
        double score = 95.5;

        System.out.println("我叫 " + name + "，今年 " + age + " 岁，本次成绩 " + score);
    }
}
```

### A2. 分支判断

```java
public class A2Demo {
    public static void main(String[] args) {
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
    }
}
```

### A3. 循环求和

```java
public class A3Demo {
    public static void main(String[] args) {
        int sum = 0;

        for (int i = 1; i <= 100; i++) {
            sum += i;
        }

        System.out.println("结果是：" + sum);
    }
}
```

### A4. 偶数过滤

```java
public class A4Demo {
    public static void main(String[] args) {
        for (int i = 1; i <= 20; i++) {
            if (i % 2 == 0) {
                System.out.println(i);
            }
        }
    }
}
```

### A5. 方法封装

```java
public class A5Demo {
    public static boolean isEven(int num) {
        return num % 2 == 0;
    }

    public static void main(String[] args) {
        System.out.println(isEven(10));
        System.out.println(isEven(7));
    }
}
```

## 2. B 级答案

### B1. 编写 `User` 类

```java
public class User {
    Long id;
    String username;
    String role;

    public void printInfo() {
        System.out.println("id=" + id + ", username=" + username + ", role=" + role);
    }
}
```

### B2. 增加构造方法

```java
public class User {
    Long id;
    String username;
    String role;

    public User(Long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public void printInfo() {
        System.out.println("id=" + id + ", username=" + username + ", role=" + role);
    }

    public static void main(String[] args) {
        User user1 = new User(1L, "tom", "ADMIN");
        User user2 = new User(2L, "jerry", "USER");

        user1.printInfo();
        user2.printInfo();
    }
}
```

### B3. 封装字段

```java
public class User {
    private Long id;
    private String username;
    private String role;

    public User(Long id, String username, String role) {
        this.id = id;
        setUsername(username);
        this.role = role;
    }

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

### B4. 继承练习

```java
class Person {
    protected String name;

    public Person(String name) {
        this.name = name;
    }
}

class Employee extends Person {
    private String jobTitle;

    public Employee(String name, String jobTitle) {
        super(name);
        this.jobTitle = jobTitle;
    }

    public void printInfo() {
        System.out.println("name=" + name + ", jobTitle=" + jobTitle);
    }
}
```

### B5. 接口练习

```java
interface MessageSender {
    void send(String content);
}

class EmailSender implements MessageSender {
    @Override
    public void send(String content) {
        System.out.println("发送邮件：" + content);
    }
}

class SmsSender implements MessageSender {
    @Override
    public void send(String content) {
        System.out.println("发送短信：" + content);
    }
}

public class B5Demo {
    public static void main(String[] args) {
        MessageSender emailSender = new EmailSender();
        MessageSender smsSender = new SmsSender();

        emailSender.send("文档解析完成");
        smsSender.send("工单已创建");
    }
}
```

## 3. C 级答案

### C1. `List` 基础

```java
import java.util.ArrayList;
import java.util.List;

public class C1Demo {
    public static void main(String[] args) {
        List<String> docs = new ArrayList<>();
        docs.add("vpn-guide.txt");
        docs.add("wifi-guide.txt");
        docs.add("faq.md");
        docs.add("process.doc");
        docs.add("notice.txt");

        for (String doc : docs) {
            System.out.println(doc);
        }
    }
}
```

### C2. `Set` 去重

```java
import java.util.HashSet;
import java.util.Set;

public class C2Demo {
    public static void main(String[] args) {
        Set<String> tags = new HashSet<>();
        tags.add("vpn");
        tags.add("network");
        tags.add("vpn");
        tags.add("faq");

        System.out.println(tags);
    }
}
```

### C3. `Map` 查询

```java
import java.util.HashMap;
import java.util.Map;

public class C3Demo {
    public static void main(String[] args) {
        Map<Long, String> userMap = new HashMap<>();
        userMap.put(1L, "tom");
        userMap.put(2L, "jerry");
        userMap.put(3L, "alice");

        System.out.println(userMap.get(2L));
    }
}
```

### C4. 自定义异常使用

```java
public class C4Demo {
    public static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
    }

    public static void main(String[] args) {
        try {
            validateUsername("");
        } catch (IllegalArgumentException e) {
            System.out.println("捕获异常：" + e.getMessage());
        }
    }
}
```

### C5. 文件写入

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class C5Demo {
    public static void main(String[] args) throws IOException {
        Files.writeString(
                Path.of("demo.md"),
                "这是我的第一份 Java 文件写入练习。",
                StandardCharsets.UTF_8
        );
    }
}
```

### C6. 文件读取

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class C6Demo {
    public static void main(String[] args) throws IOException {
        String content = Files.readString(Path.of("demo.md"), StandardCharsets.UTF_8);
        System.out.println(content);
    }
}
```

### C7. `StringBuilder` 拼接

```java
public class C7Demo {
    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档 ");
        builder.append("vpn-guide.txt");
        builder.append(" 解析成功，耗时 ");
        builder.append(120);
        builder.append(" ms");

        System.out.println(builder);
    }
}
```

## 4. D 级答案

### D1. 定义 `KnowledgeDocument` 类

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

    public void printSummary() {
        System.out.println("id=" + id + ", docName=" + docName + ", status=" + status + ", createdAt=" + createdAt);
    }
}
```

### D2. 使用枚举表示状态

```java
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}
```

```java
import java.time.LocalDateTime;

public class KnowledgeDocument {
    private Long id;
    private String docName;
    private DocumentStatus status;
    private LocalDateTime createdAt;

    public KnowledgeDocument(Long id, String docName, DocumentStatus status) {
        this.id = id;
        this.docName = docName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void printSummary() {
        System.out.println("id=" + id + ", docName=" + docName + ", status=" + status + ", createdAt=" + createdAt);
    }
}
```

### D3. 模拟文档列表

```java
import java.util.ArrayList;
import java.util.List;

public class D3Demo {
    public static void main(String[] args) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        documents.add(new KnowledgeDocument(1L, "vpn-guide.txt", DocumentStatus.SUCCESS));
        documents.add(new KnowledgeDocument(2L, "wifi-guide.txt", DocumentStatus.PENDING));
        documents.add(new KnowledgeDocument(3L, "faq.md", DocumentStatus.FAILED));

        for (KnowledgeDocument document : documents) {
            document.printSummary();
        }
    }
}
```

### D4. 过滤成功文档

```java
import java.util.ArrayList;
import java.util.List;

enum DocumentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

class KnowledgeDocument {
    private Long id;
    private String docName;
    private DocumentStatus status;

    public KnowledgeDocument(Long id, String docName, DocumentStatus status) {
        this.id = id;
        this.docName = docName;
        this.status = status;
    }

    public String getDocName() {
        return docName;
    }

    public DocumentStatus getStatus() {
        return status;
    }
}

public class D4Demo {
    public static void main(String[] args) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        documents.add(new KnowledgeDocument(1L, "vpn-guide.txt", DocumentStatus.SUCCESS));
        documents.add(new KnowledgeDocument(2L, "wifi-guide.txt", DocumentStatus.PENDING));
        documents.add(new KnowledgeDocument(3L, "faq.md", DocumentStatus.SUCCESS));

        List<KnowledgeDocument> successDocs = documents.stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.SUCCESS)
                .toList();

        for (KnowledgeDocument doc : successDocs) {
            System.out.println(doc.getDocName());
        }
    }
}
```

这版答案我已经写成了可直接运行版。

也就是说，你把整段代码复制到一个文件里，调整一下类拆分方式，就能直接运行，不需要自己再猜“是不是还漏了 getter”。

### D5. 文档状态校验

```java
enum DocumentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

class KnowledgeDocument {
    private DocumentStatus status;

    public KnowledgeDocument(DocumentStatus status) {
        this.status = status;
    }

    public DocumentStatus getStatus() {
        return status;
    }

public void markSuccess() {
    if (status == DocumentStatus.SUCCESS) {
        return;
    }
    if (status == DocumentStatus.FAILED) {
        status = DocumentStatus.SUCCESS;
    }
}
}
```

上面这版答案严格按照题目要求来写：

- 如果已经是 `SUCCESS`，直接返回
- 只有 `FAILED` 时，才允许改成 `SUCCESS`

如果你后面想做“扩展版设计”，当然也可以允许：

- `PENDING -> SUCCESS`
- `PROCESSING -> SUCCESS`

但那属于你对需求的二次扩展，不属于这道题的标准答案。

如果对象为空引用，例如：

```java
KnowledgeDocument doc = null;
doc.markSuccess();
```

会抛出：

- `NullPointerException`

因为你是在对一个 `null` 引用调用方法。

### D6. 模拟问答上下文拼接

```java
import java.util.List;

public class D6Demo {
    public static String buildContext(List<String> snippets) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < snippets.size(); i++) {
            builder.append(i + 1)
                    .append(". ")
                    .append(snippets.get(i))
                    .append("\n");
        }

        return builder.toString().trim();
    }

    public static void main(String[] args) {
        List<String> snippets = List.of("VPN 登录故障排查", "证书导入说明", "DNS 检查步骤");
        System.out.println(buildContext(snippets));
    }
}
```

### D7. 模拟参数校验

```java
public class D7Demo {
    public static KnowledgeDocument createDocument(String docName) {
        if (docName == null || docName.isBlank()) {
            throw new IllegalArgumentException("docName 不能为空");
        }
        return new KnowledgeDocument(1L, docName, DocumentStatus.PENDING);
    }
}
```

### D8. 思考题参考回答

#### 1. 为什么文档状态适合用枚举？

因为状态是有限集合，不应该允许随便传字符串。枚举更安全、可读性更好，也能减少拼写错误。

#### 2. 为什么问答上下文拼接适合用 `StringBuilder`？

因为需要多次追加字符串，如果直接用 `String` 频繁拼接，会创建很多临时对象，效率较低。

#### 3. 为什么文件读写必须明确指定 UTF-8？

因为不同操作系统、不同编辑器默认编码可能不一样。如果不显式指定，中文内容容易乱码。

#### 4. 为什么项目里大量数据不是用数组，而是用 `List`？

因为 `List` 长度可变、操作更灵活，更适合真实业务开发。

#### 5. 为什么对象比较内容时一般不用 `==`？

因为 `==` 对引用类型比较的是地址，不一定代表内容相同。比较内容通常要用 `equals()`。

## 5. 一些答案之外的提醒

### 5.1 如果你写得比答案短，不一定是坏事

答案是“教学版”，强调清晰。

如果你已经能写出更简洁又正确的版本，说明你在进步。

### 5.2 如果你和答案差很多，也不用慌

这很正常。

你现在最重要的不是一次全对，而是：

- 知道自己卡在哪
- 能通过答案补上理解

### 5.3 最值得反复重做的题

如果时间有限，建议至少反复重做这几题：

1. B3 封装字段
2. C4 异常处理
3. C7 `StringBuilder`
4. D2 状态枚举
5. D6 上下文拼接

因为它们和 `KnowFlow` 项目关联最强。

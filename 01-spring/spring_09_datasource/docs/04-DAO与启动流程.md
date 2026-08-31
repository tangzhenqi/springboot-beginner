# DAO 与启动流程

[← 上一篇](03-Spring-XML配置详解.md) · [下一篇 →](05-数据库连接与验证.md)

> 分析 `BookDao`、`BookDaoImpl`、`App.java` 和空置的 `AppSystemProperties.java`。

## 7. DAO 代码分析

### 7.1 `BookDao` 接口

```java
public interface BookDao {
    public void save();
}
```

接口只描述“可以保存”，不关心如何保存。这符合面向接口编程的思想。

接口方法中的 `public` 可以省略，因为 Java 接口中的普通抽象方法默认就是 `public abstract`：

```java
void save();
```

### 7.2 `BookDaoImpl` 实现

```java
public class BookDaoImpl implements BookDao {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public void save() {
        System.out.println("book dao save ..." + name);
    }
}
```

`name` 是一个私有字段，Spring 不需要直接访问它。XML 中的：

```xml
<property name="name" value="..."/>
```

会寻找并调用公共方法 `setName(...)`，这就是 setter 注入。

当前 `save()` 并没有持有 `DataSource`，也没有 JDBC 代码。因此它不是一个完整的数据库 DAO，只是一个用于观察注入结果的教学占位实现。

如果要让 DAO 真正访问数据库，还需要把 `DataSource` 注入 DAO，并使用 JDBC、Spring `JdbcTemplate`、MyBatis 或 JPA 等技术执行 SQL。

---

## 8. `App.java` 运行入口分析

### 8.1 创建 Spring 容器

```java
ApplicationContext ctx =
        new ClassPathXmlApplicationContext("applicationContext.xml");
```

`applicationContext.xml` 位于 `src/main/resources`。Maven 构建时它会被复制到类路径根目录，因此可以直接用文件名加载。

创建上下文时，Spring 会完成以下主要工作：

1. 读取并解析 XML；
2. 注册 Bean 定义；
3. 加载 properties 并替换占位符；
4. 创建默认的非懒加载单例 Bean；
5. 调用 setter 注入属性；
6. 返回可供查询的容器对象。

“创建 Bean 对象”和“建立数据库物理连接”不是同一个概念。Spring 可以先创建数据源对象，而数据源可以选择稍后再创建连接。

### 8.2 获取并打印 Druid 数据源

```java
DataSource dataSource_druid =
        (DataSource) ctx.getBean("dataSource_druid");
System.out.println(dataSource_druid);
```

`getBean("dataSource_druid")` 按 XML 中的 `id` 查找 Bean。旧式写法返回 `Object`，所以代码做了强制类型转换。

也可以使用带类型的重载，减少手动转换：

```java
DataSource dataSource = ctx.getBean("dataSource_druid", DataSource.class);
```

Druid 重写了 `toString()`，所以打印结果不是默认的“类名 + 哈希值”，而是连接池状态，例如：

```text
{
    CreateTime:"...",
    ActiveCount:0,
    PoolingCount:0,
    CreateCount:0,
    ...
}
```

关键字段：

| 字段 | 含义 |
| --- | --- |
| `ActiveCount` | 当前借出、正在使用的连接数 |
| `PoolingCount` | 当前池中空闲连接数 |
| `CreateCount` | 已创建过的物理连接数 |
| `DestroyCount` | 已销毁的物理连接数 |
| `ConnectCount` | 连接被借出的累计次数 |

本案例实测打印 Druid 时这些计数为 0，说明此时只是创建并配置了数据源对象，还没有通过它借用连接。

若配置 `initialSize > 0` 并主动初始化，或者显式调用 `init()` / `getConnection()`，Druid 会更早尝试连接数据库。数据库名错误、服务未启动、端口错误、认证失败等问题也通常会在真正建连时暴露。

### 8.3 获取并打印 C3P0 数据源

```java
DataSource dataSource_c3p0 =
        (DataSource) ctx.getBean("dataSource_c3p0");
System.out.println(dataSource_c3p0);
```

C3P0 的 `toString()` 会输出大量连接池配置。对这个版本的 C3P0，实测打印对象时还会触发连接池初始化，并按照默认的 `initialPoolSize=3` 异步尝试创建连接。

因此不能把“只是打印数据源，绝不会连接数据库”概括到所有连接池实现。准确说法是：

- Druid 在本案例当前配置下，打印时没有建立连接；
- C3P0 在本案例所用版本和配置下，打印会初始化连接池并尝试建连；
- 显式调用 `dataSource.getConnection()` 一定要求连接池提供一个可用连接。

### 8.4 获取并调用 DAO

```java
BookDao bookDao = (BookDao) ctx.getBean("bookDao");
bookDao.save();
```

容器中保存的实际对象类型是 `BookDaoImpl`，但变量使用接口类型 `BookDao`，降低了调用方与具体实现的耦合。

实测核心输出为：

```text
book dao save ...root666
```

这证明 `${username}` 已被解析，并通过 `setName()` 注入了 DAO。

### 8.5 `AppSystemProperties.java`

该类当前只有一个空 `main()` 方法：

```java
public class AppSystemProperties {
    public static void main(String[] args) {
    }
}
```

它不会被 Spring 自动运行，也不影响 `App.main()`。结合文件名推测，它可能原本用于演示 Java 系统属性，但当前案例中没有实现，可以暂时忽略。

---

---

[← 上一篇](03-Spring-XML配置详解.md) · [下一篇 →](05-数据库连接与验证.md)

# Maven 依赖与 DataSource

[← 上一篇](01-案例概览与运行流程.md) · [下一篇 →](03-Spring-XML配置详解.md)

> 理解 Spring、连接池、MySQL 驱动各自的职责，以及为什么面向 `DataSource` 接口编程。

## 4. Maven 依赖详解

`pom.xml` 中有四个核心依赖。

### 4.1 `spring-context`

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>5.2.10.RELEASE</version>
</dependency>
```

它提供 Spring IoC 容器及 XML 应用上下文等能力。本案例用到的主要类型包括：

- `ApplicationContext`：Spring 容器的核心接口；
- `ClassPathXmlApplicationContext`：从类路径读取 XML 并创建容器；
- XML 中的 `<bean>`、`<property>` 和 `<context:property-placeholder>`。

### 4.2 Druid

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid</artifactId>
    <version>1.1.16</version>
</dependency>
```

Druid 是 JDBC 数据库连接池。它负责维护一组可复用的数据库连接，避免每次执行 SQL 都重新完成 TCP 连接、数据库认证等昂贵操作。

### 4.3 C3P0

```xml
<dependency>
    <groupId>c3p0</groupId>
    <artifactId>c3p0</artifactId>
    <version>0.9.1.2</version>
</dependency>
```

C3P0 是另一个 JDBC 连接池实现。本案例同时配置两种连接池，目的是展示：**Spring 可以用相同的 `<bean>` + `<property>` 方式管理不同厂商的对象。**

项目通常只需选择一种连接池，不需要同时使用 Druid 和 C3P0。

### 4.4 MySQL JDBC 驱动

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>5.1.47</version>
</dependency>
```

连接池负责“管理连接”，JDBC 驱动负责“按 MySQL 协议建立连接并与数据库通信”。二者职责不同，通常缺一不可。

这个案例使用 MySQL Connector/J 5.x，因此驱动类写成：

```text
com.mysql.jdbc.Driver
```

如果项目升级到 MySQL Connector/J 8.x，通常应改为：

```text
com.mysql.cj.jdbc.Driver
```

---

## 5. 为什么统一使用 `DataSource` 接口

`App.java` 没有把返回值直接声明为 `DruidDataSource` 或 `ComboPooledDataSource`，而是使用：

```java
import javax.sql.DataSource;
```

```java
DataSource dataSource = (DataSource) ctx.getBean("dataSource_druid");
```

`javax.sql.DataSource` 是 JDBC 标准接口，Druid 和 C3P0 都实现了它。面向接口编程带来两个好处：

1. 上层代码不必依赖具体连接池厂商；
2. 将来替换连接池时，获取连接的业务代码通常不需要改变。

典型使用方式是：

```java
try (Connection connection = dataSource.getConnection()) {
    // 使用连接执行 SQL
}
```

`Connection.close()` 对连接池而言通常不是销毁物理连接，而是把连接归还给池，供后续请求复用。

---

---

[← 上一篇](01-案例概览与运行流程.md) · [下一篇 →](03-Spring-XML配置详解.md)

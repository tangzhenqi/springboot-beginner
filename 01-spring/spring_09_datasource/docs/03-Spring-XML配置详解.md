# Spring XML 配置详解

[← 上一篇](02-Maven依赖与DataSource.md) · [下一篇 →](04-DAO与启动流程.md)

> 逐段分析 `applicationContext.xml`，重点理解第三方 Bean、setter 注入和 properties 占位符。

## 6. `applicationContext.xml` 逐段分析

### 6.1 XML 命名空间

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="...">
```

- `beans` 命名空间支持 `<bean>`、`<property>` 等基本配置；
- `context` 命名空间支持 `<context:property-placeholder>` 等上下文扩展标签；
- `xsi:schemaLocation` 告诉 XML 解析器每个命名空间对应的 XSD 规则。

如果没有声明 `xmlns:context` 及对应的 schema，就不能使用 `context:` 标签。

### 6.2 直接写死参数的 Druid Bean

```xml
<bean id="dataSource_druid"
      class="com.alibaba.druid.pool.DruidDataSource">
    <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
    <property name="url" value="jdbc:mysql://localhost:3306/spring_db"/>
    <property name="username" value="root"/>
    <property name="password" value="root"/>
</bean>
```

Spring 的处理过程可以理解为：

```java
DruidDataSource dataSource = new DruidDataSource();
dataSource.setDriverClassName("com.mysql.jdbc.Driver");
dataSource.setUrl("jdbc:mysql://localhost:3306/spring_db");
dataSource.setUsername("root");
dataSource.setPassword("root");
```

也就是说：

- `class` 指定要实例化的类；
- `id` 是该对象在 Spring 容器中的唯一名称；
- `<property name="...">` 对应 JavaBean setter；
- `value` 注入字符串等简单值。

这也是 Spring 能管理第三方类的关键：即使不能修改第三方源码、不能给它加注解，只要类可以被创建并暴露合适的 setter，就可以通过 XML 配置。

### 6.3 直接写死参数的 C3P0 Bean

```xml
<bean id="dataSource_c3p0"
      class="com.mchange.v2.c3p0.ComboPooledDataSource">
    <property name="driverClass" value="com.mysql.jdbc.Driver"/>
    <property name="jdbcUrl" value="jdbc:mysql://localhost:3306/spring_db"/>
    <property name="user" value="root"/>
    <property name="password" value="root"/>
    <property name="maxPoolSize" value="1000"/>
</bean>
```

Druid 和 C3P0 的配置项名字不同：

| 含义 | Druid 属性 | C3P0 属性 |
| --- | --- | --- |
| 驱动类 | `driverClassName` | `driverClass` |
| JDBC 地址 | `url` | `jdbcUrl` |
| 用户名 | `username` | `user` |
| 密码 | `password` | `password` |

原因不是 Spring 对它们做了特殊规定，而是两个类暴露的 setter 名字不同。例如 `jdbcUrl` 对应 `setJdbcUrl(...)`。

`maxPoolSize="1000"` 会由 Spring 的类型转换机制从字符串转换为整数，再调用 `setMaxPoolSize(int)`。该值适合作为“可以配置池参数”的演示，但真实项目中通常不能盲目设为 1000，因为数据库允许的最大连接数、应用实例数量和服务器资源都有限。

### 6.4 加载 properties 文件

```xml
<context:property-placeholder
    location="classpath*:*.properties"
    system-properties-mode="NEVER"/>
```

这行配置会注册属性占位符处理器。在创建普通 Bean 之前，它会读取匹配的 properties 资源，并把 XML 中的 `${key}` 替换为相应的值。

本案例匹配到的项目资源包括：

```properties
# jdbc.properties
jdbc.driver=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://127.0.0.1:3306/spring_db
jdbc.username=root
jdbc.password=root
```

```properties
# jdbc2.properties
username=root666
```

`classpath*:` 表示在整个类路径中搜索匹配资源，范围可能包含当前项目输出目录以及依赖 JAR。这里再配合 `*.properties`，搜索范围很宽。

常见写法对比：

| 写法 | 含义和适用场景 |
| --- | --- |
| `classpath:jdbc.properties` | 从类路径读取一个明确命名的资源；日常配置更推荐 |
| `classpath:config/jdbc.properties` | 从类路径下指定目录读取一个资源 |
| `classpath*:jdbc.properties` | 搜索类路径中所有同名资源 |
| `classpath*:*.properties` | 搜索类路径根位置的所有 properties；演示方便，但可能误加载依赖中的文件 |
| `classpath:jdbc.properties,classpath:jdbc2.properties` | 显式加载多个文件，含义清楚、结果更可控 |

`classpath:` 不应简单理解为“只查当前工程”，`classpath*:` 也不应简单理解为“递归查找所有目录”。是否匹配子目录取决于路径模式；例如 `classpath*:/**/*.properties` 才明确带有递归目录模式。

#### `system-properties-mode="NEVER"` 的作用

它表示解析占位符时不使用 JVM system properties 作为候选值，只使用本地加载到的 properties 等配置值。

例如，本机或启动参数中可能存在名为 `username` 的系统属性。如果允许系统属性参与解析，通用名称可能发生意外冲突；设置为 `NEVER` 后，本案例的 `${username}` 会读取 `jdbc2.properties` 中的 `root666`。

不过，避免冲突的更好方法仍然是使用带业务前缀的键，例如：

```properties
demo.book.owner=root666
```

### 6.5 使用占位符创建另一个 Druid Bean

```xml
<bean class="com.alibaba.druid.pool.DruidDataSource">
    <property name="driverClassName" value="${jdbc.driver}"/>
    <property name="url" value="${jdbc.url}"/>
    <property name="username" value="${jdbc.username}"/>
    <property name="password" value="${jdbc.password}"/>
</bean>
```

经过占位符替换后，效果相当于把 `jdbc.properties` 中的值直接填入 XML。

这个 Bean 没有显式 `id`。Spring 仍会创建并管理它，但会生成类似下面的名称：

```text
com.alibaba.druid.pool.DruidDataSource#0
```

因此，当前容器实际配置了三个数据源 Bean：

1. `dataSource_druid`：参数直接写在 XML 中；
2. `dataSource_c3p0`：参数直接写在 XML 中；
3. 一个自动命名的 Druid Bean：参数来自 `jdbc.properties`。

`App.java` 只按名称获取了前两个，第三个只是为了演示属性占位符。若后续按类型执行 `ctx.getBean(DataSource.class)`，会因为存在多个候选 Bean 而产生“不唯一”的异常；此时应给 Bean 明确命名并按名称获取，或在更现代的注解配置中使用 `@Primary` / `@Qualifier` 消除歧义。

### 6.6 给 `BookDaoImpl` 注入普通属性

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl">
    <property name="name" value="${username}"/>
</bean>
```

`${username}` 在 `jdbc2.properties` 中定义为 `root666`，因此 Spring 创建 Bean 时等价于执行：

```java
BookDaoImpl bookDao = new BookDaoImpl();
bookDao.setName("root666");
```

随后调用 `bookDao.save()`，输出：

```text
book dao save ...root666
```

这里同时证明了两件事：

1. `context:property-placeholder` 能加载多个 properties 文件；
2. 占位符不仅能配置数据源，也能用于任意 Spring Bean 的简单属性注入。

---

---

[← 上一篇](02-Maven依赖与DataSource.md) · [下一篇 →](04-DAO与启动流程.md)

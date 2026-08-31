# `SqlSessionFactoryBean` 核心源码详解

> 对应方法：`SqlSessionFactoryBean.buildSqlSessionFactory()`
>
> 对应版本：`mybatis-spring 1.3.0`、`mybatis 3.5.6`、`spring 5.2.10`
>
> 本文结合当前 `spring_15_spring_mybatis` 案例讲解。重点不是逐行翻译源码，而是理解它如何把 Spring 中的各种配置组装成 MyBatis 的 `Configuration`，最后创建出 `SqlSessionFactory`。

## 1. 这个方法处在什么位置

`buildSqlSessionFactory()` 是 `mybatis-spring` 中 `SqlSessionFactoryBean` 的核心方法。

先看当前项目的配置：

```java
@Bean
public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
    SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
    ssfb.setTypeAliasesPackage("com.spring.domain");
    ssfb.setDataSource(dataSource);
    return ssfb;
}
```

这里创建的不是普通 `SqlSessionFactory`，而是：

```java
SqlSessionFactoryBean
```

它同时实现了两个重要接口：

```java
FactoryBean<SqlSessionFactory>
InitializingBean
```

两个接口分别决定不同职责：

| 接口 | 作用 |
| --- | --- |
| `InitializingBean` | Spring 完成属性注入后，调用 `afterPropertiesSet()` 初始化对象 |
| `FactoryBean<SqlSessionFactory>` | 工厂本身由 Spring 管理，但对外提供的主要产品是 `SqlSessionFactory` |

因此完整生命周期大致是：

```text
Spring 调用 @Bean 方法
      ↓
创建 SqlSessionFactoryBean
      ↓
调用 setTypeAliasesPackage(...)
调用 setDataSource(...)
      ↓
属性设置完成
      ↓
afterPropertiesSet()
      ↓
buildSqlSessionFactory()
      ↓
得到 SqlSessionFactory
      ↓
getObject() 将它提供给 Spring 容器
```

`afterPropertiesSet()` 的核心代码是：

```java
@Override
public void afterPropertiesSet() throws Exception {
    notNull(dataSource, "Property 'dataSource' is required");
    notNull(sqlSessionFactoryBuilder,
            "Property 'sqlSessionFactoryBuilder' is required");
    state((configuration == null && configLocation == null)
                    || !(configuration != null && configLocation != null),
            "Property 'configuration' and 'configLocation' " +
            "can not specified with together");

    this.sqlSessionFactory = buildSqlSessionFactory();
}
```

它先检查三个前提：

1. 必须有 `DataSource`；
2. 必须有 `SqlSessionFactoryBuilder`；
3. `configuration` 对象和 `configLocation` 配置文件不能同时指定。

为什么必须有 `DataSource`？因为 MyBatis 最终需要通过数据源获取 JDBC 连接。

为什么配置对象和配置文件不能同时指定？因为它们都是 MyBatis 全局配置的来源，同时存在时无法明确应该以哪个为基础。

需要注意：

```text
buildSqlSessionFactory()
不是执行 SQL 的方法，
而是程序启动时创建 MyBatis 运行环境的方法。
```

可以把它理解为组装工厂：

```text
各种配置零件
  ├─ DataSource
  ├─ 类型别名
  ├─ 插件
  ├─ TypeHandler
  ├─ 事务工厂
  ├─ Mapper XML
  └─ MyBatis 全局设置
            ↓
buildSqlSessionFactory()
            ↓
Configuration
            ↓
DefaultSqlSessionFactory
```

## 2. 第一步：确定 `Configuration` 从哪里来

方法开始先声明：

```java
Configuration configuration;
XMLConfigBuilder xmlConfigBuilder = null;
```

`Configuration` 是 MyBatis 最重要的全局配置对象。后续执行 SQL 所需的几乎所有信息都会保存在这里，例如：

- 数据源和事务工厂；
- Mapper 接口；
- `MappedStatement`；
- 类型别名；
- 类型处理器；
- 插件；
- 缓存；
- MyBatis 全局设置。

源码提供三种获得 `Configuration` 的方式，而且三选一。

**方式一：外部直接传入 `Configuration` 对象**

```java
if (this.configuration != null) {
    configuration = this.configuration;

    if (configuration.getVariables() == null) {
        configuration.setVariables(this.configurationProperties);
    } else if (this.configurationProperties != null) {
        configuration.getVariables()
                     .putAll(this.configurationProperties);
    }
}
```

调用方式大致是：

```java
Configuration configuration = new Configuration();
configuration.setMapUnderscoreToCamelCase(true);

sqlSessionFactoryBean.setConfiguration(configuration);
```

这时工厂直接使用传进来的配置对象。

`configurationProperties` 会被放入 `Configuration.variables`，用于替换配置中的 `${...}` 占位符。

如果原配置对象还没有 variables，就整体设置：

```java
configuration.setVariables(configurationProperties);
```

如果已经存在，就合并：

```java
configuration.getVariables().putAll(configurationProperties);
```

**方式二：读取 MyBatis 核心 XML 配置文件**

```java
else if (this.configLocation != null) {
    xmlConfigBuilder = new XMLConfigBuilder(
        this.configLocation.getInputStream(),
        null,
        this.configurationProperties
    );

    configuration = xmlConfigBuilder.getConfiguration();
}
```

对应的配置方式类似：

```java
ssfb.setConfigLocation(
    new ClassPathResource("SqlMapConfig.xml")
);
```

`XMLConfigBuilder` 专门负责解析 MyBatis 核心配置文件，例如：

```xml
<configuration>
    <settings>...</settings>
    <typeAliases>...</typeAliases>
    <plugins>...</plugins>
    <mappers>...</mappers>
</configuration>
```

这里先执行：

```java
xmlConfigBuilder.getConfiguration();
```

只是取得构建器内部正在组装的 `Configuration`。真正完整解析 XML 的 `parse()` 会在后面调用。

**方式三：创建默认 `Configuration`**

```java
else {
    configuration = new Configuration();
    configuration.setVariables(this.configurationProperties);
}
```

既没有传入配置对象，也没有指定 MyBatis 核心 XML 时，就创建默认配置。

当前案例走的正是第三条分支，因为项目只设置了：

```java
ssfb.setTypeAliasesPackage("com.spring.domain");
ssfb.setDataSource(dataSource);
```

没有调用：

```java
ssfb.setConfiguration(...);
ssfb.setConfigLocation(...);
```

所以当前项目实际执行：

```java
configuration = new Configuration();
```

三条分支可以总结为：

| 条件 | `Configuration` 来源 |
| --- | --- |
| 设置了 `configuration` | 直接使用传入的 Java 配置对象 |
| 否则，设置了 `configLocation` | 由 `XMLConfigBuilder` 读取 MyBatis 核心 XML |
| 两者都没有 | `new Configuration()` 使用默认配置 |

优先级是：

```text
configuration 对象
      ↓ 否则
configLocation 配置文件
      ↓ 否则
默认 Configuration
```

## 3. 第二步：向 `Configuration` 注册组件

确定基础 `Configuration` 后，源码开始把 `SqlSessionFactoryBean` 上设置的其他组件逐个注册进去。

这些组件大多数在当前入门项目中没有配置，但理解它们可以看清这个方法为什么是 MyBatis-Spring 的核心装配方法。

**注册 `ObjectFactory`**

```java
if (this.objectFactory != null) {
    configuration.setObjectFactory(this.objectFactory);
}
```

`ObjectFactory` 负责创建结果对象。

例如查询结果需要转换成：

```java
Account
```

MyBatis 默认通过默认对象工厂创建 `Account`。只有需要自定义对象实例化策略时，才需要替换它。

当前案例没有设置，使用 MyBatis 默认 `ObjectFactory`。

**注册 `ObjectWrapperFactory`**

```java
if (this.objectWrapperFactory != null) {
    configuration.setObjectWrapperFactory(
        this.objectWrapperFactory
    );
}
```

MyBatis 会把 Java 对象包装成统一的访问模型，以便读写属性。

例如结果映射时要完成：

```text
Account.name 属性
      ↓
调用 setName(...)
```

普通 JavaBean 使用默认包装逻辑即可。只有特殊对象结构才需要自定义 `ObjectWrapperFactory`。

**注册 VFS 实现**

```java
if (this.vfs != null) {
    configuration.setVfsImpl(this.vfs);
}
```

VFS 是 Virtual File System，主要用于扫描 classpath 中的类和资源。

普通 Spring 项目通常使用默认实现。在特殊容器或特殊打包环境中，可能需要自定义 VFS。

**扫描并注册类型别名**

```java
if (hasLength(this.typeAliasesPackage)) {
    String[] typeAliasPackageArray =
        tokenizeToStringArray(
            this.typeAliasesPackage,
            ConfigurableApplicationContext
                .CONFIG_LOCATION_DELIMITERS
        );

    for (String packageToScan : typeAliasPackageArray) {
        configuration.getTypeAliasRegistry()
                     .registerAliases(
                         packageToScan,
                         typeAliasesSuperType == null
                             ? Object.class
                             : typeAliasesSuperType
                     );
    }
}
```

当前项目设置了：

```java
ssfb.setTypeAliasesPackage("com.spring.domain");
```

所以会扫描：

```text
com.spring.domain
```

并发现：

```java
com.spring.domain.Account
```

之后在 Mapper XML 中可以使用短名称：

```xml
resultType="Account"
```

而不必写全限定名：

```xml
resultType="com.spring.domain.Account"
```

`typeAliasesSuperType` 可以限制只给某个父类型的子类注册别名。

当前项目没有设置，所以使用：

```java
Object.class
```

也就是不额外限制父类型。

源码还支持直接注册指定类型：

```java
if (!isEmpty(this.typeAliases)) {
    for (Class<?> typeAlias : this.typeAliases) {
        configuration.getTypeAliasRegistry()
                     .registerAlias(typeAlias);
    }
}
```

两种方式的区别：

| 配置 | 作用 |
| --- | --- |
| `typeAliasesPackage` | 扫描整个包，批量注册 |
| `typeAliases` | 明确指定若干 Class，逐个注册 |

当前案例的 SQL 都写在注解中，返回类型也能从方法签名推断，所以类型别名暂时没有明显作用；它在 Mapper XML 中更常用。

**注册 MyBatis 插件**

```java
if (!isEmpty(this.plugins)) {
    for (Interceptor plugin : this.plugins) {
        configuration.addInterceptor(plugin);
    }
}
```

MyBatis 插件实现：

```java
Interceptor
```

可以拦截 MyBatis 内部关键对象的方法，常见用途包括：

- 分页；
- SQL 性能统计；
- SQL 审计；
- 数据权限；
- 特定字段自动处理。

插件不是拦截任意 Java 方法，而是按照 MyBatis 插件机制拦截 Executor、StatementHandler、ParameterHandler 或 ResultSetHandler 等对象。

当前项目没有配置插件，所以这一段跳过。

**扫描并注册 `TypeHandler`**

包扫描方式：

```java
if (hasLength(this.typeHandlersPackage)) {
    String[] packages = tokenizeToStringArray(...);

    for (String packageToScan : packages) {
        configuration.getTypeHandlerRegistry()
                     .register(packageToScan);
    }
}
```

直接指定方式：

```java
if (!isEmpty(this.typeHandlers)) {
    for (TypeHandler<?> typeHandler : this.typeHandlers) {
        configuration.getTypeHandlerRegistry()
                     .register(typeHandler);
    }
}
```

`TypeHandler` 负责 Java 类型和 JDBC 类型之间的转换。

例如：

```text
Java Integer ↔ JDBC INTEGER
Java String  ↔ JDBC VARCHAR
Java Date    ↔ JDBC TIMESTAMP
```

本案例中的 `Integer`、`String`、`Double` 都有内置 TypeHandler，不需要自定义。

如果数据库中用一个字符串保存复杂枚举或 JSON，就可能需要自定义 TypeHandler。

**识别数据库厂商**

```java
if (this.databaseIdProvider != null) {
    try {
        configuration.setDatabaseId(
            this.databaseIdProvider
                .getDatabaseId(this.dataSource)
        );
    } catch (SQLException e) {
        throw new NestedIOException(
            "Failed getting a databaseId", e
        );
    }
}
```

`DatabaseIdProvider` 用于识别当前数据库厂商，例如：

```text
MySQL
Oracle
PostgreSQL
```

同一个 Mapper 中可以针对不同厂商提供不同 SQL。

源码注释强调：

```text
必须在解析 Mapper XML 之前设置 databaseId
```

因为解析 Mapper 时需要根据 databaseId 判断应该选择哪条数据库专用 SQL。

当前案例只使用 MySQL，也没有配置 `DatabaseIdProvider`，所以跳过。

**注册缓存**

```java
if (this.cache != null) {
    configuration.addCache(this.cache);
}
```

这里可以向 MyBatis 全局配置加入自定义 Cache 实现。

当前项目没有配置，因此不执行。

这一大段的共同结构是：

```java
if (用户配置了某组件) {
    configuration.注册该组件();
}
```

也就是说，`SqlSessionFactoryBean` 把 Spring 配置方式转换成了 MyBatis `Configuration` 内部的注册操作。

## 4. 第三步：解析配置、建立环境和 Mapper

完成可选组件注册后，源码开始处理 XML、事务、数据源和 Mapper 映射。

**完整解析 MyBatis 核心配置文件**

```java
if (xmlConfigBuilder != null) {
    try {
        xmlConfigBuilder.parse();
    } catch (Exception ex) {
        throw new NestedIOException(
            "Failed to parse config resource: "
                    + this.configLocation,
            ex
        );
    } finally {
        ErrorContext.instance().reset();
    }
}
```

只有前面选择了 `configLocation` 分支，`xmlConfigBuilder` 才不为 `null`。

`parse()` 会读取核心 XML 中的全局配置，例如：

```xml
<settings>
    <setting name="mapUnderscoreToCamelCase"
             value="true"/>
</settings>
```

以及 XML 中定义的别名、插件、类型处理器和 Mapper 等内容。

当前项目没有给 `SqlSessionFactoryBean` 设置 `configLocation`，所以：

```java
xmlConfigBuilder == null
```

这一段不会执行。

`ErrorContext` 是 MyBatis 用来保存当前解析错误上下文的辅助对象。它通常使用线程局部状态；无论成功还是失败都执行 `reset()`，可以避免错误信息污染后续解析。

**选择事务工厂**

```java
if (this.transactionFactory == null) {
    this.transactionFactory =
        new SpringManagedTransactionFactory();
}
```

如果用户没有指定 MyBatis `TransactionFactory`，默认使用：

```java
SpringManagedTransactionFactory
```

它的作用是让 MyBatis 获取的连接能够与 Spring 事务体系协调。

但要准确区分两个概念：

```text
存在 SpringManagedTransactionFactory
不等于项目已经启用了 @Transactional 事务
```

当前案例还没有声明 `DataSourceTransactionManager`，也没有启用 `@Transactional`。因此它还不具备“Service 中多次数据库操作统一提交和回滚”的完整 Spring 业务事务。

`SpringManagedTransactionFactory` 可以理解为整合接口；真正的 Spring 声明式事务还需要事务管理器和事务边界。

**创建 MyBatis `Environment`**

```java
configuration.setEnvironment(
    new Environment(
        this.environment,
        this.transactionFactory,
        this.dataSource
    )
);
```

`Environment` 把三个东西绑定在一起：

| 内容 | 当前案例中的值 |
| --- | --- |
| 环境 id | 默认 `SqlSessionFactoryBean` |
| 事务工厂 | `SpringManagedTransactionFactory` |
| 数据源 | Spring 容器中的 Druid `DataSource` |

结构可以表示为：

```text
Configuration
    └─ Environment
         ├─ id
         ├─ TransactionFactory
         └─ DataSource
```

以后 MyBatis 创建 `SqlSession`、Executor 和 Transaction 时，就能从 `Configuration.environment` 找到事务工厂和数据源。

源码会覆盖核心 XML 中原有的 Environment。这是因为 Spring 整合后，数据源应该以 Spring 注入给 `SqlSessionFactoryBean` 的对象为准。

如果仍让 MyBatis XML 自己创建另一个数据源，就可能出现：

```text
Spring 事务管理的是数据源 A
MyBatis 执行 SQL 使用数据源 B
```

这样事务无法正确协调。

**解析 Mapper XML**

```java
if (!isEmpty(this.mapperLocations)) {
    for (Resource mapperLocation : this.mapperLocations) {
        if (mapperLocation == null) {
            continue;
        }

        try {
            XMLMapperBuilder xmlMapperBuilder =
                new XMLMapperBuilder(
                    mapperLocation.getInputStream(),
                    configuration,
                    mapperLocation.toString(),
                    configuration.getSqlFragments()
                );

            xmlMapperBuilder.parse();
        } catch (Exception e) {
            throw new NestedIOException(
                "Failed to parse mapping resource: '"
                        + mapperLocation + "'",
                e
            );
        } finally {
            ErrorContext.instance().reset();
        }
    }
}
```

`mapperLocations` 指向独立的 Mapper XML 文件，例如：

```text
classpath*:com/spring/dao/*Mapper.xml
```

`XMLMapperBuilder.parse()` 会解析：

- `<mapper namespace="...">`；
- `<select>`；
- `<insert>`；
- `<update>`；
- `<delete>`；
- `<resultMap>`；
- `<sql>` SQL 片段；
- 二级缓存配置。

解析后，SQL 会变成 `MappedStatement` 放进 `Configuration`。

例如：

```xml
<mapper namespace="com.spring.dao.AccountDao">
    <select id="findById" resultType="Account">
        select * from tbl_account where id = #{id}
    </select>
</mapper>
```

会注册类似：

```text
com.spring.dao.AccountDao.findById
```

这个 statement id 由：

```text
namespace + "." + 方法/语句 id
```

组成。

**当前项目为什么没有 Mapper XML 也能运行**

当前案例的 SQL 写在接口注解中：

```java
@Select("select * from tbl_account where id = #{id}")
Account findById(Integer id);
```

而且没有设置：

```java
ssfb.setMapperLocations(...);
```

所以当前源码执行时：

```java
mapperLocations == null
```

Mapper XML 解析循环不会执行。

那么注解 SQL 在哪里解析？

是在后续 `MapperFactoryBean` 初始化 Mapper 接口时：

```text
MapperFactoryBean.checkDaoConfig()
      ↓
configuration.addMapper(AccountDao.class)
      ↓
MapperAnnotationBuilder 解析 @Select 等注解
      ↓
注册 MappedStatement
```

因此要区分两条 SQL 映射加载路线：

| SQL 写法 | 主要解析位置 |
| --- | --- |
| Mapper XML | 本方法中的 `XMLMapperBuilder.parse()` |
| `@Select` 等注解 | 注册 Mapper 接口时由 `MapperAnnotationBuilder` 解析 |

这也解释了为什么 `buildSqlSessionFactory()` 执行完成时，当前项目的 `Configuration` 里不一定已经包含所有注解 Mapper；它们可以在 `MapperFactoryBean` 初始化时继续加入同一个 `Configuration`。

## 5. 第四步：创建真正的 `SqlSessionFactory`

方法最后只有一行：

```java
return this.sqlSessionFactoryBuilder.build(configuration);
```

看起来简单，但前面所有工作都是为了准备这个 `configuration`。

当前 MyBatis 版本中，`SqlSessionFactoryBuilder` 的对应方法大致是：

```java
public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
}
```

所以最终真正创建的是：

```java
DefaultSqlSessionFactory
```

它内部保存已经组装好的 `Configuration`：

```text
DefaultSqlSessionFactory
    └─ Configuration
         ├─ Environment
         │    ├─ DataSource
         │    └─ TransactionFactory
         ├─ TypeAliasRegistry
         ├─ TypeHandlerRegistry
         ├─ InterceptorChain
         ├─ MappedStatements
         ├─ MapperRegistry
         └─ 其他全局设置
```

`SqlSessionFactory` 后续可以：

```java
SqlSession sqlSession = sqlSessionFactory.openSession();
```

创建 `SqlSession`。

然后：

```java
AccountDao accountDao =
    sqlSession.getMapper(AccountDao.class);
```

获取 Mapper 代理。

在 Spring 整合环境中，这些操作通常由 `MapperFactoryBean` 和 `SqlSessionTemplate` 代替业务代码完成。

**为什么还要有 `FactoryBean` 的 `getObject()`**

`buildSqlSessionFactory()` 把结果保存到字段：

```java
this.sqlSessionFactory = buildSqlSessionFactory();
```

之后 Spring 取用工厂产品时调用：

```java
@Override
public SqlSessionFactory getObject() throws Exception {
    if (this.sqlSessionFactory == null) {
        afterPropertiesSet();
    }

    return this.sqlSessionFactory;
}
```

因此容器中有两层对象：

```text
SqlSessionFactoryBean：负责组装和生产
SqlSessionFactory：真正提供 openSession() 能力的产品
```

普通情况下：

```java
ctx.getBean("sqlSessionFactory")
```

拿到的是 `SqlSessionFactory` 产品。

如果要获取工厂本身，Spring 的 `FactoryBean` 规则是使用 `&`：

```java
ctx.getBean("&sqlSessionFactory")
```

拿到的是 `SqlSessionFactoryBean`。

`SqlSessionFactoryBean.isSingleton()` 返回 `true`，所以它生产的 `SqlSessionFactory` 是单例产品。一个应用通常也只需要一个对应数据源的 `SqlSessionFactory`。

**构建工厂时是否已经连接数据库**

在当前案例中，构建 `SqlSessionFactory` 主要是在内存中组装配置，并不因为下面这行就立即执行账户查询：

```java
new DefaultSqlSessionFactory(configuration);
```

真正的数据库连接通常在打开会话、执行 Mapper SQL 时从 `DataSource` 获取。

需要注意，如果配置了需要访问数据库元数据的组件，例如 `DatabaseIdProvider`，构建期间就可能通过 DataSource 获取连接来识别数据库厂商。当前案例没有配置它。

## 6. 当前案例的真实执行路径与总结

把源码代入当前项目配置，可以明确哪些分支执行、哪些分支跳过。

当前设置：

```java
SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
ssfb.setTypeAliasesPackage("com.spring.domain");
ssfb.setDataSource(dataSource);
```

对应字段状态大致如下：

| 字段 | 当前值 | 结果 |
| --- | --- | --- |
| `configuration` | `null` | 不走直接配置对象分支 |
| `configLocation` | `null` | 不解析 MyBatis 核心 XML |
| `configurationProperties` | `null` | 不合并额外变量 |
| `typeAliasesPackage` | `com.spring.domain` | 扫描并注册 `Account` 别名 |
| `typeAliases` | 空 | 跳过逐个别名注册 |
| `plugins` | 空 | 不注册插件 |
| `typeHandlersPackage` | 空 | 不扫描自定义 TypeHandler |
| `typeHandlers` | 空 | 不注册指定 TypeHandler |
| `databaseIdProvider` | `null` | 不识别数据库厂商 |
| `cache` | `null` | 不注册自定义缓存 |
| `transactionFactory` | `null` | 创建 `SpringManagedTransactionFactory` |
| `dataSource` | Druid 数据源 | 写入 `Environment` |
| `mapperLocations` | 空 | 不解析 Mapper XML |

实际执行路径可以压缩成：

```text
afterPropertiesSet()
  │
  ├─ 检查 DataSource 不为 null
  ├─ 检查 SqlSessionFactoryBuilder 不为 null
  └─ 调用 buildSqlSessionFactory()
          │
          ├─ configuration 为空
          ├─ configLocation 为空
          ├─ new Configuration()
          │
          ├─ 扫描 com.spring.domain
          │    └─ 注册 Account 类型别名
          │
          ├─ plugins 为空，跳过
          ├─ 自定义 TypeHandler 为空，跳过
          ├─ DatabaseIdProvider 为空，跳过
          ├─ 核心 XML 构建器为空，跳过
          │
          ├─ 创建 SpringManagedTransactionFactory
          │
          ├─ 创建 Environment
          │    ├─ transactionFactory
          │    └─ Druid DataSource
          │
          ├─ mapperLocations 为空，跳过 Mapper XML
          │
          └─ new DefaultSqlSessionFactory(configuration)
```

随后才是 Mapper 代理注册：

```text
MapperScannerConfigurer 扫描 com.spring.dao
      ↓
发现 AccountDao
      ↓
注册 MapperFactoryBean<AccountDao>
      ↓
configuration.addMapper(AccountDao.class)
      ↓
解析 @Select、@Insert、@Update、@Delete
      ↓
创建 AccountDao Mapper 代理
```

最后执行查询：

```text
accountDao.findById(1)
      ↓
MapperProxy 拦截
      ↓
找到 AccountDao.findById 的 MappedStatement
      ↓
#{id} 转换成 ? 并绑定 1
      ↓
通过 Environment 中的 DataSource 获取连接
      ↓
执行 SQL
      ↓
结果映射成 Account
```

**核心对象关系**

| 对象 | 作用 |
| --- | --- |
| `SqlSessionFactoryBean` | 接收 Spring 配置并组装 MyBatis 运行环境 |
| `Configuration` | 保存 MyBatis 的全部全局配置和 SQL 映射 |
| `Environment` | 把环境 id、事务工厂和数据源绑定起来 |
| `SqlSessionFactoryBuilder` | 根据 `Configuration` 创建工厂 |
| `DefaultSqlSessionFactory` | 最终的 `SqlSessionFactory` 实现 |
| `MapperFactoryBean` | 使用 `SqlSessionFactory` 创建 Mapper 代理 |
| `SqlSessionTemplate` | 在 Spring 中协调 `SqlSession` 与资源使用 |

**初学者容易误解的几点**

1. `SqlSessionFactoryBean` 不是 `SqlSessionFactory`，前者是 Spring 工厂 Bean，后者是它生产的产品。
2. `buildSqlSessionFactory()` 主要组装配置，不负责执行具体业务 SQL。
3. `Configuration` 不只是几个开关，它是 MyBatis 整个运行时配置中心。
4. `SpringManagedTransactionFactory` 存在，不代表当前项目已经启用完整的 `@Transactional` 业务事务。
5. 当前案例没有 Mapper XML，所以 `mapperLocations` 分支跳过；注解 SQL 会在注册 Mapper 接口时解析。
6. 最后一行 `build(configuration)` 本身很简单，真正重要的是前面如何把所有信息装进 `Configuration`。

最后用一段话概括整个方法：

> `buildSqlSessionFactory()` 先选择或创建一个 MyBatis `Configuration`，再把类型别名、插件、TypeHandler、数据库厂商、缓存、事务工厂、数据源和 Mapper XML 等配置注册进去，最后把组装完成的 `Configuration` 交给 `SqlSessionFactoryBuilder`，创建一个持有该配置的 `DefaultSqlSessionFactory`。

最核心的主线是：

```text
Spring 配置属性
      ↓
SqlSessionFactoryBean
      ↓
buildSqlSessionFactory()
      ↓
Configuration
      ↓
DefaultSqlSessionFactory
      ↓
SqlSession / Mapper 代理
      ↓
执行 SQL
```

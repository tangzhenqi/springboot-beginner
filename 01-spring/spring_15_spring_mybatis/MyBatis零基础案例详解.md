# MyBatis 零基础案例详解：`spring_15_spring_mybatis`

> 适合人群：已经知道 Java 类、接口、Maven、Spring IoC/DI，但没有学习过 MyBatis。
>
> 学习目标：不只让程序跑起来，还要能回答“接口为什么不用实现类”“`#{id}` 从哪里取值”“查询结果怎么变成 Java 对象”“Spring 到底替我们省掉了什么”。

## 1. 先说结论：这个案例完成了什么

这个案例用 Java 代码查询 MySQL 中的账户表：

```text
tbl_account 数据表
        ↓ MyBatis 执行 SQL、转换数据
AccountDao 接口
        ↓ Spring 注入
AccountServiceImpl
        ↓ 启动类调用
App2 输出 Account 对象
```

运行 `App2` 后，核心代码只有：

```java
AccountService accountService = ctx.getBean(AccountService.class);
Account ac = accountService.findById(1);
System.out.println(ac);
```

但这三行背后发生了很多事：Spring 创建数据源，MyBatis 创建 `SqlSessionFactory`，扫描 `AccountDao`，生成接口代理对象，执行 SQL，再把查询结果转换成 `Account`。

本项目特意保留了两种运行方式：

| 入口 | 写法 | 用途 |
| --- | --- | --- |
| `App.java` | 原生 MyBatis | 看清 MyBatis 本来的使用步骤 |
| `App2.java` | Spring + MyBatis | 看清 Spring 整合后省掉了什么 |

建议先理解 `App`，再理解 `App2`。

## 2. MyBatis 是什么

Java 直接使用 JDBC 查询数据库，通常要做这些事：

1. 获取数据库连接；
2. 编写 SQL；
3. 创建 `PreparedStatement`；
4. 给 SQL 占位符赋值；
5. 执行 SQL；
6. 遍历 `ResultSet`；
7. 手工把每一列放进 Java 对象；
8. 关闭结果集、语句和连接。

MyBatis 是一个持久层框架。它仍然让开发者自己控制 SQL，但替我们处理大量重复的 JDBC 代码，尤其是：

- 参数绑定：把 Java 参数放入 SQL；
- 结果映射：把数据库的一行数据转换成 Java 对象；
- 资源管理：配合 Spring 获取、使用和释放数据库连接；
- Mapper 代理：只写接口和 SQL，不必手写 Dao 实现类。

如果你想先通过一段完整代码理解上面的 JDBC 步骤，可以阅读独立案例：[JDBC与MyBatis查询账户对比案例.md](JDBC与MyBatis查询账户对比案例.md)。它使用“根据 id 查询账户”这个具体需求，对照展示 JDBC 与 MyBatis 的全部代码。

可以先用下面这个公式记忆：

```text
MyBatis = 自己写 SQL + 框架完成 JDBC 模板代码和对象映射
```

MyBatis 不是数据库，也不替代 MySQL；它也不是连接池。这个案例里各工具的分工是：

| 工具 | 职责 |
| --- | --- |
| MySQL | 保存真实数据，执行 SQL |
| MySQL Driver | 让 Java 能通过 JDBC 与 MySQL 通信 |
| Druid | 管理和复用数据库连接 |
| MyBatis | 参数绑定、执行 SQL、结果映射、生成 Mapper 代理 |
| mybatis-spring | 把 MyBatis 对象接入 Spring 容器 |
| Spring | 创建 Bean、注入依赖，管理各层对象 |

## 3. 必须先认识的 8 个名词

| 名词 | 在本案例中的例子 | 通俗理解 |
| --- | --- | --- |
| Entity / Domain | `Account` | Java 中承载一行账户数据的对象 |
| Mapper / Dao | `AccountDao` | 声明“要对数据库做什么”的接口 |
| SQL 映射 | `@Select(...)` | 接口方法和 SQL 的对应关系 |
| DataSource | `DruidDataSource` | 数据库连接的来源/连接池 |
| SqlSession | `App` 中的 `sqlSession` | 一次与 MyBatis 交互的会话入口 |
| SqlSessionFactory | `sqlSessionFactory` | 专门生产 `SqlSession` 的工厂 |
| Mapper 代理 | `AccountDao` 的运行时对象 | MyBatis 动态生成的接口实现对象 |
| Service | `AccountServiceImpl` | 组织业务逻辑，调用 Dao，不直接写 SQL |

Dao 和 Mapper 在这个案例里可以理解成同一层。类名叫 `AccountDao`，MyBatis 文档通常称它为 Mapper 接口。

## 4. 项目结构

```text
spring_15_spring_mybatis
├── pom.xml
├── MyBatis零基础案例详解.md              本文
├── Spring 整合 MyBatis.md                整合知识总结
├── MapperScannerConfigurer.md            Mapper 扫描器原理扩展
└── src/main
    ├── java
    │   ├── App.java                      原生 MyBatis 入口
    │   ├── App2.java                     Spring 整合入口
    │   └── com/spring
    │       ├── config
    │       │   ├── SpringConfig.java      Spring 总配置
    │       │   ├── JdbcConfig.java        创建数据源
    │       │   └── MybatisConfig.java     创建 MyBatis 核心对象、扫描 Mapper
    │       ├── domain/Account.java        实体类
    │       ├── dao/AccountDao.java        Mapper 接口和 SQL
    │       ├── service/AccountService.java
    │       └── service/impl/AccountServiceImpl.java
    └── resources
        ├── jdbc.properties               数据库连接参数
        └── SqlMapConfig.xml.bak          原生 MyBatis 配置
```

## 5. 运行前准备数据库

### 5.1 建库、建表、插入测试数据

在 MySQL 中执行：

```sql
CREATE DATABASE IF NOT EXISTS spring_db
    DEFAULT CHARACTER SET utf8;

USE spring_db;

CREATE TABLE IF NOT EXISTS tbl_account (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(35),
    money DOUBLE
);

INSERT INTO tbl_account(name, money)
VALUES ('Tom', 1000), ('Jerry', 2000);
```

如果多次执行插入语句，会出现重复数据。学习时可以先执行：

```sql
SELECT * FROM tbl_account;
```

确认至少有一条 `id = 1` 的数据，因为 `App2` 查询的正是它。

### 5.2 配置数据库连接

打开 `src/main/resources/jdbc.properties`，根据自己的 MySQL 环境填写：

```properties
jdbc.driver=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://localhost:3306/spring_db?useSSL=false
jdbc.username=root
jdbc.password=填写你自己的密码
```

四个属性分别表示：

- `driver`：MySQL 5.x JDBC 驱动类；
- `url`：连接本机 MySQL 的 `spring_db` 数据库；
- `username`：数据库用户；
- `password`：数据库密码。

不要把真实生产密码提交到公开仓库。本案例是本地教学项目，实际项目通常使用环境变量或配置中心保存敏感配置。

## 6. 数据表为什么能对应 `Account` 类

表结构和 Java 类的对应关系如下：

| 数据库列 | 数据库类型 | Java 属性 | Java 类型 |
| --- | --- | --- | --- |
| `id` | `INT` | `id` | `Integer` |
| `name` | `VARCHAR` | `name` | `String` |
| `money` | `DOUBLE` | `money` | `Double` |

`Account.java` 的核心就是三个私有属性和对应的 getter/setter：

```java
public class Account implements Serializable {
    private Integer id;
    private String name;
    private Double money;

    // getter、setter、toString
}
```

查询 `select * from tbl_account` 后，MyBatis 大致做的是：

```java
Account account = new Account();
account.setId(resultSet.getInt("id"));
account.setName(resultSet.getString("name"));
account.setMoney(resultSet.getDouble("money"));
```

实际代码由 MyBatis 完成。因为列名 `id/name/money` 与 Java 属性名完全相同，所以能自动映射。

如果列名和属性名不同，要用 SQL 别名或显式结果映射。例如表中叫 `account_name`，Java 中叫 `name`：

```sql
SELECT id, account_name AS name, money
FROM tbl_account;
```

## 7. `AccountDao`：本案例的 MyBatis 核心

完整接口如下：

```java
public interface AccountDao {

    @Insert("insert into tbl_account(name,money)values(#{name},#{money})")
    void save(Account account);

    @Delete("delete from tbl_account where id = #{id} ")
    void delete(Integer id);

    @Update("update tbl_account set name = #{name} , money = #{money} where id = #{id} ")
    void update(Account account);

    @Select("select * from tbl_account")
    List<Account> findAll();

    @Select("select * from tbl_account where id = #{id} ")
    Account findById(Integer id);
}
```

### 7.1 SQL 注解和方法如何对应

| 方法 | 注解 | 输入 | 输出 | 用途 |
| --- | --- | --- | --- | --- |
| `save` | `@Insert` | 一个 `Account` | 无 | 新增账户 |
| `delete` | `@Delete` | 一个 id | 无 | 按 id 删除 |
| `update` | `@Update` | 一个 `Account` | 无 | 按 id 更新姓名和余额 |
| `findAll` | `@Select` | 无 | `List<Account>` | 查询全部账户 |
| `findById` | `@Select` | 一个 id | `Account` | 按 id 查询一个账户 |

MyBatis 会同时查看方法参数、返回值和 SQL 注解：

- 参数决定 SQL 占位符从哪里取值；
- 返回值决定查一条还是查多条，以及结果要转换成什么类型；
- 注解决定要执行的 SQL。

### 7.2 `#{}` 到底是什么意思

先看保存方法：

```java
void save(Account account);
```

假设传入：

```java
Account account = new Account();
account.setName("Lucy");
account.setMoney(3000.0);
accountDao.save(account);
```

SQL 中的：

```sql
VALUES (#{name}, #{money})
```

会从 `account` 对象调用 `getName()` 和 `getMoney()` 取值。它最终类似 JDBC 的预编译语句：

```sql
INSERT INTO tbl_account(name, money) VALUES (?, ?)
```

然后分别给两个 `?` 绑定 `Lucy` 和 `3000.0`。这不是简单的字符串拼接。

再看：

```java
Account findById(Integer id);
```

这里参数是一个简单类型，`#{id}` 就绑定传入的整数。

### 7.3 `#{}` 和 `${}` 不要混淆

| 写法 | 工作方式 | SQL 注入风险 | 常见用途 |
| --- | --- | --- | --- |
| `#{value}` | 生成 `?` 并安全绑定参数 | 低 | 普通查询条件和值，默认选择 |
| `${value}` | 把文本直接拼进 SQL | 高 | 极少数不能参数化的结构，如受控的列名 |

初学阶段可以记住：**数据库中的值几乎都使用 `#{}`，不要为了省事改成 `${}`。**

### 7.4 多个参数时要怎么写

本案例的方法要么只有一个简单参数，要么传一个对象。如果以后一个方法有多个独立参数，建议明确使用 `@Param`：

```java
Account findByNameAndMoney(
    @Param("name") String name,
    @Param("money") Double money
);
```

对应 SQL：

```java
@Select("select * from tbl_account where name = #{name} and money = #{money}")
```

这样参数名清晰，也不会依赖编译器是否保留方法参数名。

### 7.5 接口没有实现类，为什么能执行 SQL

`AccountDao` 的确没有 `AccountDaoImpl`。程序运行时，MyBatis 使用 JDK 动态代理生成一个实现 `AccountDao` 接口的对象。

调用：

```java
accountDao.findById(1);
```

并不是进入我们编写的方法体，因为接口根本没有方法体，而是被代理对象拦截：

```text
findById(1)
  → 找到 AccountDao.findById 上的 @Select
  → 读取 SQL
  → 把 1 绑定到 #{id}
  → 从 DataSource 获取连接
  → JDBC 执行查询
  → 把结果行转换为 Account
  → 返回 Account
```

因此 Mapper 接口必须同时满足两件事：

1. MyBatis 能发现这个接口；
2. MyBatis 能找到每个方法对应的 SQL。

本案例分别由 `MapperScannerConfigurer` 和 `@Select/@Insert/@Update/@Delete` 完成。

如果要继续追踪“扫描接口、注册 `MapperFactoryBean`、创建 JDK 动态代理、拦截方法并执行 SQL”的源码级链路，阅读独立专题：[Mapper代理生成与执行原理详解.md](Mapper代理生成与执行原理详解.md)。

## 8. 原生 MyBatis 路线：逐行理解 `App`

`App.java` 没有启动 Spring，目的是展示 MyBatis 自己怎样工作。

### 第 1 步：创建构建器

```java
SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
```

它是临时的构建工具，用来根据配置创建 `SqlSessionFactory`。

### 第 2 步：读取核心配置

```java
InputStream inputStream =
    Resources.getResourceAsStream("SqlMapConfig.xml.bak");
```

虽然扩展名是 `.bak`，文件仍在 classpath 中，而且代码按完整文件名读取，所以可以加载。`.bak` 只是用来表达“Spring 整合后不再依赖它”。

配置文件包含四部分：

```xml
<properties resource="jdbc.properties"/>
<typeAliases>...</typeAliases>
<environments>...</environments>
<mappers>...</mappers>
```

| 配置 | 作用 |
| --- | --- |
| `properties` | 读取数据库连接参数 |
| `typeAliases` | 给实体类注册类型别名 |
| `environments` | 配置数据源与 MyBatis 事务方式 |
| `mappers` | 告诉 MyBatis 去哪里寻找 Mapper 接口 |

### 第 3 步：创建 `SqlSessionFactory`

```java
SqlSessionFactory sqlSessionFactory = builder.build(inputStream);
```

工厂会解析配置。正常项目中，它是重量级且线程安全的对象，一个应用通常创建一个并长期复用。

### 第 4 步：打开 `SqlSession`

```java
SqlSession sqlSession = sqlSessionFactory.openSession();
```

`SqlSession` 是执行 Mapper/SQL 的入口。它不是线程安全对象，不应该做成全局共享变量。

### 第 5 步：获得 Mapper 代理并查询

```java
AccountDao accountDao = sqlSession.getMapper(AccountDao.class);
Account account = accountDao.findById(2);
```

`getMapper` 不是找我们写的实现类，而是让 MyBatis 创建一个动态代理对象。

### 第 6 步：关闭会话

```java
sqlSession.close();
```

关闭会话会释放它占用的资源。更稳妥的原生写法是使用 `try/finally` 或 try-with-resources，避免发生异常时漏关。

### 原生路线执行增删改时为什么还要 `commit`

`openSession()` 默认不是自动提交。若在 `App` 中调用 `save/update/delete`，成功后需要：

```java
sqlSession.commit();
```

异常时则调用：

```java
sqlSession.rollback();
```

当前 `App` 只做查询，所以没有写 `commit()`。

## 9. Spring 整合路线：逐个理解配置类

整合的目标不是让 MyBatis 消失，而是把 MyBatis 对象的创建和生命周期交给 Spring。

### 9.1 `SpringConfig`：总开关

```java
@Configuration
@ComponentScan("com.spring")
@PropertySource("classpath:jdbc.properties")
@Import({JdbcConfig.class, MybatisConfig.class})
public class SpringConfig {
}
```

| 注解 | 在本案例中的作用 |
| --- | --- |
| `@Configuration` | 声明这是 Spring 配置类 |
| `@ComponentScan` | 扫描 `@Service`，把 `AccountServiceImpl` 放入容器 |
| `@PropertySource` | 读取 `jdbc.properties` |
| `@Import` | 引入 JDBC 和 MyBatis 两个配置类 |

`JdbcConfig`、`MybatisConfig` 没写 `@Configuration`，但被 `@Import` 引入后，其中的 `@Bean` 方法仍会被处理。大型项目通常会给配置类也加上 `@Configuration`，含义更清楚。

### 9.2 `JdbcConfig`：创建数据源

```java
@Value("${jdbc.driver}")
private String driver;

@Bean
public DataSource dataSource() {
    DruidDataSource ds = new DruidDataSource();
    ds.setDriverClassName(driver);
    // 设置 url、username、password
    return ds;
}
```

这里完成两件事：

1. `@Value` 从 properties 文件取出连接参数；
2. `@Bean` 把 Druid 数据源注册进 Spring 容器。

返回类型写成接口 `DataSource`，实际对象是 `DruidDataSource`。后面的 MyBatis 只依赖标准 `DataSource` 接口，不需要关心连接池的具体品牌。

### 9.3 `MybatisConfig`：创建工厂

```java
@Bean
public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
    SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
    ssfb.setTypeAliasesPackage("com.spring.domain");
    ssfb.setDataSource(dataSource);
    return ssfb;
}
```

方法参数 `DataSource dataSource` 由 Spring 按类型自动注入，拿到的就是上一节创建的 Druid 数据源。

`SqlSessionFactoryBean` 是 `mybatis-spring` 提供的 `FactoryBean`。它自身是工厂 Bean，最终向容器提供的主要产品是 `SqlSessionFactory`。

两项设置的含义：

- `setDataSource`：告诉 MyBatis 去哪里获取数据库连接；
- `setTypeAliasesPackage`：扫描实体类，为 XML 映射中的短类型名提供支持。

当前案例的 SQL 全写在注解中，方法返回类型也能直接推断，所以类型别名几乎没有显现出来；它主要是在使用 Mapper XML 时更有价值。

### 9.4 `MybatisConfig`：扫描 Mapper

```java
@Bean
public MapperScannerConfigurer mapperScannerConfigurer() {
    MapperScannerConfigurer msc = new MapperScannerConfigurer();
    msc.setBasePackage("com.spring.dao");
    return msc;
}
```

它扫描 `com.spring.dao` 包，找到 `AccountDao` 接口，并为接口注册可注入的代理对象。

注意，它和 `@ComponentScan` 不是一回事：

| 扫描器 | 主要扫描什么 | 本案例的结果 |
| --- | --- | --- |
| `@ComponentScan` | 带 `@Component/@Service` 等注解的类 | 注册 `AccountServiceImpl` |
| `MapperScannerConfigurer` | MyBatis Mapper 接口 | 注册 `AccountDao` 代理 |

如果只保留 `@ComponentScan`，`AccountDao` 没有实现类，也没有组件注解，Spring 无法凭空创建它。

## 10. Service 层在做什么

```java
@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountDao accountDao;

    public Account findById(Integer id) {
        return accountDao.findById(id);
    }
}
```

当前是入门案例，所以 Service 只是转发 Dao 调用，看起来有些“多余”。实际项目中，Service 会放业务规则，例如：

- 转账时先扣款再加款；
- 检查余额是否足够；
- 一次业务调用多个 Dao；
- 用事务保证多条 SQL 要么全部成功，要么全部失败。

分层的价值是让“业务规则”和“数据库访问细节”各自待在合适的位置。

不要在 Controller 或启动类中到处直接调用 Mapper，否则业务一复杂就难以统一管理事务和规则。

## 11. `App2` 的完整执行链路

### 11.1 启动 Spring

```java
ApplicationContext ctx =
    new AnnotationConfigApplicationContext(SpringConfig.class);
```

启动期间大致发生：

```text
读取 SpringConfig
  ├─ 加载 jdbc.properties
  ├─ 创建 Druid DataSource
  ├─ 创建 SqlSessionFactory
  ├─ 扫描 AccountDao，注册 Mapper 代理
  └─ 扫描 @Service，创建 AccountServiceImpl
                         └─ 注入 AccountDao 代理
```

### 11.2 获取 Service

```java
AccountService accountService = ctx.getBean(AccountService.class);
```

Spring 返回的是 `AccountServiceImpl` 对象。变量使用接口类型，便于解耦和替换实现。

### 11.3 调用查询

```java
Account ac = accountService.findById(1);
```

运行时调用链可以串成：

```text
App2
  → AccountServiceImpl.findById(1)
  → AccountDao代理.findById(1)
  → 读取 @Select SQL
  → SqlSessionTemplate/SqlSession
  → DataSource 获取连接
  → MySQL 执行 SQL
  → ResultSet 映射成 Account
  → 原路返回给 App2
```

`SqlSessionTemplate` 是 mybatis-spring 在内部使用的线程安全模板。它负责把 Mapper 调用连接到合适的 `SqlSession`，业务代码不再手工 `openSession()` 和 `close()`。

## 12. 原生配置与 Spring 配置如何一一对应

| 原生 MyBatis：`SqlMapConfig.xml.bak` | Spring 整合后 |
| --- | --- |
| `<properties resource="jdbc.properties">` | `@PropertySource` |
| `<dataSource>` | `JdbcConfig.dataSource()` |
| `<typeAliases>` | `setTypeAliasesPackage(...)` |
| `<mappers>` | `MapperScannerConfigurer` |
| 手工 `build()` | `SqlSessionFactoryBean` |
| 手工 `openSession()` / `close()` | mybatis-spring 内部管理 |
| 手工 `getMapper()` | Mapper 扫描后直接注入 |

最重要的变化是：

```text
原生：程序自己创建和使用 MyBatis 对象
整合：Spring 创建对象，mybatis-spring 把 Mapper 变成可注入 Bean
```

## 13. 这个案例里的事务要准确理解

当前案例虽然引入了 `spring-jdbc`，但没有配置 `DataSourceTransactionManager`，也没有启用 `@Transactional`。

因此：

- 单次 Mapper 调用可以正常查询或更新；
- 不能认为多个 Dao 方法已经自动组成一个 Spring 事务；
- 若一个 Service 方法连续执行两条更新，第二条失败，第一条不一定会按业务期望一起回滚。

真正的 Spring 声明式事务还需要事务管理器和事务注解。仓库后面的 `spring_24_case_transfer` 会专门演示这一点。

可以用一句话区分：

```text
Spring 管理了 Mapper/SqlSession 的使用 ≠ 已经配置了完整的业务事务
```

## 14. 怎样动手验证 5 个 CRUD 方法

建议先保持源代码不变运行 `App2`，确认查询成功。然后可以临时把 `App2` 中查询部分替换成下面的片段逐个练习。

### 14.1 查询全部

```java
List<Account> accounts = accountService.findAll();
accounts.forEach(System.out::println);
```

需要增加：

```java
import java.util.List;
```

### 14.2 新增

```java
Account account = new Account();
account.setName("Lucy");
account.setMoney(3000.0);
accountService.save(account);
```

执行后在 MySQL 中检查：

```sql
SELECT * FROM tbl_account;
```

### 14.3 更新

```java
Account account = new Account();
account.setId(1);
account.setName("Tom-Updated");
account.setMoney(1500.0);
accountService.update(account);
```

`id` 决定更新哪一行，`name` 和 `money` 是新值。如果忘记设置 id，条件会变成 `id = NULL`，通常一行也更新不到。

### 14.4 删除

```java
accountService.delete(2);
```

删除不可逆，练习前确认 id=2 是测试数据。

### 14.5 按 id 查询

```java
Account account = accountService.findById(1);
System.out.println(account);
```

查不到数据时通常返回 `null`，不是返回一个属性全为空的 `Account`。

## 15. 依赖逐个说明

`pom.xml` 中有六个直接依赖：

| 依赖 | 作用 |
| --- | --- |
| `spring-context` | Spring IoC 容器、注解配置和依赖注入 |
| `spring-jdbc` | Spring JDBC/事务基础设施，也是本整合所需依赖 |
| `mybatis` | MyBatis 核心 |
| `mybatis-spring` | MyBatis 与 Spring 的桥梁 |
| `druid` | 数据库连接池 |
| `mysql-connector-java` | MySQL JDBC 驱动 |

要特别区分两个包名：

- `org.apache.ibatis.*` 来自 MyBatis 本体；
- `org.mybatis.spring.*` 来自 MyBatis-Spring 整合包。

课程使用的是 Java 8、Spring 5.2、MyBatis 3.5 和 MySQL 驱动 5.1 的教学组合。学习当前案例时按项目版本运行即可，不要在尚未理解代码前同时升级所有依赖，否则容易把版本迁移问题和学习问题混在一起。

## 16. 常见问题排查

### 16.1 `Communications link failure`

含义：Java 无法连接 MySQL。

依次检查：

1. MySQL 服务是否启动；
2. 主机和端口是否正确；
3. `spring_db` 是否存在；
4. 用户名和密码是否正确；
5. 数据库用户是否有访问权限。

### 16.2 `Unknown database 'spring_db'`

数据库还没有创建。执行第 5 节的建库 SQL。

### 16.3 `Table 'spring_db.tbl_account' doesn't exist`

库存在，但表不存在，或者表名写错。执行建表 SQL。

### 16.4 `NoSuchBeanDefinitionException: AccountDao`

Spring 容器中没有 Mapper 代理。重点检查：

- `MybatisConfig` 是否被 `@Import`；
- `MapperScannerConfigurer` 是否注册；
- `basePackage` 是否准确写成 `com.spring.dao`。

### 16.5 `BindingException: Invalid bound statement`

MyBatis 找到了接口方法，却没找到对应 SQL。注解方式下检查方法上是否有正确的 `@Select/@Insert/@Update/@Delete`。XML 方式下还要检查 namespace 和方法 id。

### 16.6 查询结果属性全是 `null`

SQL 列名与 Java 属性名无法对应。给查询列添加与属性同名的别名，或学习 MyBatis 的 `@Results/resultMap` 显式映射。

### 16.7 查询返回 `null`

这不一定是错误，可能只是数据库里没有该 id。先直接执行 SQL 检查数据：

```sql
SELECT * FROM tbl_account WHERE id = 1;
```

### 16.8 MySQL 驱动警告

项目使用 5.1 驱动，所以配置为：

```properties
jdbc.driver=com.mysql.jdbc.Driver
```

如果将来升级到 MySQL Connector/J 8.x，驱动类通常应改为 `com.mysql.cj.jdbc.Driver`，连接 URL 也可能需要增加时区等参数。升级属于另一个任务，不影响理解当前案例。

## 17. 初学者最容易产生的 7 个误解

1. **“Dao 接口一定要自己写实现类。”** 不是，MyBatis 会生成动态代理实现。
2. **“`@Autowired` 创建了 AccountDao。”** 不是，Mapper 扫描器创建代理 Bean，`@Autowired` 只负责把它注入进来。
3. **“MyBatis 会自动猜出任意 SQL。”** 不会，SQL 必须来自注解或 Mapper XML。
4. **“`#{name}` 是普通字符串替换。”** 不是，它主要通过预编译参数安全绑定。
5. **“实体属性可以随便命名。”** 不能随便，结果列要能映射到属性，或者显式配置映射关系。
6. **“整合 Spring 后 MyBatis 不存在了。”** 仍然存在，只是创建会话和 Mapper 的模板代码被托管了。
7. **“能执行两条更新就等于有事务。”** 不等于，完整业务事务还要配置事务管理器和 `@Transactional`。

## 18. 推荐学习顺序

按下面顺序重新阅读代码，比从配置类硬背效果好：

1. 看建表 SQL，知道数据长什么样；
2. 看 `Account`，建立“表的一行 ↔ Java 一个对象”的对应；
3. 看 `AccountDao`，理解参数、SQL 和返回值；
4. 看 `App`，理解原生 MyBatis 六步；
5. 看 `JdbcConfig`，理解连接从哪里来；
6. 看 `MybatisConfig`，理解工厂和 Mapper 扫描；
7. 看 `AccountServiceImpl`，理解代理对象如何注入；
8. 看 `App2`，串起完整执行链路；
9. 动手运行五个 CRUD；
10. 最后阅读 [MapperScannerConfigurer.md](MapperScannerConfigurer.md) 深挖代理注册原理。

## 19. 学完后的自测题

如果能不看答案说清这些问题，就真正理解了本案例：

1. MyBatis 和 JDBC、Druid、MySQL 分别是什么关系？
2. `AccountDao` 为什么没有实现类也能调用？
3. `#{name}` 从哪里获取值？为什么通常比 `${name}` 安全？
4. `findAll()` 为什么返回 `List<Account>`，`findById()` 为什么返回 `Account`？
5. 数据库列如何映射到 `Account` 属性？列名不同时怎么办？
6. `SqlSessionFactory` 和 `SqlSession` 有什么区别？
7. `MapperScannerConfigurer` 做了什么？
8. `@ComponentScan` 为什么不能替代 Mapper 扫描器？
9. Spring 整合前后，哪些代码被省掉了？
10. 当前案例为什么还不能代表完整的 Spring 事务配置？

## 20. 一页总结

```text
数据：tbl_account 中的一行
  ↕ 自动映射
对象：Account

SQL 放在哪里：AccountDao 方法的 @Select/@Insert/@Update/@Delete
参数怎么进去：#{属性名/参数名} → PreparedStatement 的 ?
结果怎么出来：ResultSet 列名 → Account 属性
接口谁实现：MyBatis 动态代理
接口谁发现：MapperScannerConfigurer
连接从哪里来：Druid DataSource
工厂谁创建：SqlSessionFactoryBean
对象谁组装：Spring IoC 容器
业务从哪里调用：AccountServiceImpl
```

最核心的一句话：

> 开发者声明 Mapper 接口、方法和 SQL；MyBatis 在运行时生成代理并完成 JDBC 调用；Spring 再负责创建、管理和注入这些对象。

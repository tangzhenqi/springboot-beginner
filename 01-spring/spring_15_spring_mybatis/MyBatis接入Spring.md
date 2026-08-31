# spring_15_spring_mybatis —— Spring 整合 MyBatis

## 一、案例目标

MyBatis 单独用的时候，每次查询都要自己写一遍"建 Builder → 读配置 → 造 SqlSessionFactory → 开 SqlSession → 拿 Mapper → 关 SqlSession"这套模板代码。

**整合的本质就是：把 MyBatis 的这些核心对象交给 Spring 容器管理，让 Mapper 接口变成可以直接 `@Autowired` 的 Bean。**

本案例保留了整合前后两条路线，可以直接对照：

| 路线 | 启动类 | 配置来源 | 拿 Dao 的方式 |
| --- | --- | --- | --- |
| 整合前：MyBatis 原生 | `App` | `SqlMapConfig.xml.bak` | `sqlSession.getMapper(...)` 手工获取 |
| 整合后：Spring 管理 | `App2` | `SpringConfig` 等配置类 | `@Autowired` 自动注入 |

## 二、工程结构

```
spring_15_spring_mybatis
├── pom.xml                        6 个依赖，见下文
└── src/main
    ├── java
    │   ├── App.java                             整合前：MyBatis 原生写法
    │   ├── App2.java                            整合后：纯注解 Spring 写法
    │   └── com/spring
    │       ├── config/SpringConfig.java         主配置类
    │       ├── config/JdbcConfig.java           数据源配置（Druid）
    │       ├── config/MybatisConfig.java        MyBatis 整合配置 ★核心
    │       ├── dao/AccountDao.java              Mapper 接口，注解写 SQL
    │       ├── domain/Account.java              实体类
    │       ├── service/AccountService.java
    │       └── service/impl/AccountServiceImpl.java
    └── resources
        ├── jdbc.properties                      数据库连接参数
        └── SqlMapConfig.xml.bak                 MyBatis 原生配置（已被配置类取代）
```

> `SqlMapConfig.xml` 被改名成 `.bak`，是为了直观表达"整合后这个文件不再需要了"。但 `App` 里仍按 `.bak` 这个全名加载它，所以两条路线都还能跑。

## 三、依赖说明

`pom.xml` 里 6 个依赖，缺一不可：

| 依赖 | 作用 |
| --- | --- |
| `spring-context` | Spring 核心容器 |
| `spring-jdbc` | 提供 `DataSource` 事务管理支持，**mybatis-spring 依赖它** |
| `mybatis` | MyBatis 本体 |
| `mybatis-spring` | **整合包**，提供 `SqlSessionFactoryBean` 和 `MapperScannerConfigurer` |
| `druid` | 阿里数据库连接池 |
| `mysql-connector-java` | MySQL 驱动 |

关键是 `mybatis-spring`——整合能力全靠它。**注意 `spring-jdbc` 不能漏**，否则会报 `NoClassDefFoundError: org/springframework/jdbc/datasource/DataSourceTransactionManager`。

## 四、整合前：MyBatis 原生写法（`App`）

```java
// 1. 创建SqlSessionFactoryBuilder对象
SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
// 2. 加载SqlMapConfig.xml配置文件
InputStream inputStream = Resources.getResourceAsStream("SqlMapConfig.xml.bak");
// 3. 创建SqlSessionFactory对象
SqlSessionFactory sqlSessionFactory = builder.build(inputStream);
// 4. 获取SqlSession
SqlSession sqlSession = sqlSessionFactory.openSession();
// 5. 获取Mapper代理对象并执行
AccountDao accountDao = sqlSession.getMapper(AccountDao.class);
Account ac = accountDao.findById(2);
// 6. 释放资源
sqlSession.close();
```

痛点很明显：

- **6 步模板代码，每次都要写**；
- 忘了 `close()` 就会**泄漏连接**；
- Dao 对象要手工 `getMapper` 拿，**没法注入到 Service 里**。

对应的 `SqlMapConfig.xml.bak` 里配了四块内容：`properties`（读配置文件）、`typeAliases`（类型别名）、`environments`（数据源+事务）、`mappers`（Mapper 扫描）。**记住这四块，整合后它们会一一被搬到配置类里。**

## 五、整合后：三个配置类

### 1. `SpringConfig` —— 主配置类

```java
@Configuration
@ComponentScan("com.spring")
@PropertySource("classpath:jdbc.properties")
@Import({JdbcConfig.class, MybatisConfig.class})
public class SpringConfig {
}
```

| 注解 | 作用 | 取代了 XML 里的 |
| --- | --- | --- |
| `@Configuration` | 声明配置类 | `applicationContext.xml` 本身 |
| `@ComponentScan` | 扫描 `@Service` 等注解 | `<context:component-scan>` |
| `@PropertySource` | 加载 properties 文件 | `<context:property-placeholder>` / MyBatis 的 `<properties>` |
| `@Import` | 引入其他配置类 | `<import resource="..."/>` |

> `@Import` 引入的 `JdbcConfig` 和 `MybatisConfig` **本身没加 `@Configuration`**。这样也能工作——被 `@Import` 引入的类会被注册为 Bean，里面的 `@Bean` 方法照样生效（Spring 称之为 lite 模式）。不过规范写法还是建议补上 `@Configuration`，否则同一配置类内部方法互相调用时不会走代理，可能造成重复创建对象。

### 2. `JdbcConfig` —— 数据源

```java
public class JdbcConfig {
    @Value("${jdbc.driver}")
    private String driver;
    @Value("${jdbc.url}")
    private String url;
    @Value("${jdbc.username}")
    private String userName;
    @Value("${jdbc.password}")
    private String password;

    @Bean
    public DataSource dataSource(){
        DruidDataSource ds = new DruidDataSource();
        ds.setDriverClassName(driver);
        ds.setUrl(url);
        ds.setUsername(userName);
        ds.setPassword(password);
        return ds;
    }
}
```

- `@Value("${...}")` 读取 `jdbc.properties` 里的值，前提是 `SpringConfig` 上配了 `@PropertySource`。
- `@Bean` 把返回的 `DataSource` 放进容器，**这个 Bean 马上要被 MybatisConfig 用到**。
- 这一块取代了 XML 里 `<environments>` 中的 `<dataSource>`。

### 3. `MybatisConfig` —— 整合的核心

```java
public class MybatisConfig {
    //定义bean，SqlSessionFactoryBean，用于产生SqlSessionFactory对象
    @Bean
    public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource){
        SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
        ssfb.setTypeAliasesPackage("com.spring.domain");
        ssfb.setDataSource(dataSource);
        return ssfb;
    }

    //定义bean，返回MapperScannerConfigurer对象
    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer(){
        MapperScannerConfigurer msc = new MapperScannerConfigurer();
        msc.setBasePackage("com.spring.dao");
        return msc;
    }
}
```

**`SqlSessionFactoryBean`** —— 还记得 `spring_03_bean_instance` 里的**第四种实例化方式 FactoryBean 吗？这里就是它的实战应用**。它实现了 `FactoryBean<SqlSessionFactory>`，容器里最终拿到的是 `SqlSessionFactory`，而不是这个工厂本身。

- `setDataSource(dataSource)`：形参 `DataSource dataSource` 是**方法参数注入**，Spring 自动按类型把 `JdbcConfig` 里那个 Bean 传进来。
- `setTypeAliasesPackage(...)`：取代 XML 的 `<typeAliases>`。
- `SqlSession` 的创建、使用和关闭会由 mybatis-spring 托管。不过本案例还没有声明 `DataSourceTransactionManager`，因此尚未建立跨多个 Dao 调用的 Spring 业务事务；完整事务配置会在后续转账案例中学习。

**`MapperScannerConfigurer`** —— 取代 XML 的 `<mappers>`，也是整合中最关键的一步。

- `setBasePackage("com.spring.dao")`：扫描这个包下的所有接口；
- 为每个接口**生成动态代理对象并注册成 Spring Bean**；
- 于是 `AccountDao` 就变成了容器里的一个 Bean，可以被 `@Autowired` 注入。

> 这两行背后的完整机制（执行时机、`MapperFactoryBean`、动态代理链路、常见坑）见 [MapperScannerConfigurer.md](MapperScannerConfigurer.md)。

### 配置搬迁对照表

| MyBatis XML | 整合后的位置 |
| --- | --- |
| `<properties resource="jdbc.properties"/>` | `@PropertySource` |
| `<typeAliases>` | `ssfb.setTypeAliasesPackage(...)` |
| `<environments>` 的 `<dataSource>` | `JdbcConfig` 的 `dataSource()` |
| `<environments>` 的 `<transactionManager>` | 由 Spring 接管，无需配置 |
| `<mappers>` | `MapperScannerConfigurer.setBasePackage(...)` |

## 六、整合的最终效果

Service 里直接注入 Dao，**看不到任何 MyBatis 的痕迹**：

```java
@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountDao accountDao;      // 这就是 MapperScannerConfigurer 生成的代理对象

    public Account findById(Integer id) {
        return accountDao.findById(id);
    }
}
```

启动类 `App2` 只剩三行：

```java
ApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
AccountService accountService = ctx.getBean(AccountService.class);
Account ac = accountService.findById(1);
```

对比 `App` 的 6 步模板代码，**SqlSession 的创建和关闭全部由 Spring 托管**，业务代码里再也不用管。

Dao 层用注解写 SQL，连 Mapper XML 都省了：

```java
public interface AccountDao {
    @Select("select * from tbl_account where id = #{id} ")
    Account findById(Integer id);
}
```

## 七、辨析：容器里的 bean 分别是怎么来的？

这个案例最容易犯迷糊的地方是——**明明没看到几行"注册 bean"的代码，容器里怎么就什么都有了？** 实际上这里的 bean 有三种截然不同的来源：

| 来源 | 例子 | 注册方式 | 为什么用这种方式 |
| --- | --- | --- | --- |
| 自己写的类 | `AccountServiceImpl` | `@Service` + `@ComponentScan` | 源码在自己手里，直接加注解 |
| 第三方 jar 里的类 | `DataSource`、`SqlSessionFactoryBean`、`MapperScannerConfigurer` | `@Bean` 方法 | **改不了别人的源码**，没法加 `@Component` |
| 自己写的接口（无实现类） | `AccountDao` | `MapperScannerConfigurer` 生成动态代理 | 接口没有实现类，只能由 MyBatis 代理 |

### 1. 第三方 bean 只能用 `@Bean` 导入

本案例一共导入了 **3 个第三方 bean**：

| 第三方 bean | 来自哪个 jar | 定义位置 |
| --- | --- | --- |
| `DataSource`（Druid 实现） | `druid` | `JdbcConfig` 的 `dataSource()` |
| `SqlSessionFactoryBean` | `mybatis-spring` | `MybatisConfig` 的 `sqlSessionFactory()` |
| `MapperScannerConfigurer` | `mybatis-spring` | `MybatisConfig` 的 `mapperScannerConfigurer()` |

它们看起来"不像导入"，是因为 `@Bean` 方法长得就是普通的 new 对象再 return。但这是**唯一可行的写法**——`DruidDataSource` 的源码在别人的 jar 包里，你没法在它的类上加 `@Component`。

```java
// 自己的类 → 加注解 + 组件扫描
@Service
public class AccountServiceImpl implements AccountService { }

// 第三方的类 → 只能写 @Bean 方法
@Bean
public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource){
    SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
    ssfb.setDataSource(dataSource);
    return ssfb;
}
```

完整链路：`SpringConfig` --`@Import`--> `JdbcConfig` / `MybatisConfig` --`@Bean`--> 三个第三方对象进容器。

> 这套 `@Import` + `@Bean` 的写法是上一个案例 `spring_14_annotation_third_bean_manager`（名字里的 third_bean 就是"第三方 bean"）专门讲的内容，那里只导了一个 Druid 数据源，本案例是它的实战延续。差别在于 spring_14 的 `@Value` 是硬编码的（`@Value("root")`），这里改成了 `${jdbc.password}` 配合 `@PropertySource` 读外部文件——后者才是实际项目的写法。

### 2. `AccountDao` 没加任何 Spring 注解，为什么能注入？

`AccountDao` 是**自己写的接口**，但它既没有 `@Repository`，也没有实现类，却能被 `@Autowired` 注入。靠的就是 `MapperScannerConfigurer`：它扫描 `com.spring.dao` 包，为每个接口生成动态代理对象并注册成 bean。

这是 MyBatis 整合特有的机制，也是最容易看不明白"这个 bean 到底哪来的"的一种。删掉 `mapperScannerConfigurer()` 这个 `@Bean`，启动就会报 `NoSuchBeanDefinitionException: AccountDao`。

### 3. `mybatis` 和 `mybatis-spring` 是两回事

案例里的 MyBatis 相关代码分属两个层次，看包名就能区分：

| 包名前缀 | 属于 | 案例中的体现 | 作用 |
| --- | --- | --- | --- |
| `org.apache.ibatis.*` | `mybatis` **本体** | `AccountDao` 上的 `@Select` / `@Insert` / `@Update` / `@Delete` | 写 SQL，**和 Spring 无关** |
| `org.mybatis.spring.*` | `mybatis-spring` **整合包** | `SqlSessionFactoryBean`、`MapperScannerConfigurer` | 让 MyBatis 融入 Spring 容器 |

> `org.apache.ibatis` 这个包名是历史遗留——MyBatis 的前身叫 iBATIS。

关键结论：**`AccountDao` 里那些 SQL 注解，就算完全不用 Spring 也照样要这么写。** `App` 那条原生路线里没有任何 Spring，用的却是同一个 `AccountDao`、同一批注解，它只是换了种方式拿到接口的代理对象而已。

而 `@Bean`、`@Import`、`@Configuration`、`@Value` 这些**全都是 Spring 的注解**，跟 MyBatis 没有半点关系——它们只是"把对象放进容器"的通用机制，导什么对象都是这套写法。

## 八、运行方式

### 前置准备：建库建表

本案例连的是本机 `spring_db` 库，运行前必须先准备好数据：

```sql
create database if not exists spring_db character set utf8;
use spring_db;

create table tbl_account(
    id int primary key auto_increment,
    name varchar(35),
    money double
);

insert into tbl_account(name, money) values('Tom', 1000), ('Jerry', 2000);
```

数据库连接参数在 `src/main/resources/jdbc.properties`，按自己的环境改：

```properties
jdbc.driver=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://localhost:3306/spring_db?useSSL=false
jdbc.username=root
jdbc.password=root
```

### 运行

- 运行 `App2` → 整合后的写法，查 id=1 的记录
- 运行 `App` → 整合前的原生写法，查 id=2 的记录

预期输出形如：

```
Account{id=1, name='Tom', money=1000.0}
```

## 九、常见问题

| 现象 | 原因 |
| --- | --- |
| `NoSuchBeanDefinitionException: AccountDao` | `MapperScannerConfigurer` 的 `basePackage` 配错了，或这个 `@Bean` 漏了 |
| `NoClassDefFoundError: ...DataSourceTransactionManager` | 少了 `spring-jdbc` 依赖 |
| `Communications link failure` | MySQL 没启动，或 url / 端口不对 |
| `Unknown database 'spring_db'` | 库没建，见上面的建表 SQL |
| 查询返回 `null` | 表里没有对应 id 的数据 |
| 驱动类过期警告 | `mysql-connector-java` 5.x 用 `com.mysql.jdbc.Driver`；升到 8.x 要改成 `com.mysql.cj.jdbc.Driver` 并在 url 加时区参数 |

## 十、小结

```
整合三步走：
  ① 导 mybatis-spring + spring-jdbc 依赖
  ② 配 SqlSessionFactoryBean —— 交出 DataSource，替代 SqlMapConfig.xml
  ③ 配 MapperScannerConfigurer —— 把 Mapper 接口变成 Spring Bean

整合的收益：
  没有 SqlSessionFactoryBuilder、没有 SqlSession、没有 close()
  Dao 直接 @Autowired 注入，Service 业务代码无需管理 MyBatis 会话
```

这套整合配置在 SpringBoot 里会被进一步简化成**一个 starter 加几行 yml**——但底层做的事情和本案例完全一样，理解了这里，SpringBoot 的自动配置就不再是黑盒。

下一个案例 `spring_16_spring_junit` 会解决"每次都要手写 `AnnotationConfigApplicationContext` 才能测试"的问题。

# spring_14_annotation_third_bean_manager —— 注解开发管理第三方 Bean

## 一、案例目标

前面三个案例把自己写的类全部注解化了，但还剩一个搞不定的场景：

> `DruidDataSource` 来自 Druid 的 jar 包，**源码不是自己的**，没法在它的类上加 `@Repository` / `@Component`。

这就是本案例要解决的问题——用 **`@Bean`** 把第三方类交给 Spring 管理，并用 **`@Import`** 把配置拆分成多个类。

| XML 写法 | 注解写法 |
| --- | --- |
| `<bean class="com.alibaba.druid.pool.DruidDataSource">` | `@Bean` 方法 |
| `<import resource="jdbc.xml"/>` | `@Import(JdbcConfig.class)` |

### 什么才算"第三方 Bean"

本案例涉及的类可以分成三层：

| 类 | 来源 | 能改它的源码吗 |
| --- | --- | --- |
| `com.spring.dao.impl.BookDaoImpl` | **自己写的** | ✅ 能，所以可以加 `@Repository` |
| `javax.sql.DataSource` | **JDK 自带**（Java SE 的 JDBC 标准接口，属 `java.sql` 模块）| ❌ 不能 |
| `com.alibaba.druid.pool.DruidDataSource` | **第三方**（Druid 的 jar 包，`pom.xml` 里引入的）| ❌ 不能 |

所以本案例里真正的"第三方 Bean"是 **`DruidDataSource`**，而不是 `DataSource`——后者是 JDK 的一部分，严格说不算第三方。

但换个角度看，**第二、三行其实是同一类问题：源码都不是你的，都没法加 `@Component`，都只能靠 `@Bean` 方法交给 Spring 管理**。所以"第三方 Bean"这个说法的实质含义是：

> **非自己编写、无法加注解的类。**

按这个定义，JDK 里的类（`SimpleDateFormat`、`RestTemplate` 等）和真正的第三方 jar 里的类，走的是完全相同的套路。记住这条比纠结"到底算不算第三方"更有用。

> 顺带澄清一个容易混淆的点：`ctx.getBean(DataSource.class)` 中的 `DataSource.class` **不是"某个 Bean 的类型"，而是按类型查找的坐标**——接口无法实例化，容器里自始至终只有 `JdbcConfig.dataSource()` 里 `new` 出来的那**一个** `DruidDataSource` 实例，它同时具备 `DruidDataSource` / `DruidAbstractDataSource` / `DataSource` / `Object` 多重类型身份，用其中任一身份都能取到同一个对象：
>
> ```java
> DataSource a = ctx.getBean(DataSource.class);
> DruidDataSource b = ctx.getBean(DruidDataSource.class);
> System.out.println(a == b);   // true，同一个实例
> ```
>
> 区别只在于**拿到的引用被声明成什么类型**：声明成 `DataSource` 时，编译器只允许调用该接口声明的方法，所以 `a.getUsername()` 编译不过（`getUsername()` 是 Druid 的扩展方法），而 `b.getUsername()` 可以。

## 二、工程结构

```
spring_14_annotation_third_bean_manager
├── pom.xml                       spring-context 5.2.10 + druid 1.1.16
└── src/main/java/com/spring
    ├── App.java                          启动类，获取 DataSource
    ├── config/SpringConfig.java          主配置类，@Import 导入 JdbcConfig
    ├── config/JdbcConfig.java            独立的数据源配置类，@Bean 定义 DataSource
    ├── dao/BookDao.java                  接口
    └── dao/impl/BookDaoImpl.java         @Repository，用于演示 @Bean 方法的参数注入
```

**配置类拆分**是这里的一个工程习惯：数据源配置、事务配置、MVC 配置各自一个类，主配置类只负责把它们组装起来，而不是全部堆在 `SpringConfig` 里。

## 三、核心知识点

### 1. `@Bean` —— 把第三方对象变成 Bean

```java
public class JdbcConfig {
    //1. 定义一个方法，方法体里 new 出要管理的对象并返回
    //2. 方法上加 @Bean，表示该方法的返回值是一个 bean
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

要点：

- **Bean 的 id 默认就是方法名**（这里是 `dataSource`）。想改名写 `@Bean("ds")`。
- 方法返回值类型即 Bean 的类型，所以 `App` 中可以用 `ctx.getBean(DataSource.class)` 按类型取。
- `@Bean` 方法**必须写在配置类里**才生效（被 `@Configuration` 标注，或被 `@Import` 导入）。

### 2. `@Bean` 方法的形参 —— 自动装配

这是本案例最容易被忽略的一个知识点：

```java
@Bean
public DataSource dataSource(BookDao bookDao){    // 形参会被自动注入
    System.out.println(bookDao);
    ...
}
```

**`@Bean` 修饰的方法，其形参由 Spring 按类型自动装配**，不需要写 `@Autowired`。容器发现要造 `dataSource` 时，会先从容器里找一个 `BookDao` 类型的 Bean 传进来。如果找不到对应类型的 Bean，启动会直接失败。

这就是本案例明明在做数据源配置、却还留着一个 `BookDao` / `BookDaoImpl` 的原因——它存在的唯一目的就是演示这个特性。

### 3. 配置数据用 `@Value` 注入

```java
@Value("com.mysql.jdbc.Driver")
private String driver;
@Value("jdbc:mysql://localhost:3306/spring_db")
private String url;
@Value("root")
private String userName;
@Value("root")
private String password;
```

这里写的是**字面值**，纯粹为了演示。实际项目中应该配合上一个案例的 `@PropertySource` 抽到 `jdbc.properties` 里，改成 `@Value("${jdbc.url}")`，避免把密码硬编码在源码中。

### 4. `@Import` —— 导入其他配置类

`JdbcConfig` 有两种被 Spring 识别的方式：

**方式一：加 `@Configuration`，靠 `@ComponentScan` 扫到**（源码中这行被注释掉了）

```java
@Configuration      // 加上后，因为它在 com.spring 包下，会被 @ComponentScan("com.spring") 扫描到
public class JdbcConfig { }
```

**方式二：用 `@Import` 显式导入（本案例采用，也是推荐做法）**

```java
@Configuration
@ComponentScan("com.spring")
@Import({JdbcConfig.class})     // 多个配置类写成数组
public class SpringConfig { }
```

为什么推荐 `@Import`：

- **不依赖扫描路径**，配置类放哪个包都能被加载；
- 主配置类里一眼能看出**加载了哪些配置**，关系是显式的；
- 被导入的类**不需要**加 `@Configuration`（所以源码里那行注释掉也能正常工作）。

> `@Import` 是**不可重复注解**，一个类上只能写一次，多个配置类必须写在同一个数组里：`@Import({JdbcConfig.class, MvcConfig.class})`。

## 四、深入：`@Import` 的工作机制

> 本节是对上面第 4 点的展开，初学时了解前两小节即可，后两小节可以留到读 Spring Boot 源码时再回看。

### 1. 它的"扫描范围"：它根本不扫描

这是它和 `@ComponentScan` 最本质的区别：

| | `@ComponentScan("com.spring")` | `@Import({JdbcConfig.class})` |
| --- | --- | --- |
| 输入 | **包名字符串** | **Class 对象** |
| 行为 | 递归遍历该包及子包下所有 `.class`，逐个读注解元数据判断要不要注册 | 直接把点名的这几个类拿去处理 |
| 范围 | 一个包树 | 精确到类，就这几个，一个不多 |
| 类的位置 | 必须在指定包及其子包下 | **任意包，甚至第三方 jar 里的类** |
| 类上要不要标注 | 必须有 `@Component` 及其衍生注解 | **什么都不用加** |

所以本案例即使把 `JdbcConfig` 挪到 `com.other.xxx` 包下（完全在 `@ComponentScan("com.spring")` 范围之外），照样能正常加载——这就是"不依赖扫描路径"的含义。

也因为参数是 Class 数组，`@Import` **不支持任何形式的通配符或包名**，写 `@Import("com.spring.config.*")` 连编译都过不去。

### 2. 但被导入的类，它自己的注解会继续生效

"不扫描"指的是 `@Import` 这个动作本身，不代表被导入的类是个死物。Spring 会把它当作一个**配置类**来解析，递归处理它身上的注解：

```java
@Import({JdbcConfig.class})        // 只导入了 JdbcConfig 一个类
public class SpringConfig { }
```

```java
public class JdbcConfig {
    @Bean                          // ✅ 被解析，注册 dataSource
    public DataSource dataSource(...) { }
}
```

如果 `JdbcConfig` 上再写 `@ComponentScan("com.other")` 或 `@Import(MvcConfig.class)`，**这些同样会被处理**，形成链式传递。

后面会大量遇到的各种 `@EnableXxx`（`@EnableTransactionManagement`、`@EnableAspectJAutoProxy`）本质上都是包了一层 `@Import` 的注解；Spring Boot 的自动配置也是靠这个机制展开的。

### 3. 它能接收的三类东西

本案例只用了第一种，完整规则是：

| 传入的类型 | 作用 | 典型场景 |
| --- | --- | --- |
| **普通类 / 配置类** | 当作配置类解析，处理它的 `@Bean` 等注解 | 本案例的 `JdbcConfig` |
| **`ImportSelector` 实现类** | 返回一个类名字符串数组，**按条件动态决定**导入哪些配置类 | Spring Boot 的 `AutoConfigurationImportSelector` |
| **`ImportBeanDefinitionRegistrar` 实现类** | 拿到 `BeanDefinitionRegistry` **手动注册** BeanDefinition，最灵活 | MyBatis 的 `@MapperScan` |

后两种是框架作者用的扩展点，日常业务开发基本只会用到第一种。知道有这么回事即可——[`spring_15_spring_mybatis`](../spring_15_spring_mybatis) 里的 `@MapperScan` 走的就是第三条路。

### 4. 被导入的配置类，自己也是一个 Bean

`JdbcConfig` 不只是"被读一遍"就完事——Spring 必须 new 出它的实例，才能调用它的 `@Bean` 方法、才能给它的 `@Value` 字段赋值，所以它本身也会被注册成 Bean。

但**它的 bean 名称和被扫描到时不一样**：

| 进入容器的方式 | bean 名称 |
| --- | --- |
| `@Configuration` + 被 `@ComponentScan` 扫到 | `jdbcConfig`（短类名首字母小写）|
| 被 `@Import` 导入 | `com.spring.config.JdbcConfig`（**全限定类名**）|

本模块用的 Spring 5.2.10 正好在这个行为的分界线上：5.2 起，`ConfigurationClassPostProcessor` 对导入的配置类改用了 `FullyQualifiedAnnotationBeanNameGenerator`，目的是避免不同包下的同名配置类互相覆盖（5.2 之前两种方式都是短类名）。

在 `App` 里加一行即可实测（需 `import java.util.Arrays;`）：

```java
System.out.println(Arrays.toString(ctx.getBeanDefinitionNames()));
```

当前代码下会打出 `com.spring.config.JdbcConfig`；若把 `JdbcConfig` 上的 `@Configuration` 解开注释、同时删掉 `SpringConfig` 上的 `@Import`，改由 `@ComponentScan` 扫描，名称就变成 `jdbcConfig`。

> 这也解释了为什么按名称取配置类 Bean 容易踩坑——按类型取（`ctx.getBean(JdbcConfig.class)`）才是稳妥做法。

## 五、运行方式

运行 `com.spring.App` 的 `main` 方法：

```java
AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
DataSource dataSource = ctx.getBean(DataSource.class);
System.out.println(dataSource);
```

预期输出两行：

```
com.spring.dao.impl.BookDaoImpl@2b71fc7e        ← @Bean 方法形参自动装配成功
{ CreateTime:"...", ActiveCount:0, PoolingCount:0, ... }    ← Druid 数据源对象
```

第一行来自 `dataSource(BookDao bookDao)` 方法里的 `System.out.println(bookDao)`，第二行是 Druid 重写过的 `toString()`。

> **注意**：本案例只是把数据源对象造出来，**并不会真正连接数据库**（Druid 连接池是懒加载的，不调用 `getConnection()` 就不建连接）。所以即使本机没装 MySQL、没有 `spring_db` 库，程序也能正常跑完。pom 里也确实没有引入 MySQL 驱动。

## 六、小结

```
XML  <bean class="第三方类">      →  配置类中的 @Bean 方法
XML  <import resource="xxx.xml"/> →  @Import({XxxConfig.class})
@Bean 方法的形参                  →  按类型自动装配，无需 @Autowired
@Import 的加载方式                →  按 Class 精确导入，不扫描包
```

至此，Spring 注解开发的四块内容全部齐了：

| 案例 | 解决的问题 | 核心注解 |
| --- | --- | --- |
| [11](../spring_11_annotation_bean) | 定义 Bean | `@Component` / `@Configuration` / `@ComponentScan` |
| [12](../spring_12_annotation_bean_manager) | 管理 Bean | `@Scope` / `@PostConstruct` / `@PreDestroy` |
| [13](../spring_13_annotation_di) | 依赖注入 | `@Autowired` / `@Qualifier` / `@Value` / `@PropertySource` |
| **14（本案例）** | **管理第三方 Bean** | **`@Bean` / `@Import`** |

XML 配置文件到此可以彻底退场。下一个案例 [`spring_15_spring_mybatis`](../spring_15_spring_mybatis) 会用这套注解方式整合 MyBatis——那时的 `SqlSessionFactoryBean`、`MapperScannerConfigurer` 全都要靠本案例的 `@Bean` 来配置。

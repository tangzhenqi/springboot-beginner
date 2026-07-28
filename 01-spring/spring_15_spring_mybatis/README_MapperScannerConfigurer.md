# MapperScannerConfigurer 到底干了什么

> 针对 `MybatisConfig.java:20-23` 这两行的剖析：
>
> ```java
> MapperScannerConfigurer msc = new MapperScannerConfigurer();
> msc.setBasePackage("com.spring.dao");
> ```
>
> 版本：`mybatis-spring 1.3.0` + `mybatis 3.5.6` + `spring 5.2.10`

## 一、一句话概括

**扫描 `com.spring.dao` 包下的所有接口，给每个接口生成一个动态代理对象，并注册成 Spring 容器里的 bean。**

结果就是：`AccountDao` 这个**只有接口、没有实现类**的东西，可以像普通 bean 一样被 `@Autowired` 注入。

```java
@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private AccountDao accountDao;   // ← 注进来的就是它生成的代理对象
}
```

## 二、它解决的是什么问题

### 没有它的时候（`App.java` 那条原生路线）

```java
SqlSession sqlSession = sqlSessionFactory.openSession();
AccountDao accountDao = sqlSession.getMapper(AccountDao.class);   // ← 手工获取
Account ac = accountDao.findById(2);
sqlSession.close();
```

`getMapper()` 才是 MyBatis 真正"造出 Dao 对象"的地方。问题在于：

1. **必须先有 `SqlSession`**，而 `SqlSession` 要手工开、手工关；
2. 拿到的对象**只是个局部变量**，不在 Spring 容器里，没法注入到 Service；
3. 每个 Dao、每次使用都要重复这三行。

### 有了它之后

这三件事被一次性解决：Dao 进了容器、`SqlSession` 由 Spring 托管、业务代码里一行 MyBatis 的痕迹都没有。

## 三、执行时机：它比普通 bean 更早

`MapperScannerConfigurer` 实现了 `BeanDefinitionRegistryPostProcessor` 接口。这个接口的作用是——**在容器实例化任何普通 bean 之前，允许你往容器里追加 bean 的定义**。

Spring 容器启动的大致顺序：

```
1. 读配置（SpringConfig / JdbcConfig / MybatisConfig），登记所有 BeanDefinition
2. 执行 BeanDefinitionRegistryPostProcessor    ← MapperScannerConfigurer 在这里干活
       └─ 扫包，把 AccountDao 的 BeanDefinition 追加进去
3. 执行 BeanFactoryPostProcessor
4. 实例化所有单例 bean（AccountServiceImpl 在这里被创建）
5. 依赖注入：@Autowired private AccountDao accountDao  ← 此时 AccountDao 的定义已经存在，注入成功
```

**关键点在第 2 步必须早于第 5 步**。如果 Dao 的注册晚于 Service 的实例化，`@Autowired` 就会找不到 bean。这也解释了为什么它是一个"Configurer"而不是普通的工具类。

## 四、扫描时它做了什么

### 4.1 扫哪些类

内部用的是 `ClassPathMapperScanner`（继承自 Spring 的 `ClassPathBeanDefinitionScanner`），扫描规则和 `@ComponentScan` 是同一套，但判定条件不同：

| | `@ComponentScan` | `MapperScannerConfigurer` |
| --- | --- | --- |
| 扫描目标 | 标了 `@Component` 及其衍生注解的**类** | 包下的**接口**（默认不要求任何注解） |
| 是否递归子包 | 是 | 是 |

所以 `com.spring.dao` 下的 `AccountDao` 接口，**什么注解都不用加**就会被扫到。这正是本案例里 `AccountDao` 干干净净却能进容器的原因。

> 如果想缩小范围，可以用 `msc.setAnnotationClass(XxxMapper.class)` 只扫带特定注解的接口，或 `setMarkerInterface(...)` 只扫某个父接口的子接口。本案例没配，所以是"整包接口全收"。

### 4.2 把接口注册成什么

这是最反直觉的一步。**扫到 `AccountDao` 接口后，注册进容器的 BeanDefinition 里，beanClass 并不是 `AccountDao`，而是被替换成了 `MapperFactoryBean`。**

伪代码大致是这样：

```java
// 扫到接口 AccountDao 后
definition.setBeanClass(MapperFactoryBean.class);                 // 类型换成工厂
definition.getConstructorArgumentValues()
          .addGenericArgumentValue(AccountDao.class);              // 把原接口作为构造参数传进去
definition.setAutowireMode(AUTOWIRE_BY_TYPE);                      // 自动按类型注入 SqlSessionFactory
```

三行分别对应：

1. **换类型**：接口本身不能被实例化，所以真正注册的是一个工厂；
2. **记住原接口**：工厂需要知道自己要造哪个 Mapper 的代理；
3. **开启按类型自动装配**：让容器把 `SqlSessionFactory` 自动喂给这个工厂——**这就是它和 `sqlSessionFactory()` 那个 `@Bean` 之间的隐式联系**。

bean 的 id 默认是**接口名首字母小写**，即 `accountDao`。

### 4.3 `MapperFactoryBean` 又是什么

又是一个 `FactoryBean`（`spring_03_bean_instance` 的第四种实例化方式，在整合里出现了两次）。它的核心方法只有一行：

```java
public T getObject() throws Exception {
    return getSqlSession().getMapper(this.mapperInterface);
}
```

**兜了一大圈，最终调用的还是原生路线里那个 `getMapper()`。** 区别只是：`SqlSession` 不再由你手工 `openSession()`，而是 Spring 通过 `SqlSessionTemplate` 托管的——它是线程安全的，会自动参与 Spring 事务、自动关闭。

所以容器里 `accountDao` 这个 bean，`getBean("accountDao")` 拿到的是 `getObject()` 的返回值（Mapper 代理），而不是 `MapperFactoryBean` 本身。

## 五、代理对象是怎么执行 SQL 的

`getMapper()` 返回的是 **JDK 动态代理**对象，`InvocationHandler` 是 MyBatis 的 `MapperProxy`。调用链：

```
accountDao.findById(1)
   ↓ 走进代理
MapperProxy.invoke(proxy, method, args)
   ↓ 把 Method 包装成 MapperMethod
MapperMethod.execute(sqlSession, args)
   ↓ 读取该方法上的 @Select 注解拿到 SQL、判断返回值类型
sqlSession.selectOne("com.spring.dao.AccountDao.findById", 1)
   ↓
JDBC 执行 → ResultSet 映射成 Account 对象
```

几个要点：

- **接口方法名 + 全限定接口名**组成 SQL 的唯一标识（`statement id`），这就是为什么 Mapper 接口的方法不能随便重载；
- SQL 从哪来？本案例是从方法上的 `@Select`/`@Insert` 等注解读的；如果用 XML 写 SQL，则是从同名 `.xml` 文件里读；
- **代理对象不持有具体实现逻辑**，所有方法调用都被统一拦截后翻译成 SQL 执行——这就是"接口没有实现类也能工作"的根本原因。

## 六、完整链路串起来

```
容器启动
  │
  ├─ JdbcConfig.dataSource()            → 造出 Druid 数据源
  │
  ├─ MybatisConfig.sqlSessionFactory(DataSource)
  │      SqlSessionFactoryBean（FactoryBean 之一）
  │      → getObject() 产出 SqlSessionFactory
  │
  └─ MybatisConfig.mapperScannerConfigurer()
         MapperScannerConfigurer（BeanDefinitionRegistryPostProcessor）
         → 扫描 com.spring.dao
         → 发现接口 AccountDao
         → 注册 BeanDefinition：beanClass = MapperFactoryBean
                                构造参数 = AccountDao.class
                                自动装配 SqlSessionFactory ←──┘
                │
                └─ MapperFactoryBean（FactoryBean 之二）
                     → getObject() = sqlSession.getMapper(AccountDao.class)
                     → JDK 动态代理对象（MapperProxy）
                           │
                           └─ 注入到 AccountServiceImpl 的 @Autowired 字段
```

**两个 `@Bean` 之间没有任何显式的调用关系**，靠的是"按类型自动装配"把 `SqlSessionFactory` 送进了 `MapperFactoryBean`。这也是初看 `MybatisConfig` 时最费解的地方——两个方法看起来毫不相干，实际上后者严重依赖前者。

删掉 `sqlSessionFactory()` 这个 `@Bean`，启动会报：

```
No qualifying bean of type 'org.apache.ibatis.session.SqlSessionFactory' available
```

## 七、两个常见坑

### 1. `basePackage` 配错，或配得太宽

```java
msc.setBasePackage("com.spring.dao");   // ✓
msc.setBasePackage("com.spring");       // ✗ 危险
```

配成 `com.spring` 会把 `AccountService`、`BookService` 这些**业务接口也当成 Mapper** 去生成代理。结果是 `@Autowired AccountService` 注入到的是一个 MyBatis 代理对象，调用方法时会抛"找不到对应 statement"之类的异常，而且报错信息很难指向真正的原因。

**Mapper 接口一定要单独放在一个包里。**

配错包名（比如写成不存在的 `com.spring.mapper`）则是另一种表现：容器能启动，但注入时报 `NoSuchBeanDefinitionException: AccountDao`。

### 2. `@Bean` 方法建议写成 `static`

因为 `MapperScannerConfigurer` 执行得非常早（第三节的第 2 步），容器为了调用 `mapperScannerConfigurer()` 就必须**提前实例化 `MybatisConfig` 这个配置类**。如果配置类里有 `@Value` 字段，此时属性解析器可能还没就绪，会拿到未替换的 `${...}` 原始字符串。

规范写法是：

```java
@Bean
public static MapperScannerConfigurer mapperScannerConfigurer(){ ... }
```

**本案例不会踩到这个坑**——`MybatisConfig` 里没有任何 `@Value` 字段，数据库参数全在 `JdbcConfig` 里。但换个项目就说不准了，知道有这回事即可。

## 八、更简洁的等价写法

`MapperScannerConfigurer` 是编程式配置，实际项目里更常见的是注解形式，直接标在配置类上：

```java
@Configuration
@MapperScan("com.spring.dao")
public class MybatisConfig {
    // mapperScannerConfigurer() 这个 @Bean 可以删掉了
}
```

`@MapperScan` 内部通过 `@Import(MapperScannerRegistrar.class)` 注册的还是同一套扫描逻辑，**两者完全等价**。SpringBoot 项目里通常连 `@MapperScan` 都不用写——`mybatis-spring-boot-starter` 会自动扫描标了 `@Mapper` 的接口。

本案例用的是最原始的写法，好处是**每一步都摊开在眼前**，便于理解 SpringBoot 帮你做了什么。

## 九、小结

这两行代码的完整含义：

```java
MapperScannerConfigurer msc = new MapperScannerConfigurer();
msc.setBasePackage("com.spring.dao");
```

> 在容器实例化普通 bean 之前，扫描 `com.spring.dao` 包下的全部接口，
> 为每个接口注册一个 `MapperFactoryBean` 类型的 BeanDefinition，
> 并让它自动装配 `SqlSessionFactory`；
> 容器取用时，`MapperFactoryBean` 调 `getMapper()` 产出 JDK 动态代理对象，
> 代理拦截方法调用，读取 `@Select` 等注解上的 SQL 并执行。

一句话：**它是"接口 → 可注入的 Bean"这条转换链路的起点。**

# Mapper 代理生成与执行原理详解

> 对应案例：`spring_15_spring_mybatis`
>
> 对应版本：`mybatis-spring 1.3.0`、`mybatis 3.5.6`、`spring 5.2.10`
>
> 本文只研究一个问题：`AccountDao` 明明只有接口、没有实现类，为什么可以被 Spring 注入，并且调用 `findById(1)` 后能够执行 SQL？

## 1. Mapper 代理解决了什么问题

项目中的 `AccountDao` 是一个接口：

```java
public interface AccountDao {

    @Select("select * from tbl_account where id = #{id}")
    Account findById(Integer id);
}
```

项目中并不存在：

```java
public class AccountDaoImpl implements AccountDao {
    // 没有这个类
}
```

按照普通 Java 规则，接口不能直接创建对象：

```java
AccountDao accountDao = new AccountDao(); // 编译错误
```

但是在 Service 中却可以直接注入：

```java
@Autowired
private AccountDao accountDao;
```

还可以正常调用：

```java
Account account = accountDao.findById(1);
```

原因是 MyBatis 在程序运行期间创建了一个实现 `AccountDao` 接口的代理对象。

这个代理对象可以暂时想象成下面的“虚拟实现类”：

```java
// 只是帮助理解，项目中不存在这份源码
public class AccountDaoProxy implements AccountDao {

    @Override
    public Account findById(Integer id) {
        // 1. 找到 findById 对应的 SQL
        // 2. 获取数据库连接
        // 3. 创建 PreparedStatement
        // 4. 绑定 id 参数
        // 5. 执行查询
        // 6. 把 ResultSet 转换成 Account
        // 7. 返回 Account
    }
}
```

MyBatis 不会真的在项目中生成 `AccountDaoProxy.java`。它使用 JDK 动态代理，在内存中生成代理类和代理对象。

“生成 Mapper 代理”的本质可以概括为：

```text
接口 AccountDao
      ↓ MyBatis 动态代理
实现了 AccountDao 的运行时对象
      ↓ 拦截方法调用
把 findById(1) 转换成对应的 SQL 操作
```

整个过程分为两个阶段：

```text
阶段一：Spring 容器启动
扫描 Mapper → 注册 MapperFactoryBean → 创建 Mapper 代理 → 注入 Service

阶段二：业务方法调用
代理拦截方法 → 定位 SQL → 绑定参数 → 执行 SQL → 映射结果
```

## 2. Spring 启动时如何注册 Mapper

**第 1 步：注册 Mapper 扫描器**

项目在 `MybatisConfig` 中配置：

```java
@Bean
public MapperScannerConfigurer mapperScannerConfigurer() {
    MapperScannerConfigurer msc = new MapperScannerConfigurer();
    msc.setBasePackage("com.spring.dao");
    return msc;
}
```

`setBasePackage("com.spring.dao")` 表示：

```text
从 com.spring.dao 包开始扫描
寻找可以作为 MyBatis Mapper 的接口
```

因此扫描器会发现：

```text
com.spring.dao.AccountDao
```

`MapperScannerConfigurer` 实现了 Spring 的：

```java
BeanDefinitionRegistryPostProcessor
```

它会在普通 Bean 实例化之前执行，并有机会向 Spring 容器追加新的 Bean 定义。

大致启动顺序是：

```text
读取 Spring 配置
      ↓
执行 BeanDefinitionRegistryPostProcessor
      ↓
MapperScannerConfigurer 扫描 Mapper
      ↓
注册 AccountDao 对应的 BeanDefinition
      ↓
创建普通 Bean
      ↓
创建 AccountServiceImpl 并注入 AccountDao
```

扫描必须发生在 Service 注入之前。否则 Spring 创建 `AccountServiceImpl` 时，就找不到 `AccountDao` 类型的 Bean。

**第 2 步：判断扫描结果是不是 Mapper 候选对象**

当前版本的扫描器主要寻找：

- 位于指定包中的类型；
- 类型必须是接口；
- 接口必须是独立的接口；
- Bean 名称不能和已有 Bean 冲突。

所以 `AccountDao` 即使没有 `@Repository`，也能被扫描到。

要注意两种扫描的区别：

| 扫描方式 | 扫描目标 | 本案例扫描结果 |
| --- | --- | --- |
| `@ComponentScan` | 带 `@Component`、`@Service` 等注解的类 | `AccountServiceImpl` |
| `MapperScannerConfigurer` | 指定包中的 Mapper 接口 | `AccountDao` |

`@ComponentScan` 不能代替 Mapper 扫描器，因为一个没有实现类的接口不能像普通组件一样被实例化。

**第 3 步：把接口的 Bean 定义改造成 `MapperFactoryBean`**

扫描器发现 `AccountDao` 后，不能直接向 Spring 注册：

```text
beanClass = AccountDao.class
```

因为 `AccountDao` 是接口，无法调用构造方法创建对象。

当前版本的 `ClassPathMapperScanner` 会修改 Bean 定义，大致变成：

```text
Bean 名称：accountDao
Bean 类型：MapperFactoryBean
构造参数：com.spring.dao.AccountDao
自动装配：按类型注入 SqlSessionFactory
```

可以把它理解成注册了：

```java
new MapperFactoryBean<AccountDao>(AccountDao.class);
```

这里非常关键：

```text
扫描阶段不是把 AccountDao 接口直接实例化
而是给 AccountDao 注册一个专门生产代理对象的工厂
```

**第 4 步：给 `MapperFactoryBean` 提供 `SqlSessionFactory`**

同一个配置类还定义了：

```java
@Bean
public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
    SqlSessionFactoryBean ssfb = new SqlSessionFactoryBean();
    ssfb.setTypeAliasesPackage("com.spring.domain");
    ssfb.setDataSource(dataSource);
    return ssfb;
}
```

这段配置在 `SqlSessionFactoryBean` 内部如何组装成最终工厂，可阅读：[SqlSessionFactoryBean核心源码详解.md](SqlSessionFactoryBean核心源码详解.md)。

`SqlSessionFactoryBean` 最终向 Spring 容器提供 `SqlSessionFactory`。

因为本案例没有给扫描器指定某个特定工厂，`MapperFactoryBean` 会按类型自动获得容器中的 `SqlSessionFactory`。

依赖关系是：

```text
DataSource
    ↓
SqlSessionFactoryBean
    ↓ 生产
SqlSessionFactory
    ↓ 注入
MapperFactoryBean<AccountDao>
```

如果项目存在多个 `SqlSessionFactory`，仅靠按类型注入会产生歧义。这时需要明确指定 Mapper 使用哪个工厂。

## 3. `MapperFactoryBean` 如何生成代理

`MapperFactoryBean<T>` 实现了 Spring 的：

```java
FactoryBean<T>
```

普通 Bean 和 `FactoryBean` 的区别是：

```text
普通 Bean：
Spring 创建什么对象，getBean() 就返回什么对象

FactoryBean：
Spring 创建工厂，getBean() 返回工厂 getObject() 生产的产品
```

`MapperFactoryBean` 的核心方法非常简单：

```java
@Override
public T getObject() throws Exception {
    return getSqlSession().getMapper(this.mapperInterface);
}
```

对于本案例，等价于：

```java
return getSqlSession().getMapper(AccountDao.class);
```

这与原生 MyBatis 中手工获取 Mapper 的写法相同：

```java
AccountDao accountDao = sqlSession.getMapper(AccountDao.class);
```

区别只是调用者不同：

```text
原生 MyBatis：开发者手工调用 getMapper()

Spring 整合后：MapperFactoryBean 自动调用 getMapper()
```

**先把 Mapper 接口加入 MyBatis 配置**

在调用代理前，`MapperFactoryBean` 会检查 `AccountDao` 是否已经加入 MyBatis 的 `Configuration`：

```text
Configuration 中没有 AccountDao
       ↓
configuration.addMapper(AccountDao.class)
       ↓
解析接口上的 @Select、@Insert、@Update、@Delete
       ↓
注册每个方法对应的 MappedStatement
```

以 `findById` 为例，MyBatis 会形成一份类似下面的 SQL 操作说明：

```text
statement id：com.spring.dao.AccountDao.findById
SQL 类型：SELECT
SQL：select * from tbl_account where id = #{id}
参数：Integer
返回值：Account
```

`MappedStatement` 可以理解为 MyBatis 对一条 SQL 映射的完整描述。它不仅保存 SQL，还包含 SQL 类型、参数映射、结果映射等信息。

**`getMapper()` 的内部链路**

调用：

```java
getSqlSession().getMapper(AccountDao.class);
```

内部大致经过：

```text
SqlSession.getMapper(AccountDao.class)
        ↓
Configuration.getMapper(AccountDao.class, sqlSession)
        ↓
MapperRegistry.getMapper(AccountDao.class, sqlSession)
        ↓
找到 MapperProxyFactory<AccountDao>
        ↓
MapperProxyFactory.newInstance(sqlSession)
        ↓
创建 MapperProxy
        ↓
Proxy.newProxyInstance(...)
        ↓
返回 AccountDao 代理对象
```

JDK 动态代理的核心调用可以简化为：

```java
Proxy.newProxyInstance(
    AccountDao.class.getClassLoader(),
    new Class<?>[]{AccountDao.class},
    mapperProxy
);
```

三个参数的含义：

| 参数 | 作用 |
| --- | --- |
| 类加载器 | 用来加载运行时生成的代理类 |
| `AccountDao` 接口数组 | 规定代理对象必须实现哪些接口 |
| `mapperProxy` | 规定接口方法被调用后交给谁处理 |

生成的对象满足：

```java
proxy instanceof AccountDao // true
```

但它不是我们编写的 `AccountDaoImpl`，因为项目中没有这个类。

代理类的名称可能类似：

```text
com.sun.proxy.$Proxy21
```

在较新的 Java 中也可能类似：

```text
jdk.proxy2.$Proxy21
```

名称和数字不固定，不应该在业务代码中依赖代理类名。

**为什么 Spring 注入时看到的是 `AccountDao`**

`MapperFactoryBean` 的 `getObjectType()` 返回：

```java
return this.mapperInterface;
```

本案例返回的是：

```java
AccountDao.class
```

因此 Spring 知道这个工厂生产的是 `AccountDao` 类型的对象，可以完成：

```java
@Autowired
private AccountDao accountDao;
```

`MapperFactoryBean.isSingleton()` 返回 `true`，Spring 会把生产出的 Mapper 代理作为单例产品管理和复用。

## 4. `findById(1)` 如何转换成 SQL

代理对象创建并注入 Service 后，业务代码开始调用：

```java
accountDao.findById(1);
```

真正执行的不是普通实现类方法，而是下面这条链路：

```text
AccountDao.findById(1)
        ↓
MapperProxy.invoke(...)
        ↓
MapperMethod.execute(...)
        ↓
SqlSession.selectOne(...)
        ↓
Executor 执行查询
        ↓
PreparedStatement 参数绑定
        ↓
ResultSet 映射为 Account
```

**第 1 步：`MapperProxy` 拦截调用**

创建代理时传入了：

```java
MapperProxy
```

它实现 JDK 的：

```java
InvocationHandler
```

所有普通 Mapper 方法调用都会进入它的 `invoke()` 方法。

下面的代码：

```java
accountDao.findById(1);
```

可以理解为被转换成：

```java
mapperProxy.invoke(
    accountDao代理对象,
    AccountDao.class.getMethod("findById", Integer.class),
    new Object[]{1}
);
```

于是 `MapperProxy` 获得：

```text
代理对象：accountDao
调用方法：findById
调用参数：[1]
```

`toString()`、`equals()`、`hashCode()` 这类来自 `Object` 的方法会单独处理，不会被错误地当成 SQL 方法。

**第 2 步：把 Java 方法包装成 `MapperMethod`**

MyBatis 会为普通 Mapper 方法创建 `MapperMethod`，并缓存起来，避免每次调用都重新分析完整的方法信息。

`MapperMethod` 主要分析两件事：

```text
SqlCommand：
方法对应哪条 MappedStatement？
SQL 类型是 SELECT、INSERT、UPDATE 还是 DELETE？

MethodSignature：
方法参数怎样转换？
返回一个对象、集合、Map，还是 void？
```

对于：

```java
Account findById(Integer id);
```

分析结果大致是：

```text
statement id：com.spring.dao.AccountDao.findById
SQL 类型：SELECT
参数类型：Integer
返回类型：Account
返回集合：否
```

**第 3 步：根据 SQL 类型和返回值选择执行方法**

因为 `findById` 对应 `SELECT`，返回值又是单个 `Account`，所以最终选择类似：

```java
sqlSession.selectOne(
    "com.spring.dao.AccountDao.findById",
    1
);
```

不同返回值会选择不同处理方式：

| Mapper 方法 | MyBatis 处理方式 |
| --- | --- |
| `Account findById(Integer id)` | 查询一条，返回一个对象或 `null` |
| `List<Account> findAll()` | 查询多条，返回集合 |
| `void save(Account account)` | 执行插入，不向调用者返回行数 |
| `int update(Account account)` | 执行更新，返回受影响行数 |

**第 4 步：把 `#{id}` 转换成 JDBC 参数**

Mapper SQL 是：

```sql
select * from tbl_account where id = #{id}
```

MyBatis 会把它处理为 JDBC 预编译形式：

```sql
select * from tbl_account where id = ?
```

然后根据 `findById(1)` 的方法参数完成类似操作：

```java
preparedStatement.setInt(1, 1);
```

完整对应关系：

```text
findById(1)
    ↓
方法参数 id = 1
    ↓
SQL 中的 #{id}
    ↓
PreparedStatement 中第一个 ?
    ↓
setInt(1, 1)
```

所以 `#{id}` 不是简单字符串替换，它的底层仍然使用 `PreparedStatement` 参数绑定。

**第 5 步：把 `ResultSet` 映射成 `Account`**

数据库查询结果可能是：

```text
id=1, name=Tom, money=1000.0
```

MyBatis 根据方法返回类型知道需要生成：

```java
Account
```

然后按照列名和 Java 属性名进行映射：

```text
id    → account.setId(...)
name  → account.setName(...)
money → account.setMoney(...)
```

它完成的事情类似手写 JDBC：

```java
Account account = new Account();
account.setId(resultSet.getInt("id"));
account.setName(resultSet.getString("name"));
account.setMoney(resultSet.getDouble("money"));
```

查询到一行时返回 `Account`；没有查询到时返回 `null`。如果按“查询一个对象”的方式查出了多行，MyBatis 会抛出 `TooManyResultsException`。

**第 6 步：结果沿调用链返回**

```text
MySQL 查询结果
      ↓
ResultSet
      ↓
Account
      ↓
MapperProxy 返回
      ↓
AccountServiceImpl.findById 返回
      ↓
App2 输出
```

## 5. 四个核心对象与两类代理

理解 Mapper 代理时，最容易把几个名字相近的对象混在一起。

| 对象 | 所属框架 | 主要职责 |
| --- | --- | --- |
| `MapperScannerConfigurer` | mybatis-spring | 扫描 Mapper 接口，注册 Bean 定义 |
| `MapperFactoryBean` | mybatis-spring | 调用 `getMapper()`，生产 Mapper 代理 |
| `MapperProxyFactory` | MyBatis | 使用 JDK 动态代理创建 Mapper 对象 |
| `MapperProxy` | MyBatis | 拦截 Mapper 方法并交给 `MapperMethod` |
| `MapperMethod` | MyBatis | 分析方法签名，决定执行哪种 SQL 操作 |
| `SqlSessionTemplate` | mybatis-spring | 在 Spring 环境中协调 `SqlSession`、事务和资源 |

可以按照下面的关系记忆：

```text
MapperScannerConfigurer
负责发现 AccountDao
        ↓
MapperFactoryBean
负责请求生产 AccountDao 代理
        ↓
MapperProxyFactory
负责调用 JDK Proxy 创建代理对象
        ↓
MapperProxy
负责处理代理对象的方法调用
        ↓
MapperMethod
负责把具体方法转换成 SQL 操作
```

**Mapper 代理和 Spring AOP 代理不是一回事**

Mapper 代理：

```text
创建者：MyBatis
核心处理器：MapperProxy
代理目标：Mapper 接口
主要目的：把接口方法转换成 SQL 操作
```

Spring AOP 代理：

```text
创建者：Spring AOP
代理目标：普通 Spring Bean
主要目的：事务、日志、权限、性能统计等方法增强
```

当前案例中的 `AccountDao` 是 MyBatis Mapper 代理。

后续给 Service 添加 `@Transactional` 时，`AccountServiceImpl` 外面还可能出现 Spring 事务代理。此时是两种代理各自负责不同工作：

```text
App2
  ↓
AccountServiceImpl 的 Spring 事务代理
  ↓
AccountServiceImpl
  ↓
AccountDao 的 MyBatis Mapper 代理
  ↓
SQL
```

**Mapper 代理和 `SqlSessionTemplate` 代理也不是同一个对象**

在 Spring 整合环境中，Mapper 代理内部使用的 `SqlSession` 通常是 `SqlSessionTemplate`。

两者职责不同：

```text
MapperProxy：
解释“调用了哪个 Mapper 方法，应该执行哪条 SQL”

SqlSessionTemplate：
协调“本次调用使用哪个 SqlSession，怎样参与事务，何时释放资源”
```

因此 Mapper 代理重点处理“方法到 SQL 的转换”，`SqlSessionTemplate` 重点处理“会话和资源”。

## 6. 验证方法与完整总结

可以临时在 `App2` 中添加下面的代码，亲自观察 Mapper 代理：

```java
import com.spring.dao.AccountDao;

import java.lang.reflect.Proxy;

AccountDao accountDao = ctx.getBean(AccountDao.class);

System.out.println(accountDao.getClass());
System.out.println(Proxy.isProxyClass(accountDao.getClass()));
System.out.println(Proxy.getInvocationHandler(accountDao));
```

预期输出类似：

```text
class com.sun.proxy.$Proxy21
true
org.apache.ibatis.binding.MapperProxy@xxxx
```

在较新 Java 中，第一行也可能是：

```text
class jdk.proxy2.$Proxy21
```

这些结果分别证明：

1. Spring 容器中确实存在 `AccountDao` 类型的对象；
2. 对象的运行时类型不是手写实现类；
3. 它是 JDK 动态代理对象；
4. 代理内部的调用处理器是 MyBatis 的 `MapperProxy`。

也可以验证它实现了 `AccountDao`：

```java
System.out.println(accountDao instanceof AccountDao);
```

输出：

```text
true
```

**完整生成链路**

```text
Spring 容器启动
  │
  ├─ 创建 DataSource
  │
  ├─ SqlSessionFactoryBean
  │      └─ 生产 SqlSessionFactory
  │
  └─ MapperScannerConfigurer
         └─ 扫描 com.spring.dao
              └─ 发现 AccountDao 接口
                   └─ 注册 MapperFactoryBean<AccountDao>
                        ├─ 注入 SqlSessionFactory
                        ├─ 把 AccountDao 加入 MyBatis Configuration
                        ├─ 解析 @Select 等 SQL 注解
                        └─ getObject()
                             └─ SqlSession.getMapper(AccountDao.class)
                                  └─ MapperProxyFactory
                                       └─ Proxy.newProxyInstance(...)
                                            └─ AccountDao 代理对象
                                                 └─ 注入 AccountServiceImpl
```

**完整执行链路**

```text
accountDao.findById(1)
  ↓
MapperProxy.invoke(...)
  ↓
创建或读取缓存中的 MapperMethod
  ↓
定位 com.spring.dao.AccountDao.findById
  ↓
确认操作类型为 SELECT、返回类型为 Account
  ↓
SqlSession.selectOne(statementId, 1)
  ↓
#{id} 转换为 ?，绑定参数 1
  ↓
PreparedStatement 执行 SQL
  ↓
ResultSet 映射成 Account
  ↓
返回 Account{id=1, name='Tom', money=1000.0}
```

最后用六句话记忆：

```text
MapperScannerConfigurer 负责发现接口。
MapperFactoryBean 负责请求创建代理。
MapperProxyFactory 负责生成 JDK 代理对象。
MapperProxy 负责拦截接口方法调用。
MapperMethod 负责决定执行哪条 SQL、怎样处理参数和返回值。
MyBatis 最终通过 JDBC 执行 SQL，并把结果映射成 Java 对象。
```

所以，“生成 Mapper 代理”不是给接口创建一个空壳对象，而是为 Mapper 接口创建一个统一的 SQL 执行入口。

# spring_24_case_transfer —— AOP 事务管理：银行转账

## 一、案例目标

**需求：银行账户之间转账，转出和转入必须同成功、同失败。**

前面 `spring_18` ~ `spring_23` 都是我们**自己写切面**玩 AOP。本案例换个角度：**Spring 事务本身就是 AOP 的一个落地产物**——`@Transactional` 背后就是一个环绕通知，在方法前开启事务、正常结束提交、抛异常回滚。这次我们不写切面，只用注解。

### 为什么事务要放在业务层？

- 转账业务有**两次数据层调用**：一次减钱（`outMoney`）、一次加钱（`inMoney`）；
- 如果把事务放在数据层，减钱和加钱就是**两个独立事务**，各自提交；
- 一旦在两次调用中间出异常，减钱已提交、加钱没执行 —— **钱凭空消失**；
- 所以必须把事务**上提到业务层**，让两次数据层操作处在同一个事务里。

> 事务作用：在数据层保障一系列数据库操作同成功同失败。
> **Spring 事务作用：在数据层或业务层保障一系列数据库操作同成功同失败。**

## 二、工程结构

```
spring_24_case_transfer
├── pom.xml                                     spring-context/jdbc/test + druid + mybatis(-spring) + mysql + junit
└── src
    ├── main/java/com/spring
    │   ├── config/SpringConfig.java            ★ @EnableTransactionManagement
    │   ├── config/JdbcConfig.java              ★ DataSource + PlatformTransactionManager
    │   ├── config/MybatisConfig.java           SqlSessionFactoryBean + MapperScannerConfigurer
    │   ├── domain/Account.java                 id / name / money
    │   ├── dao/AccountDao.java                 @Update 注解版 SQL：inMoney / outMoney
    │   ├── service/AccountService.java         ★ @Transactional 写在接口方法上
    │   └── service/impl/AccountServiceImpl.java  ★ 转账中间埋了 1/0
    ├── main/resources/jdbc.properties          数据库连接参数
    └── test/java/.../AccountServiceTest.java   Spring 整合 JUnit
```

Spring 整合 MyBatis 的部分（`JdbcConfig` / `MybatisConfig` / `MapperScannerConfigurer`）在 `spring_15_spring_mybatis` 里已经讲过，本案例只关注**新增的事务部分**。

### 数据库准备

```sql
CREATE TABLE `tbl_account` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(35) DEFAULT NULL,
  `money` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO tbl_account (name, money) VALUES ('Tom', 1000), ('Jerry', 2000);
```

> **必须是 InnoDB**。MyISAM 不支持事务，配置写得再对也不会回滚。

## 三、事务落地三步走

### 步骤 1：配置事务管理器（JdbcConfig）

```java
//配置事务管理器，mybatis使用的是jdbc事务
@Bean
public PlatformTransactionManager transactionManager(DataSource dataSource){
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
    transactionManager.setDataSource(dataSource);
    return transactionManager;
}
```

- `PlatformTransactionManager` 是 Spring 提供的**平台事务管理器接口**，只有两个核心动作：`commit()` 提交、`rollback()` 回滚；
- `DataSourceTransactionManager` 是它的实现类，**内部采用 JDBC 事务**。给它一个 `DataSource` 它就能管事务；
- **事务管理器要根据持久层技术选择**。MyBatis 内部用的就是 JDBC 事务，所以整合 MyBatis 时选 `DataSourceTransactionManager`。

**一个关键前提**：`DataSourceTransactionManager` 和 `SqlSessionFactoryBean` 必须使用**同一个数据源**。本案例两个 `@Bean` 方法都声明了 `DataSource dataSource` 形参，Spring 按类型注入的是同一个 `dataSource` bean，所以天然满足。数据源不一致的话，事务管理器管的连接和 MyBatis 实际执行 SQL 的连接不是同一条，回滚就会失效。

### 步骤 2：开启注解式事务驱动（SpringConfig）

```java
@Configuration
@ComponentScan("com.spring")
@PropertySource("classpath:jdbc.properties")
@Import({JdbcConfig.class, MybatisConfig.class})
//开启注解式事务驱动
@EnableTransactionManagement
public class SpringConfig {
}
```

| 名称 | `@EnableTransactionManagement` |
| --- | --- |
| 类型 | 配置类注解 |
| 位置 | 配置类定义上方 |
| 作用 | 设置当前 Spring 环境中开启注解式事务支持 |

**没有这个注解，`@Transactional` 就是一句废话，不会报错，只是静默失效。**

> 顺带一提：`JdbcConfig` 和 `MybatisConfig` 类上都**没有** `@Configuration`，它们是通过 `@Import` 导入的。这种情况下 Spring 按 "lite 模式" 处理其中的 `@Bean` 方法。本案例中 bean 之间靠**方法形参**注入依赖（而不是互相调用 `@Bean` 方法），所以 lite 模式完全够用，能正常工作。

### 步骤 3：给业务方法加事务（AccountService）

```java
public interface AccountService {
    //配置当前接口方法具有事务
    @Transactional
    public void transfer(String out, String in, Double money);
}
```

| 名称 | `@Transactional` |
| --- | --- |
| 类型 | 接口注解、类注解、方法注解 |
| 位置 | 业务层接口上方、业务层实现类上方、业务方法上方 |
| 作用 | 为当前业务层方法添加事务（写在类或接口上则其中所有方法均添加事务） |

本案例写在**接口方法**上。四个位置都合法，但实际项目里更推荐写在**实现类的方法**上——写在接口上时，只有走接口代理才生效，容易在换代理方式时踩坑。

## 四、制造异常：转账中断

```java
@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private AccountDao accountDao;

    public void transfer(String out, String in, Double money) {
        accountDao.outMoney(out, money);   // 转出方减钱
        int i = 1/0;                       // ★ 制造异常，模拟转账中途崩溃
        accountDao.inMoney(in, money);     // 转入方加钱（走不到这）
    }
}
```

`1/0` 抛出 `ArithmeticException`。这是**运行时异常（RuntimeException）**，落在 Spring 事务的**默认回滚范围**内，所以会触发回滚。

> 注意：Spring 默认**只对 RuntimeException 和 Error 回滚**，`IOException` 这类受检异常默认**不回滚**。想改这个行为要用 `@Transactional(rollbackFor = {IOException.class})`。`AccountService` 里那几个 `java.io` 的 import 就是给这个实验预留的。

## 五、运行与验证

用 Spring 整合 JUnit 跑测试：

```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
public class AccountServiceTest {
    @Autowired
    private AccountService accountService;

    @Test
    public void testTransfer() throws IOException {
        accountService.transfer("Tom", "Jerry", 100D);
    }
}
```

```bash
mvn test
```

### 5.1 事务生效（当前代码）

测试结果：

```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[ERROR]   AccountServiceTest.testTransfer:21 » Arithmetic / by zero
```

**测试报错是预期结果，不是配置出问题了。** `1/0` 是我们自己埋的，异常必然抛出。真正要看的是**数据库里的数据**：

```
+----+-------+-------+
| id | name  | money |
+----+-------+-------+
|  1 | Tom   |  1000 |     ← 没少
|  2 | Jerry |  2000 |     ← 没多
+----+-------+-------+
```

`outMoney` 已经执行过了，但事务回滚把它撤销了 —— **钱没有凭空消失**。

### 5.2 事务失效（对照实验）

把 `SpringConfig` 上的 `@EnableTransactionManagement` 注释掉再跑一次：

```
+----+-------+-------+
| id | name  | money |
+----+-------+-------+
|  1 | Tom   |   900 |     ← ★ 少了 100
|  2 | Jerry |  2000 |     ← 没多
+----+-------+-------+
```

**Tom 的 100 块凭空蒸发了。** 这就是没有事务时的真实后果，也是这个案例最值得亲手跑一遍的地方。

> 做完对照实验记得把数据改回去：
> ```sql
> UPDATE tbl_account SET money = 1000 WHERE name = 'Tom';
> UPDATE tbl_account SET money = 2000 WHERE name = 'Jerry';
> ```

## 六、Spring 事务角色

理解两个概念：**事务管理员**和**事务协调员**。

**未开启 Spring 事务时**：

```
AccountDao.outMoney()  →  自己开一个事务 T1，执行完就提交
AccountDao.inMoney()   →  自己开一个事务 T2
AccountService.transfer()  →  没有事务

中间抛异常 → T1 已提交，T2 压根没执行 → 数据错乱
```

**开启 Spring 事务后**：

```
AccountService.transfer()  →  @Transactional 开启事务 T   【事务管理员】
    ├─ AccountDao.outMoney()  →  T1 加入 T                【事务协调员】
    └─ AccountDao.inMoney()   →  T2 加入 T                【事务协调员】

业务层抛异常 → 整个 T 回滚 → 数据准确
```

| 角色 | 含义 | 本案例中 |
| --- | --- | --- |
| **事务管理员** | 发起事务方，指开启事务的业务层方法 | `transfer()` |
| **事务协调员** | 加入事务方，通常指数据层方法，也可以是业务层方法 | `outMoney()` / `inMoney()` |

**注意**：这套机制成立的前提是 `DataSourceTransactionManager` 和 `SqlSessionFactoryBean` 使用**同一个数据源**（见步骤 1）。

## 七、小结

```
事务三件套（缺一不可）：
  ① JdbcConfig     配置 PlatformTransactionManager（MyBatis → DataSourceTransactionManager）
  ② SpringConfig   @EnableTransactionManagement    ← 最容易漏
  ③ AccountService @Transactional 加在业务方法上

回滚规则：默认只对 RuntimeException / Error 回滚
         受检异常要显式声明 @Transactional(rollbackFor = XxxException.class)

角色：事务管理员 = 开启事务的业务方法
     事务协调员 = 加入该事务的数据层方法
```

三个常见的"事务不生效"排查点：

1. 忘了 `@EnableTransactionManagement`（静默失效，不报错）；
2. 表引擎是 MyISAM 而非 InnoDB；
3. 抛的是受检异常，没配 `rollbackFor`。

下一站 `spring_25_case_transfer_log` 在本案例基础上加一条需求：**不管转账成功失败，日志都要记下来** —— 用事务传播行为 `Propagation.REQUIRES_NEW` 解决。

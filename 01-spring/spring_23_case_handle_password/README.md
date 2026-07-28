# spring_23_case_handle_password —— AOP 案例：百度网盘密码数据兼容处理

## 一、需求分析

**需求：对百度网盘分享链接输入密码时，尾部多输入的空格做兼容处理。**

场景还原：

- 从别人发来的内容里复制提取码，经常会**多复制到前后的空格**；
- 直接粘贴到百度的提取码输入框，而百度那边记录的提取码是**没有空格**的；
- 不做处理直接比对，就会提示提取码不一致，明明码是对的却打不开。

思路推演（这几问是本案例的重点）：

| 问题 | 结论 |
| --- | --- |
| 怎么解决？ | 在业务方法执行**之前**，对输入参数做 `trim()` |
| 所有参数都要处理吗？ | 没必要，**只处理字符串类型**即可 |
| 每个业务都写一遍去空格代码吗？ | 不，这是典型的**共性功能**，交给 AOP 统一处理 |
| 五种通知用哪个？ | 必须是**环绕通知 `@Around`** |

最后一问是关键：需求是"**把参数处理后再参与原始方法的调用**"，而五种通知里**只有环绕通知能调用原始方法**（`pjp.proceed(args)`），其余四种只能旁观、改不了入参。

所以要做两件事：

1. 在业务方法执行前对所有字符串输入参数执行 `trim()`；
2. **用处理后的参数**去调用原始方法。

## 二、工程结构

```
spring_23_case_handle_password
├── pom.xml                                       spring-context / aspectjweaver / druid / mybatis ...
└── src/main/java
    ├── App.java                                  openURL("http://pan.baidu.com/haha", "root ")
    └── com/spring
        ├── config/SpringConfig.java              @EnableAspectJAutoProxy
        ├── aop/DataAdvice.java                   ★ 核心：去空格切面
        ├── service/ResourcesService.java         boolean openURL(String url, String password)
        ├── service/impl/ResourcesServiceImpl.java   调 dao
        ├── dao/ResourcesDao.java                 boolean readResources(String url, String password)
        └── dao/impl/ResourcesDaoImpl.java        打印密码长度 + 模拟校验
```

> `pom.xml` 里的 druid、mybatis、mysql 等依赖是从前面案例的模板复制过来的，**本案例并没有连数据库**，`ResourcesDaoImpl` 是纯内存模拟。看的时候不用被这些依赖干扰。

### 业务层：一次"模拟校验"

```java
@Repository
public class ResourcesDaoImpl implements ResourcesDao {
    public boolean readResources(String url, String password) {
        System.out.println(password.length());   // 打长度，用来观察空格有没有被去掉
        return password.equals("root");          // 模拟校验
    }
}
```

`App` 故意传了一个**尾部带空格**的密码：

```java
boolean flag = resourcesService.openURL("http://pan.baidu.com/haha", "root ");
```

不加 AOP 的话，`"root ".equals("root")` 是 `false`——这就是要解决的 bug。

## 三、核心实现：DataAdvice

```java
@Component
@Aspect
public class DataAdvice {
    @Pointcut("execution(boolean com.spring.service.*Service.*(*,*))")
    private void servicePt(){}

    @Around("DataAdvice.servicePt()")
    public Object trimStr(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            //判断参数是不是字符串
            if (args[i].getClass().equals(String.class)) {
                args[i] = args[i].toString().trim();
            }
        }
        Object ret = pjp.proceed(args);
        return ret;
    }
}
```

### 3.1 切入点表达式

```
execution(boolean com.spring.service.*Service.*(*,*))
```

逐段拆开：

| 片段 | 含义 |
| --- | --- |
| `boolean` | 返回值必须是 `boolean` |
| `com.spring.service` | 包名，注意是 **service 包**，不含 `impl` 子包 |
| `*Service` | 类名以 `Service` 结尾——即匹配**接口** `ResourcesService` |
| `.*` | 任意方法名 |
| `(*,*)` | **恰好两个参数**，类型任意 |

这里有个容易绕晕的点：表达式匹配的是接口 `com.spring.service.ResourcesService`，而不是实现类 `com.spring.service.impl.ResourcesServiceImpl`（包不同、类名也不是 `*Service`）。**能匹配上，是因为 Spring 对有接口的 bean 默认用 JDK 动态代理，代理对象实现的正是这个接口**，方法签名来自接口。

> 相关讨论见 `spring_19_aop_pointcut/README_切入点写接口还是实现类.md`。

### 3.2 引用切入点时的 `DataAdvice.` 前缀

```java
@Around("DataAdvice.servicePt()")
```

同一个类内引用切入点，直接写 `servicePt()` 就行，加上类名前缀也合法。**跨类引用时必须写全限定名**，比如 `com.spring.aop.DataAdvice.servicePt()`。

### 3.3 参数处理三步走

1. **`pjp.getArgs()`** 拿到原始参数数组 `["http://pan.baidu.com/haha", "root "]`；
2. **遍历 + 类型判断 + `trim()`**：只对 `String` 类型的参数去空格，其它类型（`int`、对象等）原样放过；
3. **`pjp.proceed(args)`** ——这是全案例的**题眼**：把改造后的数组传回去调用原始方法。

如果这里写成无参的 `pjp.proceed()`，用的就还是**原始参数**，前面的 `trim()` 全白干。这是本案例最容易写错的一行。

## 四、运行结果

`App` 传入的密码是 `"root "`（尾部一个空格，长度 5）。

**当前代码（切面生效）**：

```
4
true
```

长度从 5 变成 4，说明空格已被切面去掉，校验通过。

**把 `DataAdvice` 上的 `@Component` 注释掉（切面失效）后**：

```
5
false
```

这就是没有 AOP 时的原始 bug 表现。两次对比跑下来，就能直观看到这个切面到底做了什么。

> 想再验证一下，可以把 `App` 里的密码换成 `"  root  "`（前后都加空格），结果依然是 `4` / `true`。

## 五、这个案例的价值

| 收获 | 说明 |
| --- | --- |
| **AOP 不只是"加日志"** | 它还能**修改业务数据**，直接参与业务逻辑 |
| **环绕通知的不可替代性** | 只有 `@Around` 能改入参再调用原始方法，`@Before` 拿到 `args` 也改不动实际调用 |
| **共性功能的抽取思路** | 去空格这种需求散落在几十个 Service 里，AOP 一处搞定 |
| **代码零侵入** | `ResourcesServiceImpl` / `ResourcesDaoImpl` 一行没改 |

## 六、两个值得注意的隐患

这两点是当前实现的局限，教学案例里无所谓，但真做到项目里要留意：

### 1. 参数为 `null` 会抛 NPE

```java
if (args[i].getClass().equals(String.class))   // args[i] 为 null 时直接 NPE
```

实测把 `App` 改成 `openURL(null, "root ")`：

```
java.lang.NullPointerException: Cannot invoke "Object.getClass()" because "args[i]" is null
    at com.spring.aop.DataAdvice.trimStr (DataAdvice.java:20)
```

更稳妥的写法是用 `instanceof`，它对 `null` 返回 `false`，天然免疫：

```java
if (args[i] instanceof String) {
    args[i] = ((String) args[i]).trim();
}
```

### 2. 切入点范围偏窄

`execution(boolean com.spring.service.*Service.*(*,*))` 把返回值锁死为 `boolean`、参数个数锁死为 2。真实项目里 Service 方法五花八门，通常会放宽成：

```
execution(* com.spring.service.*Service.*(..))
```

案例里写窄，是为了让匹配范围一目了然、便于教学。

## 七、小结

```
需求：入参前后的空格导致校验失败
思路：共性功能 → 抽成 AOP；要改入参 → 只能用环绕通知

核心三行：
    Object[] args = pjp.getArgs();     // 1. 取参数
    args[i] = args[i].toString().trim(); // 2. 加工（仅 String）
    Object ret = pjp.proceed(args);    // 3. ★ 用新参数调用原始方法

易错点：proceed() 不传 args → 改了个寂寞
```

前置知识见 `spring_22_aop_advice_data`（`getArgs()` 与 `proceed(args)` 的用法），下一站 `spring_24_case_transfer` 用环绕通知实现事务管理。

# spring_18_aop_quickstart —— AOP 入门

## 一、案例目标

**AOP（Aspect Oriented Programming，面向切面编程）：在不改动原始代码的前提下，为方法增加额外功能。**

本案例做的事很小：给 `BookDaoImpl.update()` 方法**在执行前**加一句"打印当前时间戳"，但**不动 `BookDaoImpl` 一行代码**。

这就是 AOP 的核心价值——像事务、日志、权限校验这类**和业务无关但到处都要写**的代码，可以抽出来集中管理。

### 案例设计的巧思

对比 `BookDaoImpl` 里的两个方法：

```java
public void save() {
    System.out.println(System.currentTimeMillis());   // ← 手写的
    System.out.println("book dao save ...");
}

public void update(){
    System.out.println("book dao update ...");        // ← 没有时间戳
}
```

`save()` 里的时间戳是**手工写死的**，`update()` 里没有。而运行后你会发现 **`update()` 也打印了时间戳**——那是 AOP 织入进去的。

`save()` 存在的意义就是当对照组：**同样的效果，一个靠手写重复代码，一个靠 AOP 自动增强。**

## 二、工程结构

```
spring_18_aop_quickstart
├── pom.xml                       spring-context + aspectjweaver
└── src/main/java/com/spring
    ├── App.java                              启动类
    ├── config/SpringConfig.java              @EnableAspectJAutoProxy
    ├── aop/MyAdvice.java                     ★ 切面类，AOP 全部逻辑在这
    ├── dao/BookDao.java                      接口
    └── dao/impl/BookDaoImpl.java             业务类，完全不知道 AOP 的存在
```

> `spring_17_aop_demo` 是本案例的前置铺垫，结构完全一样。可以对照着看 AOP 生效前后的差别。

依赖只多了一个：

```xml
<dependency>
  <groupId>org.aspectj</groupId>
  <artifactId>aspectjweaver</artifactId>
  <version>1.9.4</version>
</dependency>
```

> Spring AOP **借用了 AspectJ 的注解和切入点表达式语法**，但底层的代理实现是 Spring 自己的，并非真的用 AspectJ 织入。所以这个包主要是为了提供 `@Aspect`、`@Before` 这些注解和表达式解析能力。

## 三、AOP 核心概念

先记住这五个词，后面的代码就是它们的具体化：

| 概念 | 含义 | 本案例中 |
| --- | --- | --- |
| **连接点** JoinPoint | 所有**可以**被增强的方法 | `save()`、`update()` 等所有方法 |
| **切入点** Pointcut | **实际要**被增强的方法 | 只有 `BookDao.update()` |
| **通知** Advice | 增强的具体内容（那段共性代码） | `MyAdvice.method()` 里打印时间戳 |
| **通知类** | 存放通知方法的类 | `MyAdvice` |
| **切面** Aspect | 通知与切入点的**对应关系** | "在 `update()` 前面执行打印时间戳" |

一句话理清：**切面 = 在哪儿（切入点）+ 干什么（通知）+ 什么时机（前置/后置…）**。

> 连接点和切入点的区别：连接点是"候选集"，切入点是"选中的"。切入点一定是连接点，反之不成立。

## 四、代码逐行拆解

全部 AOP 逻辑都在 `MyAdvice` 这一个类里：

```java
//通知类必须配置成Spring管理的bean
@Component
//设置当前类为切面类
@Aspect
public class MyAdvice {
    //设置切入点，要求配置在方法上方
    @Pointcut("execution(void com.spring.dao.BookDao.update())")
    private void pt(){}

    //设置在切入点pt()的前面运行当前操作（前置通知）
    @Before("pt()")
    public void method(){
        System.out.println(System.currentTimeMillis());
    }
}
```

### 1. `@Component` —— 必须的

**通知类首先得是个 Spring bean。** 不加这个注解，容器根本扫不到它，AOP 静悄悄地不生效，也不报错——这是最难排查的一种失败。

### 2. `@Aspect` —— 声明这是切面类

告诉 Spring："这个类里定义了切入点和通知，请解析它。"

注意 `@Component` 和 `@Aspect` **两个都要加，缺一不可**：前者负责让类进容器，后者负责让 Spring 把它当切面处理。

### 3. `@Pointcut` —— 定义切入点

```java
@Pointcut("execution(void com.spring.dao.BookDao.update())")
private void pt(){}
```

这里有个初学者普遍的困惑：**这个 `pt()` 方法为什么是空的？**

因为**它根本不会被执行**。`pt()` 只是一个"挂载点"——切入点表达式必须依附在某个方法上，这个方法仅仅是给表达式起了个**名字**，方便后面引用。所以：

- 方法体**必须为空**（写了也不执行）；
- 返回值**必须是 `void``；
- 访问修饰符随意，本案例用 `private` 表示只在本类内引用；
- 方法名 `pt` 就是这个切入点的名字。

**表达式的结构**：

```
execution( void  com.spring.dao.BookDao.update() )
   ↑        ↑            ↑              ↑     ↑
 执行动作  返回值      包名.类名        方法名  参数
```

必须写**完整的全限定名**，且**匹配的是接口方法**（`BookDao.update()` 而非 `BookDaoImpl.update()`）——写接口更通用，所有实现类都会被匹配。

> 切入点表达式还支持通配符（`*`、`..`）来批量匹配，那是下一个案例 `spring_19_aop_pointcut` 的内容。本案例只精确匹配一个方法。

### 4. `@Before` —— 绑定通知与切入点

```java
@Before("pt()")
public void method(){
    System.out.println(System.currentTimeMillis());
}
```

- `@Before` 表示**前置通知**：在切入点方法执行**之前**运行；
- `"pt()"` 引用上面定义的切入点，**括号不能省**；
- 方法体就是要织入的共性功能。

> 除了 `@Before`，还有 `@After`（后置）、`@Around`（环绕）、`@AfterReturning`、`@AfterThrowing`，共五种通知类型，`spring_20_aop_advice_type` 会逐一展开。

### 5. `@EnableAspectJAutoProxy` —— 总开关

```java
@Configuration
@ComponentScan("com.spring")
//开启注解开发AOP功能
@EnableAspectJAutoProxy
public class SpringConfig {
}
```

**没有这一行，前面所有的注解都是废纸。** 它的作用是向容器注册一个后置处理器，负责在 bean 创建时判断"这个 bean 需不需要被代理"，需要就生成代理对象顶替原对象。

对应 XML 配置里的 `<aop:aspectj-autoproxy/>`。

## 五、运行与验证

运行 `com.spring.App`：

```java
BookDao bookDao = ctx.getBean(BookDao.class);
bookDao.update();
System.out.println(bookDao);
System.out.println(bookDao.getClass());
```

预期输出：

```
1690000000000                                          ← AOP 织入的（原代码里没有！）
book dao update ...                                    ← 原始业务代码
com.spring.dao.impl.BookDaoImpl@1b26f7b2
class com.sun.proxy.$Proxy19                           ← 关键：不是 BookDaoImpl！
```

### 后两行才是本案例的精华

`App` 特意打印了 `bookDao` 和 `bookDao.getClass()`，这是**理解 AOP 原理最直观的证据**：

- `System.out.println(bookDao)` 打印出 `BookDaoImpl@...`，是因为代理对象把 `toString()` 也转发给了原始对象，**看起来像是原对象**；
- 但 `getClass()` 暴露了真相——实际类型是 **`com.sun.proxy.$Proxy19`**，一个 JDK 动态代理生成的类。

**所以你从容器里拿到的 `bookDao` 根本不是 `BookDaoImpl` 的实例**，而是一个代理。调用 `update()` 时，代理先执行通知（打印时间戳），再转发给真正的 `BookDaoImpl.update()`。

这也解释了为什么"不改原代码也能增强"——因为压根就没用原对象。

### 两个动手实验

1. **调用 `save()` 而不是 `update()`** → 只输出一次时间戳（那是方法里手写的），证明**切入点只匹配了 `update()`**，AOP 没有增强 `save()`。
2. **注释掉 `@EnableAspectJAutoProxy`** → 时间戳消失，且 `getClass()` 变回 `class com.spring.dao.impl.BookDaoImpl`。**没有 AOP 就没有代理**，两者是绑定的。

## 六、AOP 工作流程

1. **Spring 容器启动**
2. **读取所有切面配置中的切入点**
3. **初始化 bean，判定 bean 对应的类中的方法是否匹配到任意切入点**
   - 匹配失败，创建**对象**
   - 匹配成功，创建原始对象（**目标对象**）的**代理**对象
4. **获取 bean 执行方法**
   - 获取 bean，调用方法并执行，完成操作
   - 获取的 bean 是代理对象时，根据代理对象的运行模式运行原始方法与增强的内容，完成操作

> 这四步落到本案例的具体过程、目标对象与代理对象的区别、JDK 与 CGLIB 两种代理方式、以及由此推出的"类内部方法互调 AOP 失效"等问题，见 [README_AOP工作流程.md](README_AOP工作流程.md)。

## 七、常见问题

| 现象 | 原因 |
| --- | --- |
| AOP 完全不生效，也不报错 | 漏了 `@Component`、`@Aspect` 或 `@EnableAspectJAutoProxy` 三者之一 |
| 同上 | 切入点表达式写错了（包名/类名/方法名/返回值任一处不匹配都会静默失效） |
| `getBean(BookDaoImpl.class)` 报找不到 bean | 容器里存的是 JDK 代理，它只实现了 `BookDao` 接口，**必须按接口类型取** |
| `ClassCastException: $Proxy cannot be cast to BookDaoImpl` | 同上，强转成实现类必然失败 |
| 通知里想拿方法参数、返回值 | 需要用 `JoinPoint` 参数，见 `spring_22_aop_advice_data` |

第三、四条是 AOP 引入后最容易踩的坑：**一旦某个 bean 被代理，就只能按接口类型使用它**。这也是"面向接口编程"在 Spring 里被反复强调的现实原因之一。

> 如果类没有实现任何接口，Spring 会改用 CGLIB 生成子类代理，此时 `getClass()` 会显示 `BookDaoImpl$$EnhancerBySpringCGLIB$$...`。

## 八、小结

```
AOP 三件套（缺一不可）：
  ① 通知类加 @Component  → 让它进容器
  ② 通知类加 @Aspect     → 让 Spring 把它当切面
  ③ 配置类加 @EnableAspectJAutoProxy → 打开总开关

切面的定义：
  @Pointcut("execution(...)")  定义在哪儿切  → 挂在一个空方法上，方法名就是切入点名
  @Before("pt()")              定义切什么、什么时机
```

一句话：**AOP = 把重复的共性代码抽出来，用动态代理在运行时自动织回去。** 业务类对此毫不知情，这就是"无侵入"。

后续案例：`spring_19_aop_pointcut` 讲切入点表达式的通配写法，`spring_20_aop_advice_type` 讲五种通知类型，`spring_24_case_transfer` 会用 AOP 实现事务管理——那才是 AOP 在真实项目里最主要的用武之地。

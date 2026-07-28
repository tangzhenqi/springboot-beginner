# spring_20_aop_advice_type —— AOP 五种通知类型

## 一、案例目标

`spring_18_aop_quickstart` 只用了一种通知 `@Before`。本案例把 **AOP 全部 5 种通知类型**都过一遍，搞清楚每种通知**在原始方法的哪个时机执行**。

### AOP 通知类型概述

- AOP 通知描述了**抽取出来的共性功能**，根据这段功能**该加在原始方法的哪个位置**，最终运行时把它织入到合理的地方。
- AOP 通知共分为 **5 种类型**：

| 通知类型 | 注解 | 执行时机 | 掌握程度 |
| --- | --- | --- | --- |
| 前置通知 | `@Before` | 原始方法**之前** | 掌握 |
| 后置通知 | `@After` | 原始方法**之后**（无论是否异常） | 掌握 |
| **环绕通知** | `@Around` | 原始方法**前后都能**，并能控制其执行 | **重点** |
| 返回后通知 | `@AfterReturning` | 原始方法**正常返回后**（有异常则不执行） | 了解 |
| 抛出异常后通知 | `@AfterThrowing` | 原始方法**抛异常后** | 了解 |

## 二、工程结构

```
spring_20_aop_advice_type
└── src/main/java/com/spring
    ├── App.java                              调用 bookDao.select()
    ├── config/SpringConfig.java              @EnableAspectJAutoProxy
    ├── aop/MyAdvice.java                     ★ 5 种通知全在这，大部分被注释
    ├── dao/BookDao.java                      update() 和 select()
    └── dao/impl/BookDaoImpl.java             select() 里藏了一行注释掉的 1/0
```

案例设计了两个方法作对照：

- `update()`：返回 `void`，用来演示前置/后置/环绕；
- `select()`：返回 `int`（100），用来演示环绕、返回后、异常后——因为**只有它有返回值和异常场景**。

`MyAdvice` 里 5 种通知都写好了，但**大部分是注释状态**，当前只放开了 `aroundSelect`（`@Around`）和 `afterThrowing`（`@AfterThrowing`）。想看哪个通知，把对应注解的注释打开即可。

## 三、五种通知逐一说明

### 1. `@Before` 前置通知

```java
@Before("match_update()")
public void before() {
    System.out.println("before advice ...");
}
```

在原始方法执行**之前**运行。最简单，`spring_18` 用的就是它。

### 2. `@After` 后置通知

```java
@After("match_select()")
public void after() {
    System.out.println("after advice ...");
}
```

在原始方法执行**之后**运行。**关键点：无论原始方法正常结束还是抛异常，它都会执行**（类似 `finally`）。

### 3. `@Around` 环绕通知（重点，最常用）

这是整个 AOP 里最重要的通知类型，其余四种能做的它几乎都能做。

| 项 | 说明 |
| --- | --- |
| **名称** | `@Around`（重点、常用） |
| **类型** | 方法注解 |
| **位置** | 通知方法定义上方 |
| **作用** | 设置通知方法与切入点的绑定关系，通知方法在原始切入点方法**前后**运行 |

标准范例：

```java
@Around("pt()")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("around before advice ...");
    Object ret = pjp.proceed();          // ← 调用原始方法
    System.out.println("around after advice ...");
    return ret;
}
```

**环绕通知有四条硬性要求，缺一不可**——这也是它和其他四种通知最大的区别：

| # | 要求 | 原因 |
| --- | --- | --- |
| 1 | 方法要有 `ProceedingJoinPoint` 参数 | 靠它的 `proceed()` 去调用原始方法 |
| 2 | 方法内必须调用 `pjp.proceed()` | **不调用，原始方法就不执行了** |
| 3 | 返回值类型为 `Object` | 要把原始方法的返回值原样传回去 |
| 4 | 方法要 `throws Throwable` | `proceed()` 声明抛出 `Throwable` |

本案例里针对 `select()`（有返回值）的版本：

```java
@Around("match_select()")
public Object aroundSelect(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("around before advice ...");
    Integer ret = (Integer) pjp.proceed();   // select() 返回 int，这里接住
    System.out.println("around after advice ...");
    return ret;                              // 必须 return，否则调用方拿到 null
}
```

> **最容易踩的两个坑**：
> 1. **忘了 `return ret`** → `App` 里 `bookDao.select()` 拿到的是 `null`，`int num` 自动拆箱时抛 `NullPointerException`。
> 2. **忘了调 `pjp.proceed()`** → 原始方法 `select()` 根本不执行，`book dao select is running ...` 不打印，返回值也没了。

环绕通知的威力在于——它能**决定原始方法要不要执行、改写入参、篡改返回值、捕获异常**，这是其他四种通知都做不到的。事务管理、性能监控、缓存、统一异常处理，实现底层几乎都是环绕通知。

### 4. `@AfterReturning` 返回后通知

```java
@AfterReturning("match_select()")
public void afterReturning() {
    System.out.println("afterReturning advice ...");
}
```

在原始方法**正常执行完毕、成功返回后**运行。**如果原始方法抛了异常，它不执行**——这是它和 `@After` 的核心区别。

### 5. `@AfterThrowing` 抛出异常后通知

```java
@AfterThrowing("match_select()")
public void afterThrowing() {
    System.out.println("afterThrowing advice ...");
}
```

在原始方法**执行过程中抛出异常后**运行。方法正常结束时它不执行。常用于记录错误日志。

`BookDaoImpl.select()` 里特意留了一行：

```java
public int select() {
    System.out.println("book dao select is running ...");
//    int i = 1/0;      // ← 放开这行制造异常，用来触发 @AfterThrowing
    return 100;
}
```

**放开 `1/0`**，`select()` 就会抛 `ArithmeticException`，此时 `@AfterThrowing` 才会触发。

## 四、@After 和 @AfterReturning 的区别（易混）

这两个名字很像，务必分清：

| | `@After` 后置通知 | `@AfterReturning` 返回后通知 |
| --- | --- | --- |
| 正常返回时 | ✓ 执行 | ✓ 执行 |
| 抛异常时 | ✓ **仍执行** | ✗ **不执行** |
| 类比 | `finally` | `try` 的正常出口 |

一句话：`@After` 是"不管怎样都执行"，`@AfterReturning` 是"只有成功才执行"。

## 五、五种通知的执行顺序

以环绕通知包裹、方法**正常返回**为例，完整顺序是：

```
@Around  around before ...
    @Before  before ...
        ┌─ 原始方法 select() 执行 ─┐
        └──────────────────────────┘
    @AfterReturning ...   （正常返回才执行）
    @After ...            （总会执行）
@Around  around after ...
```

如果原始方法**抛出异常**：

```
@Around  around before ...
    @Before  before ...
        ┌─ 原始方法抛异常 ─┐
    @AfterThrowing ...    （异常才执行）
    @After ...            （总会执行）
    ✗ @AfterReturning 不执行
    ✗ @Around 的 around after 也不执行（因为 proceed() 抛异常中断了）
```

> 注意最后一点：异常场景下，环绕通知 `pjp.proceed()` 之后的代码（`around after advice ...`）**不会执行**，除非你用 try-catch 把 `proceed()` 包起来。这也是环绕通知能做"统一异常处理"的原因。

## 六、运行方式

当前 `MyAdvice` 放开的是 `aroundSelect`（`@Around`）+ `afterThrowing`（`@AfterThrowing`），`App` 调用 `bookDao.select()`。

**默认情况（`select()` 正常返回 100）**：

```
around before advice ...
book dao select is running ...
around after advice ...
100
```

`@AfterThrowing` 因为没异常，不触发。

**放开 `BookDaoImpl` 里的 `int i = 1/0`**：

```
around before advice ...
book dao select is running ...
afterThrowing advice ...
（程序抛 ArithmeticException 终止）
```

### 建议的实验步骤

想完整体会五种通知，依次放开注解观察：

1. 只放开 `@Before` → 看它在原始方法前打印；
2. 加 `@After` + `@AfterReturning` → 看两者都在方法后打印；
3. 放开 `1/0` → `@AfterReturning` 消失、`@AfterThrowing` 出现，`@After` 依然在；
4. 放开 `@Around` → 看它把整个过程包在最外层。

## 七、小结

```
5 种通知 = 5 个不同的时机：
  @Before          前 ────────┐
  @Around          前 + 后（能控制原始方法是否执行、改返回值）★重点
  @After           后（finally，必执行）
  @AfterReturning  后（仅正常返回）
  @AfterThrowing   后（仅抛异常）

环绕通知模板（背下来）：
  public Object x(ProceedingJoinPoint pjp) throws Throwable {
      // before
      Object ret = pjp.proceed();
      // after
      return ret;
  }
```

实际项目中 **90% 的场景用 `@Around`**，因为它最灵活。下一步 `spring_22_aop_advice_data` 会讲怎么在通知里获取原始方法的**参数和返回值**，`spring_24_case_transfer` 则用环绕通知实现事务管理。

# spring_22_aop_advice_data —— AOP 通知获取数据（参数 / 返回值 / 异常）

## 一、案例目标

`spring_20_aop_advice_type` 解决的是"通知在什么**时机**执行"，本案例解决的是"通知里怎么拿到原始方法的**数据**"。

具体分三块：

- **获取参数**：所有通知类型都能拿；
- **获取返回值**：只有 `@AfterReturning` 和 `@Around` 能拿；
- **获取异常**：只有 `@AfterThrowing` 和 `@Around` 能拿。

原因很直白：前置通知运行时方法还没执行完，哪来的返回值和异常；返回后通知能正常触发就说明没异常。后置通知 `@After` 理论上两者都可有可无，所以不做研究。

| 数据 | @Before | @After | @Around | @AfterReturning | @AfterThrowing |
| --- | :---: | :---: | :---: | :---: | :---: |
| 参数 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 返回值 | ✗ | 不研究 | ✅ | ✅ | ✗ |
| 异常 | ✗ | 不研究 | ✅ | ✗ | ✅ |

拿数据靠两个对象：

| 对象 | 适用通知 | 说明 |
| --- | --- | --- |
| `JoinPoint` | 前置、后置、返回后、抛出异常后 | 描述切入点的对象，**必须是通知方法的第一个参数** |
| `ProceedingJoinPoint` | 环绕通知 | `JoinPoint` 的**子类**，多了 `proceed()`，能调用原始方法 |

## 二、工程结构

```
spring_22_aop_advice_data
├── pom.xml                                   spring-context 5.2.10 + aspectjweaver 1.9.4
└── src/main/java
    ├── App.java                              bookDao.findName(100, "itheima")
    └── com/spring
        ├── config/SpringConfig.java          @EnableAspectJAutoProxy
        ├── aop/MyAdvice.java                 ★ 五种通知全部放开，核心在这
        ├── dao/BookDao.java                  String findName(int id, String password)
        └── dao/impl/BookDaoImpl.java         打印 id，然后抛 NullPointerException
```

这个案例的原始方法被特意设计成 **既有参数、又有返回值、还会抛异常**，这样一个方法就能把三种数据全演示完：

```java
public String findName(int id, String password) {
    System.out.println("id:" + id);
    if (true) throw new NullPointerException();   // ← 制造异常，注释掉即为正常场景
    return "itcast";
}
```

> `if(true)` 是为了骗过编译器——直接写 `throw` 会让下面的 `return` 变成不可达代码，编译不过。

## 三、获取参数

### 3.1 JoinPoint（前置、后置、返回后、抛出异常后）

```java
@Before("pt()")
public void before(JoinPoint jp) {
    Object[] args = jp.getArgs();          // 拿到 [100, itheima]
    System.out.println(Arrays.toString(args));
    System.out.println("before advice ...");
}
```

要点：

1. `jp.getArgs()` 返回 `Object[]`，即原始方法的**全部参数**；
2. `JoinPoint` **必须写在形参的第一个位置**，否则 Spring 无法绑定，运行时报错。

### 3.2 ProceedingJoinPoint（环绕通知）

`ProceedingJoinPoint` 是 `JoinPoint` 的子类，所以 `getArgs()` 照样能用：

```java
@Around("pt()")
public Object around(ProceedingJoinPoint pjp) {
    Object[] args = pjp.getArgs();
    System.out.println(Arrays.toString(args));
    args[0] = 666;                          // ★ 改参数
    Object ret = null;
    try {
        ret = pjp.proceed(args);            // ★ 用新参数调用原始方法
    } catch (Throwable t) {
        t.printStackTrace();
    }
    return ret;
}
```

这是环绕通知**独有的能力**：`proceed()` 有两个重载

- `proceed()`：用**原始参数**调用；
- `proceed(Object[] args)`：用**你给的参数**调用 —— 相当于在方法执行前把入参改掉。

案例里把 `args[0]` 从 `100` 改成了 `666`，所以 `BookDaoImpl` 打印出来的是 `id:666`，而且后面 `@Before`、`@After` 拿到的也是 `[666, itheima]`。

> 这个"改参数"的能力就是后面 `spring_23_case_handle_password`（对入参统一去空格）的实现基础。

## 四、获取返回值

### 4.1 环绕通知获取返回值

`pjp.proceed()` 的返回值就是原始方法的返回值，直接接住即可，而且**可以修改后再 return**：

```java
Object ret = pjp.proceed(args);
return ret;                    // 想改就改成别的值
```

注意环绕通知方法的返回值类型必须写 `Object`，且必须把 `ret` 返回出去，否则调用方拿到的就是 `null`。

### 4.2 返回后通知获取返回值

```java
@AfterReturning(value = "pt()", returning = "ret")
public void afterReturning(JoinPoint jp, String ret) {
    System.out.println("afterReturning advice ..." + ret);
}
```

三个必须注意的点：

1. **`returning` 属性值必须与方法形参名完全相同**（这里都是 `ret`），Spring 靠名字做绑定；
2. **参数顺序**：如果同时要 `JoinPoint`，`JoinPoint` 必须排第一，`returning` 绑定的参数排后面；
3. **参数类型**：这里写成 `String` 能跑，是因为 `findName` 恰好返回 `String`。但**推荐写成 `Object`** —— 形参类型同时充当了匹配条件，写具体类型会导致返回值类型对不上时通知直接不执行（不报错，静默跳过，很难排查）。

## 五、获取异常

### 5.1 环绕通知获取异常

把 `proceed()` 用 try-catch 包起来，`catch` 到的就是原始方法抛的异常：

```java
try {
    ret = pjp.proceed(args);
} catch (Throwable t) {
    t.printStackTrace();       // 拿到异常，怎么处理看业务需求
}
```

捕获之后异常就**不再往外抛**了，所以本案例中 `App` 不会崩溃，只会打印 `null`。

> 对比 `spring_20`：那里的环绕通知直接 `throws Throwable`，异常穿透出去程序就终止了。

### 5.2 抛出异常后通知获取异常

```java
@AfterThrowing(value = "pt()", throwing = "t")
public void afterThrowing(Throwable t) {
    System.out.println("afterThrowing advice ..." + t);
}
```

同样，**`throwing` 属性值必须与形参名相同**。这个通知只是"旁观"异常，不会吞掉它，异常仍会继续往外抛。

## 六、运行结果

`App` 调用 `bookDao.findName(100, "itheima")`。

### 6.1 默认场景（原始方法抛 NullPointerException）

```
[100, itheima]                                        ← @Around 拿到原始参数
[666, itheima]                                        ← @Before 拿到被改过的参数
before advice ...
id:666                                                ← 原始方法用的是 666
afterThrowing advice ...java.lang.NullPointerException ← @AfterThrowing 拿到异常
[666, itheima]
after advice ...                                      ← @After 照常执行
java.lang.NullPointerException                        ← @Around 里 printStackTrace 打的
	at com.spring.dao.impl.BookDaoImpl.findName(BookDaoImpl.java:12)
	...
null                                                  ← 异常被吞，ret 为 null
```

`@AfterReturning` 不执行（有异常），程序**没有终止**（异常被环绕通知捕获了）。

### 6.2 正常场景（注释掉 `if(true)throw ...`）

```
[100, itheima]
[666, itheima]
before advice ...
id:666
afterReturning advice ...itcast                       ← 拿到返回值
[666, itheima]
after advice ...
itcast                                                ← App 打印的返回值
```

`@AfterThrowing` 不执行（无异常）。

### 6.3 关于执行顺序

从运行结果能反推出五个通知在代理链上的嵌套关系（外 → 内）：

```
@Around
  └ @Before
      └ @After
          └ @AfterReturning
              └ @AfterThrowing
                  └ 原始方法 findName()
```

所以异常场景下的打印顺序是：`@AfterThrowing` 先响应 → 再往外冒到 `@After` → 最后被最外层的 `@Around` catch 住。

> 这是 Spring 5.2.7 之后同一切面内的默认顺序，**不要依赖它写业务**。多个通知有明确顺序要求时，应拆成多个切面类并用 `@Order` 指定优先级。

## 七、小结

```
拿参数：JoinPoint.getArgs()            —— 五种通知都能用，JoinPoint 必须是第一个参数
       ProceedingJoinPoint.getArgs()   —— 环绕专用，是 JoinPoint 的子类
       pjp.proceed(args)               —— 环绕独有：换一套参数去调原始方法

拿返回值：@AfterReturning(returning="ret")   —— 属性名 == 形参名，类型建议写 Object
        Object ret = pjp.proceed()          —— 环绕，能拿也能改

拿异常：@AfterThrowing(throwing="t")        —— 属性名 == 形参名，只旁观不吞
       try{ pjp.proceed() }catch(...)       —— 环绕，能拿也能吞

结论：环绕通知（@Around）参数、返回值、异常全都能拿能改，所以最常用。
```

三个高频踩坑：

1. `returning` / `throwing` 的属性值和形参名**必须一致**；
2. `JoinPoint` 必须是通知方法的**第一个形参**；
3. `@AfterReturning` 的返回值形参**别写具体类型**，写 `Object`，否则类型不匹配时通知会静默不执行。

下一站：`spring_23_case_handle_password` 用本案例的 `proceed(args)` 改参数能力做"密码去空格"，`spring_24_case_transfer` 用环绕通知做事务管理。

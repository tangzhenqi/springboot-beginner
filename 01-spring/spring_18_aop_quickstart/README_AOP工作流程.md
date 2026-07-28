# AOP 工作流程

> 配套案例：`spring_18_aop_quickstart`，主文档见 [README.md](README.md)

## 一、标准四步

1. **Spring 容器启动**
2. **读取所有切面配置中的切入点**
3. **初始化 bean，判定 bean 对应的类中的方法是否匹配到任意切入点**
   - 匹配失败，创建**对象**
   - 匹配成功，创建原始对象（**目标对象**）的**代理**对象
4. **获取 bean 执行方法**
   - 获取 bean，调用方法并执行，完成操作
   - 获取的 bean 是代理对象时，根据代理对象的运行模式运行原始方法与增强的内容，完成操作

## 二、两个关键名词

第 3 步引出了 AOP 里两个必须分清的对象：

| 名词 | 指什么 | 本案例中 |
| --- | --- | --- |
| **目标对象** Target | 原始的、未被增强的那个对象 | `new BookDaoImpl()` |
| **代理对象** Proxy | 由容器生成、顶替目标对象放进容器的那个 | `$Proxy19` |

**目标对象仍然存在**，只是它不再直接暴露给你——代理对象内部持有它的引用，负责在合适的时机把调用转发过去。这就是 `App` 里 `getClass()` 打印出 `$Proxy19` 的原因：

```java
System.out.println(bookDao);            // com.spring.dao.impl.BookDaoImpl@1b26f7b2
System.out.println(bookDao.getClass()); // class com.sun.proxy.$Proxy19
```

第一行看起来像目标对象，是因为代理把 `toString()` 也转发给了它；`getClass()` 才暴露真实身份。

## 三、落到本案例

```
① 容器启动
      AnnotationConfigApplicationContext(SpringConfig.class)

② 读取切入点
      扫描到 MyAdvice，识别 @Aspect
      解析出切入点 pt() = execution(void com.spring.dao.BookDao.update())
      解析出通知 @Before("pt()") → method()

③ 初始化 bean，逐个判定
      MyAdvice     → 类中方法没匹配到切入点 → 直接创建对象
      BookDaoImpl  → update() 匹配成功
                   → 创建目标对象 BookDaoImpl 实例
                   → 再为它生成代理对象 $Proxy19，放进容器
                     （容器里存的是代理，不是目标对象）

④ 获取 bean 执行方法
      ctx.getBean(BookDao.class)  → 拿到 $Proxy19
      bookDao.update()            → 被代理拦截
            → 先执行增强内容：@Before 通知，打印时间戳
            → 再调用目标对象的原始方法：打印 book dao update ...
```

注意第 3 步的**判定是逐个 bean 进行的**：`MyAdvice` 自己也是容器里的 bean，但它的方法没匹配到任何切入点，所以走"匹配失败"分支，正常创建对象即可。

第 4 步的"**根据代理对象的运行模式**"指的是通知类型的差异——本案例是 `@Before`，所以先增强后原始；如果换成 `@After` 就是先原始后增强，`@Around` 则由你自己决定顺序。这部分是 `spring_20_aop_advice_type` 的内容。

## 四、两种代理方式

第 3 步"创建代理对象"具体怎么创建，取决于目标类有没有实现接口：

| | JDK 动态代理 | CGLIB 代理 |
| --- | --- | --- |
| 适用 | 目标类**实现了接口** | 目标类**没有实现接口** |
| 原理 | 运行时生成一个实现同样接口的类 | 运行时生成目标类的**子类** |
| `getClass()` | `com.sun.proxy.$Proxy19` | `BookDaoImpl$$EnhancerBySpringCGLIB$$...` |
| 限制 | 只能按接口类型使用 | 目标类和方法不能是 `final` |

**本案例走的是 JDK 动态代理**，因为 `BookDaoImpl implements BookDao`。Spring 默认优先用 JDK 代理，没有接口时才退回 CGLIB。

这直接导致一个后果：

```java
ctx.getBean(BookDao.class);       // ✓ 按接口取，正常
ctx.getBean(BookDaoImpl.class);   // ✗ NoSuchBeanDefinitionException
```

因为 `$Proxy19` 只实现了 `BookDao` 接口，**和 `BookDaoImpl` 没有继承关系**。强转同理会抛 `ClassCastException`。

> 想强制使用 CGLIB，可以配 `@EnableAspectJAutoProxy(proxyTargetClass = true)`。SpringBoot 2.x 之后默认就是 CGLIB，正是为了绕开"必须按接口取"这个限制。

## 五、两个容易忽略的推论

### 1. 代理与否在启动时就定了

流程第 3 步说明：**是否创建代理是在容器启动时决定好的**，不是调用方法时才判断的。

由此可以解释一个高频现象——**同一个类内部的方法互相调用，AOP 不生效**：

```java
public void update(){
    this.save();     // ← 直接落在目标对象上，没经过代理，save() 的增强不会执行
}
```

因为内部调用走的是 `this.xxx()`，压根没经过代理对象。**事务失效最经典的成因就是这个**，在 `spring_24_case_transfer` 里要格外注意。

### 2. 代理是"顶替"而不是"包装原对象后再放回去"

容器里 `bookDao` 这个 bean 的值**自始至终就是代理对象**，目标对象只被代理持有，从不单独出现在容器中。

所以任何注入了 `BookDao` 的地方，拿到的都是同一个代理——AOP 的增强对**所有调用方**一视同仁，这也是它能用来做事务、日志这类横切功能的前提。

## 六、验证方式

运行 `com.spring.App`：

```
1690000000000                        ← 第 4 步织入的增强内容
book dao update ...                  ← 目标对象的原始方法
com.spring.dao.impl.BookDaoImpl@1b26f7b2
class com.sun.proxy.$Proxy19         ← 第 3 步创建的代理对象
```

想验证流程分支，可以做两个实验：

1. **改调 `bookDao.save()`** → 只有方法里手写的那次时间戳。`save()` 没匹配到切入点，走的是第 3 步的"匹配失败"分支……但要注意：**代理是按 bean 整体创建的**，只要类中有**任意一个**方法匹配，整个 bean 就会被代理。所以 `bookDao` 依然是 `$Proxy19`，只是调用 `save()` 时代理直接转发、不做增强。
2. **注释掉 `@EnableAspectJAutoProxy`** → 第 2 步不再执行，没有切入点可读，所有 bean 都走"匹配失败"分支。时间戳消失，`getClass()` 变回 `class com.spring.dao.impl.BookDaoImpl`。

第 1 个实验尤其值得做——它能纠正"只有被增强的方法才走代理"这个常见误解：**代理的粒度是 bean（类），增强的粒度才是方法。**

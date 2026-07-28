# spring_07_di_autoware —— 依赖自动装配

## 一、案例目标

前两个案例手写了依赖注入的配置：

- [`spring_05_di_set`](../spring_05_di_set)：`<property name="bookDao" ref="bookDao"/>`
- [`spring_06_di_constructor`](../spring_06_di_constructor)：`<constructor-arg name="bookDao" ref="bookDao"/>`

共同的麻烦是**每个依赖都要手写一行**。本案例引入 `autowire` 属性，让 Spring 自己去容器里找合适的 Bean 注入，配置从一行缩成一个属性：

```xml
<bean class="com.spring.dao.impl.BookDaoImpl"/>
<bean id="bookService" class="com.spring.service.impl.BookServiceImpl" autowire="byType"/>
```

注意 `BookDaoImpl` 那行**连 id 都没写**——按类型装配时用不上名字。

## 二、工程结构

```
spring_07_di_autoware
├── pom.xml                       spring-context 5.2.10
├── README.md                     本文
├── autowire-源码剖析.md           autowire="byType" 的源码级流程分析
└── src/main
    ├── java/com/spring
    │   ├── AppForDIAutoware.java             启动类
    │   ├── dao/BookDao.java                  接口
    │   ├── dao/impl/BookDaoImpl.java         实现类
    │   ├── service/BookService.java          接口
    │   └── service/impl/BookServiceImpl.java 保留了 setBookDao()，这点很关键
    └── resources/applicationContext.xml      autowire="byType"
```

## 三、三种注入方式的区别

这是本案例的核心。先看一张总表，下面再逐条展开：

| | 构造器注入 | setter 注入 | 自动装配 `autowire` | `@Autowired` |
| --- | --- | --- | --- | --- |
| 写法 | `<constructor-arg>` | `<property>` | `autowire="byType"` | 字段上加注解 |
| 出现案例 | [06](../spring_06_di_constructor) | [05](../spring_05_di_set) | **本案例 07** | [13](../spring_13_annotation_di) |
| 需要构造方法 | ✅ 必须 | ❌ | ❌（byType/byName）| ❌ |
| **需要 setter** | ❌ | ✅ **必须** | ✅ **必须** | ❌ **不需要** |
| 能注入简单类型 | ✅ | ✅ | ❌ **只能引用类型** | ✅（用 `@Value`）|
| 依赖能否为 final | ✅ | ❌ | ❌ | ❌ |
| 缺依赖时 | 启动报错 | 启动报错 | ⚠️ **静默跳过** | 启动报错 |
| 配置量 | 每个依赖一行 | 每个依赖一行 | 一个属性搞定 | 一个注解 |

### 1. 构造器注入 vs setter 注入

两者的本质区别是**依赖在什么时候到位**：

- **构造器注入**：对象创建出来时依赖就已经齐了，之后不可更改。可以把字段声明成 `final`，天然线程安全，也不可能出现"对象存在但依赖是 null"的中间状态。
- **setter 注入**：先 new 出一个"空壳"对象，再逐个调 setter 填充。所以字段不能是 `final`，且在填充完成前对象处于半初始化状态。

代价是构造器注入在**依赖多的时候构造方法会很长**，而且两个 Bean 相互依赖时（A 的构造方法要 B，B 的构造方法要 A）会直接死锁报循环依赖错误——setter 注入则能被 Spring 的三级缓存化解。

> 实践中的取舍：**强制依赖用构造器，可选依赖用 setter**。Spring 官方文档和 IDEA 的检查提示都推荐构造器注入。

### 2. 自动装配 `autowire` —— 本案例的主角

`applicationContext.xml:16` 只写了一个属性，Spring 就自己完成了注入。它有两种模式：

| 模式 | 匹配依据 | 要求 |
| --- | --- | --- |
| `byType`（推荐）| 按**类型**在容器中找 | 该类型的 Bean **必须唯一**，找到多个直接报错 |
| `byName` | 按**名称**找，名字取自 setter 推导出的属性名 | 必须存在该名称的 Bean |

`byName` 不推荐，因为它让**变量名和配置产生了耦合**——把 `setBookDao` 改个名字，配置就悄悄失效了。

除此之外还有 `autowire="constructor"`，即按构造方法参数类型自动装配，实际用得很少。

#### `bookDao` 这个字段究竟是怎么被填上的？

看代码时最容易犯迷糊的一点：`BookServiceImpl` 里既没有 `new BookDaoImpl()`，`main` 方法里也没人调过 `setBookDao()`，那 `bookDao` 是从哪来的？

答案是——**setter 是容器帮你调的**。完整链路分三步：

**① 容器先造出 `BookDao` 实例**

```xml
<bean class="com.spring.dao.impl.BookDaoImpl"/>   <!-- applicationContext.xml:16 -->
```

容器启动时反射调用 `BookDaoImpl` 的无参构造，造出对象放进容器。这里没写 `id`，是因为 `byType` 根本不看名字，Spring 会给它一个默认名 `com.spring.dao.impl.BookDaoImpl#0`。

**② 容器造出 `BookServiceImpl` 实例**

同样先调无参构造。**此刻 `bookDao` 字段还是 `null`**，对象处于"空壳"状态。

**③ 容器执行属性注入（关键一步）**

因为 `<bean>` 上写了 `autowire="byType"`，Spring 会：

1. 扫描 `BookServiceImpl` 的所有 setter，发现 `setBookDao(BookDao)`；
2. 取出参数类型 `BookDao`；
3. 去容器里找类型能赋值给 `BookDao` 的 Bean —— `BookDaoImpl implements BookDao`，匹配；
4. 反射调用 `bookServiceImpl.setBookDao(那个 BookDaoImpl 实例)`。

所以 setter **依然是必需的**：`autowire="byType"` 走的正是 setter 注入这条通道，它省掉的只是"参数从哪来"这个信息——由容器按类型推断，而不是你在 XML 里用 `<property ref="..."/>` 显式指定。

**和不用自动装配的写法对照一下就很清楚了：**

```xml
<!-- 手写版（案例 05 的做法） -->
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>
<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <property name="bookDao" ref="bookDao"/>   <!-- 手动指明喂谁 -->
</bean>

<!-- 自动装配版（本案例） -->
<bean class="com.spring.dao.impl.BookDaoImpl"/>
<bean id="bookService" class="com.spring.service.impl.BookServiceImpl" autowire="byType"/>
```

`autowire="byType"` 做的事，就是把那行 `<property>` 省掉。

**动手验证"是容器调的 setter"：** 在 setter 里加一行打印：

```java
public void setBookDao(BookDao bookDao) {
    System.out.println("setBookDao 被调用了：" + bookDao);
    this.bookDao = bookDao;
}
```

跑 `AppForDIAutoware`，会看到这行输出出现在 `getBean()` **之前**——因为默认的单例 Bean 在容器启动时就已经初始化并注入完毕了。再把 `autowire="byType"` 删掉重跑，setter 不会被调用，`save()` 时直接 NPE，反过来证明注入完全来自这个属性。

### 3. 四个必须记住的特征

源码 `applicationContext.xml:6-12` 的注释里列了四条，逐条解释：

**① 只能用于引用类型，不能注入简单类型**

`String`、`int` 这些没法"按类型找 Bean"——容器里可能有一堆 String，Spring 无从判断你要哪个。简单类型必须老老实实用 `<property value="...">` 或 `@Value`。

**② `byType` 要求容器中该类型的 Bean 唯一**

如果 `BookDao` 有两个实现类都注册进了容器，启动会抛 `NoUniqueBeanDefinitionException`。这个问题在注解时代由 `@Qualifier` 解决（见 [案例 13](../spring_13_annotation_di)）。

**③ `byName` 要求存在指定名称的 Bean**，且与变量名耦合，不推荐。

**④ 自动装配的优先级低于 setter 注入和构造器注入**

同时写了 `<property>` 和 `autowire`，以 `<property>` 为准，自动装配的结果会被覆盖。

### 4. ⚠️ 最容易踩的坑：`autowire` 仍然依赖 setter

这是本案例最值得注意的地方——`BookServiceImpl.java:9` 的 `setBookDao()` **不能删**：

```java
public class BookServiceImpl implements BookService{
    private BookDao bookDao;

    public void setBookDao(BookDao bookDao) {   // ← 删掉它，注入就失效了
        this.bookDao = bookDao;
    }
    ...
}
```

XML 的自动装配本质上是"**帮你自动填 `<property>` 标签**"，走的仍然是 JavaBean 内省 + 调用 setter 的老路。它省掉的只是配置文件里那一行，并没有改变注入机制。

**更麻烦的是它失败得很安静。** 实测删掉 setter 后：

```
book service save ...
Exception in thread "main" java.lang.NullPointerException:
    Cannot invoke "com.spring.dao.BookDao.save()" because "this.bookDao" is null
```

容器**启动阶段完全不报错**，Spring 扫描不到可写属性就直接跳过了，直到运行时用到才 NPE。相比之下 `@Autowired` 找不到依赖会在启动时就抛 `NoSuchBeanDefinitionException`，问题暴露得早得多。

> 想知道"为什么是静默跳过而不是报错"，见 [`autowire-源码剖析.md`](./autowire-源码剖析.md) 第 3 步——根因是没有 setter 的属性压根没进候选名单，Spring 从头到尾没尝试过注入它。

### 5. `@Autowired` 与 `autowire` 的关系

两者名字像，机制完全不同：

| | XML `autowire="byType"` | 注解 `@Autowired` |
| --- | --- | --- |
| 作用位置 | 写在 `<bean>` 上，**作用于整个 Bean 的所有属性** | 写在**具体某个字段/方法**上，粒度更细 |
| 注入手段 | JavaBean 内省，**调 setter** | 反射 `Field.setAccessible(true)`，**直接写字段** |
| 找不到依赖 | 静默跳过 | 启动报错（可用 `required = false` 放宽）|
| 多个候选 | 直接报错 | 可用 `@Qualifier` 指定 |

所以 `@Autowired` 不是 `autowire` 的注解版翻译，而是更精细、更安全的替代品。到 [案例 13](../spring_13_annotation_di) 会看到，用了 `@Autowired` 之后 `BookServiceImpl` 里的 setter 就可以彻底删掉了。

## 四、运行方式

运行 `com.spring.AppForDIAutoware` 的 `main` 方法：

```java
ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");
BookService bookService = (BookService) ctx.getBean("bookService");
bookService.save();
```

预期输出：

```
book service save ...
book dao save ...
```

第二行能打印出来，就说明 `autowire="byType"` 成功把 `BookDaoImpl` 注入进了 `bookService`。

**建议动手验证的三个改动：**

1. **删掉 `setBookDao()` 方法** → 启动不报错，运行到 `bookDao.save()` 时 NPE，亲身感受"静默失败"；
2. **把 `autowire="byType"` 改成 `byName`** → 因为 `BookDaoImpl` 那行没写 id，容器里没有名为 `bookDao` 的 Bean，注入失败同样 NPE；给它补上 `id="bookDao"` 才能成功；
3. **再注册一个 `BookDao` 的实现类** → `byType` 立刻抛 `NoUniqueBeanDefinitionException`。

## 五、小结

```
构造器注入  →  依赖不可变、可 final、启动即完整      强制依赖首选
setter 注入 →  可选依赖、能解循环依赖              需要 setter
autowire    →  少写配置，但仍走 setter、静默失败     XML 时代的过渡方案
@Autowired  →  反射直写字段、粒度细、失败快          注解时代的答案
```

自动装配是 XML 配置向注解开发过渡的中间形态：它减少了配置量，但没有解决 setter 依赖和错误暴露太晚的问题。真正的解法在 [案例 13](../spring_13_annotation_di) 的 `@Autowired`。

上面这些结论都能在源码里找到依据，想深挖的话看 [`autowire-源码剖析.md`](./autowire-源码剖析.md)：从 XML 解析到反射调用 setter 的完整六步，以及 `unsatisfiedNonSimpleProperties()` 里那四个判断条件是怎么一一对应到本文的各条规则的。

下一个案例 [`spring_08_di_collection`](../spring_08_di_collection) 会补上依赖注入的最后一块：**集合类型**（List / Set / Map / Properties）怎么注入。

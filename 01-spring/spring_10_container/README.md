# spring_10_container：Spring 容器加载与 Bean 获取详解

## 一、案例要解决什么问题

这个案例不是在演示复杂业务，而是用一个最小的 `BookDao` 对象回答 Spring 容器的几个基础问题：

1. Spring 如何读取 XML 配置并建立容器？
2. `ApplicationContext` 和 `BeanFactory` 有什么关系与区别？
3. 类路径加载与文件系统加载有什么区别？
4. 可以通过哪些方式从容器中获取 Bean？
5. `lazy-init="true"` 为什么会影响构造方法的执行时机？

案例中最值得观察的不是 `save()` 方法，而是构造方法中的输出：

```java
public BookDaoImpl() {
    System.out.println("constructor");
}
```

只要控制台出现 `constructor`，就说明 Spring 已经真正创建了 `BookDaoImpl` 对象；如果没有出现，则可能只是加载了 Bean 定义，还没有实例化对象。

## 二、工程结构

```text
spring_10_container
├── pom.xml
└── src/main
    ├── java/com/spring
    │   ├── App.java                         ApplicationContext 示例
    │   ├── AppForBeanFactory.java           BeanFactory 示例
    │   └── dao
    │       ├── BookDao.java                 DAO 接口
    │       └── impl/BookDaoImpl.java        DAO 实现类
    └── resources
        └── applicationContext.xml           Spring XML 配置
```

各文件的职责非常清晰：

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | 引入 Spring 容器所需依赖，指定 Java 8 编译级别 |
| `applicationContext.xml` | 告诉 Spring 要管理哪个类以及如何创建它 |
| `BookDao` | 定义业务能力，降低调用方与具体实现的耦合 |
| `BookDaoImpl` | 被 Spring 创建和管理的具体对象 |
| `App` | 演示 `ApplicationContext` 的创建及 Bean 获取方式 |
| `AppForBeanFactory` | 演示较底层的 `BeanFactory` 容器 |

## 三、依赖配置解析

`pom.xml` 中的核心依赖是：

```xml
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-context</artifactId>
  <version>5.2.10.RELEASE</version>
</dependency>
```

`spring-context` 提供 `ApplicationContext`、`ClassPathXmlApplicationContext` 等上下文功能，并传递引入 `spring-beans`、`spring-core`、`spring-expression` 等基础模块。因此，本案例不需要再逐个声明这些模块。

```xml
<properties>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

这里表示源码和生成的字节码都以 Java 8 为目标，项目源文件编码为 UTF-8。它不是 Spring 配置，只是 Maven 的编译配置。

## 四、业务类为什么要分成接口和实现类

接口只声明能力：

```java
public interface BookDao {
    public void save();
}
```

实现类提供具体行为：

```java
public class BookDaoImpl implements BookDao {
    public BookDaoImpl() {
        System.out.println("constructor");
    }

    public void save() {
        System.out.println("book dao save ...");
    }
}
```

调用方通常面向 `BookDao` 接口编程，而不是直接依赖 `BookDaoImpl`。将来替换实现类时，调用代码可以尽量保持不变。Spring 容器负责根据配置创建具体实现，再把它以接口类型交给调用方。

构造方法和 `save()` 的输出含义不同：

| 输出 | 触发时机 | 能证明什么 |
| --- | --- | --- |
| `constructor` | `new BookDaoImpl()` 被间接执行时 | Bean 对象已经被创建 |
| `book dao save ...` | 调用 `bookDao.save()` 时 | 已获取 Bean 并执行了业务方法 |

## 五、XML Bean 配置详解

```xml
<bean id="bookDao"
      class="com.spring.dao.impl.BookDaoImpl"
      lazy-init="true"/>
```

三个配置项分别表示：

| 配置项 | 含义 |
| --- | --- |
| `id="bookDao"` | Bean 在当前容器中的名称，可用于按名称查找 |
| `class="...BookDaoImpl"` | 要实例化的具体类，必须使用全限定类名 |
| `lazy-init="true"` | 延迟初始化，在第一次需要该 Bean 时才创建对象 |

没有显式配置 `scope`，所以它的默认作用域是 `singleton`。这里的单例表示：**在同一个 Spring 容器中，同一个 Bean 定义通常只对应一个对象**，不是 JVM 全局只有一个对象。如果创建两个独立的容器，每个容器都可以拥有自己的 `bookDao` 实例。

还要区分两个动作：

```text
读取 <bean> 配置 → 注册 BeanDefinition → 创建 Bean 实例
        容器启动时完成                可能稍后才发生
```

`lazy-init="true"` 推迟的是最后的“创建 Bean 实例”，并不会阻止 Spring 读取和注册 Bean 定义。

## 六、`ApplicationContext` 加载过程

`App.java` 的有效代码是：

```java
ApplicationContext ctx =
        new ClassPathXmlApplicationContext("applicationContext.xml");
```

可以把这一行理解为以下流程：

```text
创建 ClassPathXmlApplicationContext
              ↓
从运行时 classpath 查找 applicationContext.xml
              ↓
解析 XML 中的 <bean>
              ↓
把 bookDao 注册为 BeanDefinition
              ↓
完成容器刷新
              ↓
因 lazy-init="true"，暂不创建 BookDaoImpl
```

因此，直接运行当前版本的 `App` 时，控制台没有输出是正常现象。此时容器已经创建成功，`bookDao` 的定义也已经注册，只是没有代码调用 `getBean()`，所以构造方法尚未执行。

### `ClassPathXmlApplicationContext`

```java
new ClassPathXmlApplicationContext("applicationContext.xml")
```

它按**类路径**查找资源。Maven 构建时会把：

```text
src/main/resources/applicationContext.xml
```

复制到：

```text
target/classes/applicationContext.xml
```

而 `target/classes` 会进入运行时 classpath，所以只写文件名即可找到配置。这种方式不依赖项目在磁盘上的绝对位置，项目换目录或打成 JAR 后仍然容易使用。

### `FileSystemXmlApplicationContext`

案例还给出了另一种加载方式：

```java
ApplicationContext ctx = new FileSystemXmlApplicationContext(
        "D:\\workspace\\spring\\spring_10_container\\src\\main\\resources\\applicationContext.xml"
);
```

它按**文件系统路径**加载资源。示例中的路径是特定 Windows 电脑上的绝对路径，换电脑、换目录或换操作系统后通常就会失效，必须改成当前环境中的真实路径。

两者的核心区别如下：

| 对比项 | `ClassPathXmlApplicationContext` | `FileSystemXmlApplicationContext` |
| --- | --- | --- |
| 查找位置 | 运行时 classpath | 操作系统文件系统 |
| 常见写法 | `applicationContext.xml` | 绝对路径或文件系统相对路径 |
| 可移植性 | 较好 | 较依赖部署目录 |
| 常见场景 | 配置随应用一起打包 | 配置放在应用外部并独立维护 |

本案例的配置位于 `src/main/resources`，优先使用类路径加载更合适。

## 七、三种 `getBean()` 写法

`App.java` 中准备了三种常见写法，只是当前被注释了。

### 1. 只按 Bean 名称获取

```java
BookDao bookDao = (BookDao) ctx.getBean("bookDao");
```

Spring 根据 XML 中的 `id="bookDao"` 查找对象。`getBean(String)` 的返回类型是 `Object`，所以调用方需要手动强制类型转换。

优点是定位明确；缺点是字符串写错通常要到运行时才能发现，而且手动强转存在类型错误风险。

### 2. 按名称和类型获取

```java
BookDao bookDao = ctx.getBean("bookDao", BookDao.class);
```

这种方式既指定名称，又要求结果必须兼容 `BookDao` 类型，不需要手动强转，通常比第一种更安全。名称不存在会查找失败；Bean 实际类型与指定类型不兼容也会明确报错。

### 3. 只按类型获取

```java
BookDao bookDao = ctx.getBean(BookDao.class);
```

Spring 查找类型兼容 `BookDao` 的 Bean。当前容器里只有一个这样的 Bean，因此可以正常获取。

如果将来同时配置两个 `BookDao` 实现，Spring 无法仅凭类型判断要返回哪一个，会抛出 `NoUniqueBeanDefinitionException`。此时可以改用“名称 + 类型”方式，或通过其他机制指定首选 Bean。

### 三种方式对比

| 写法 | 查找依据 | 是否强转 | 主要注意点 |
| --- | --- | --- | --- |
| `getBean("bookDao")` | 名称 | 需要 | 字符串和强转都可能出错 |
| `getBean("bookDao", BookDao.class)` | 名称 + 类型 | 不需要 | 定位清晰，类型也会校验 |
| `getBean(BookDao.class)` | 类型 | 不需要 | 同类型 Bean 必须唯一 |

无论采用哪一种写法，第一次成功获取 `bookDao` 都会触发延迟 Bean 的创建。随后执行：

```java
bookDao.save();
```

预期输出为：

```text
constructor
book dao save ...
```

顺序不能颠倒：Spring 必须先调用构造方法得到对象，之后才能调用对象的 `save()` 方法。

## 八、`BeanFactory` 示例详解

`AppForBeanFactory.java` 的核心代码是：

```java
Resource resources = new ClassPathResource("applicationContext.xml");
BeanFactory bf = new XmlBeanFactory(resources);
```

它分成两步：

1. `ClassPathResource` 把类路径中的 XML 包装成 Spring 的 `Resource` 资源对象；
2. `XmlBeanFactory` 读取该资源、解析 Bean 定义并提供 `getBean()` 能力。

当前的获取代码也被注释了：

```java
// BookDao bookDao = bf.getBean(BookDao.class);
// bookDao.save();
```

所以直接运行当前版本同样没有输出。取消这两行注释后，第一次 `getBean()` 会创建 `BookDaoImpl`，之后执行 `save()`。

### 为什么说 `BeanFactory` 更基础

`BeanFactory` 是 Spring IoC 容器的基础接口，核心职责是保存 Bean 定义、创建 Bean、装配依赖和返回 Bean。`ApplicationContext` 在 BeanFactory 能力之上又整合了更多应用级功能，例如：

- 国际化消息；
- 应用事件发布；
- 更强的资源与资源模式解析；
- 更方便地集成 Bean 后置处理器等 Spring 基础设施。

可以用下面的关系理解：

```text
BeanFactory
└── 提供 IoC 容器的核心能力

ApplicationContext
├── 具备 BeanFactory 的 Bean 管理能力
├── 事件机制
├── 国际化
└── 资源加载等应用级能力
```

日常开发通常使用 `ApplicationContext`。案例中的 `XmlBeanFactory` 已被 Spring 标记为过时 API，适合用来理解底层概念，不建议在新的业务代码中继续使用。若确实要手工使用底层 BeanFactory，现代写法可使用 `DefaultListableBeanFactory` 配合 `XmlBeanDefinitionReader`；大多数项目直接选择 `ClassPathXmlApplicationContext` 或注解配置即可。

## 九、最容易混淆的初始化差异

常见教材会把二者概括为：

- `ApplicationContext`：容器启动时创建非延迟的单例 Bean；
- 基础 `BeanFactory`：通常在第一次 `getBean()` 时创建 Bean。

这个结论有前提，不能忽略 Bean 自己的延迟配置。本案例明确写了：

```xml
lazy-init="true"
```

因此，`ApplicationContext` 也不会在容器启动时创建 `bookDao`。本案例的实际表现如下：

| 容器与配置 | 创建容器时 | 第一次 `getBean()` 时 |
| --- | --- | --- |
| `ApplicationContext` + `lazy-init="true"` | 只注册定义，不执行构造方法 | 执行构造方法 |
| `ApplicationContext` + `lazy-init="false"` | 通常直接创建单例，执行构造方法 | 返回已创建的对象 |
| 本例 `BeanFactory` + `lazy-init="true"` | 只注册定义，不执行构造方法 | 执行构造方法 |

所以，观察容器初始化时机时应同时考虑三个因素：

1. 使用的是哪类容器；
2. Bean 是否配置为延迟初始化；
3. Bean 的作用域是不是单例。

## 十、完整运行链路

如果取消 `App.java` 中以下代码的注释：

```java
BookDao bookDao = ctx.getBean("bookDao", BookDao.class);
bookDao.save();
```

完整链路是：

```text
main() 启动
  ↓
创建 ApplicationContext
  ↓
读取 applicationContext.xml
  ↓
注册 bookDao 的 BeanDefinition
  ↓
发现 lazy-init="true"，先不实例化
  ↓
调用 ctx.getBean(...)
  ↓
根据 BeanDefinition 反射调用 BookDaoImpl 构造方法
  ↓
输出 constructor
  ↓
把创建完成的单例对象放入容器缓存
  ↓
返回 BookDao 接口引用
  ↓
调用 save()
  ↓
输出 book dao save ...
```

如果再次调用 `ctx.getBean(BookDao.class)`，在默认 `singleton` 作用域下会返回容器缓存中的同一个对象，不会再次输出 `constructor`。

## 十一、建议的观察实验

### 实验 1：验证延迟初始化

保持 `lazy-init="true"`，先不调用 `getBean()`：没有 `constructor` 输出。然后取消 `getBean()` 的注释：第一次获取时出现 `constructor`。

### 实验 2：关闭延迟初始化

把配置改为：

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>
```

运行 `App`，即使不调用 `getBean()`，创建 `ApplicationContext` 时通常也会输出 `constructor`，因为默认的非延迟单例会在容器刷新阶段预实例化。

### 实验 3：验证单例

```java
BookDao first = ctx.getBean(BookDao.class);
BookDao second = ctx.getBean(BookDao.class);
System.out.println(first == second);
```

预期输出 `true`，并且构造方法只执行一次。

### 实验 4：制造按类型查找冲突

再注册一个实现 `BookDao` 的 Bean，然后调用：

```java
ctx.getBean(BookDao.class);
```

此时会因同类型候选 Bean 不唯一而报错。改用 `ctx.getBean("bookDao", BookDao.class)` 即可明确指定目标。

## 十二、常见问题

### 1. 为什么直接运行程序什么都不打印？

因为 XML 配置了 `lazy-init="true"`，而两个入口类中的 `getBean()` 都被注释。容器已经读取并注册配置，但没有创建 `BookDaoImpl`，也没有执行 `save()`。

### 2. `id="bookDao"` 是 Java 变量名吗？

不是。它是 Bean 在 Spring 容器中的名称，与 Java 局部变量名没有绑定关系。下面的变量完全可以改名：

```java
BookDao dao = ctx.getBean("bookDao", BookDao.class);
```

### 3. `class` 为什么写实现类而不是接口？

接口不能直接实例化。Spring 需要知道具体创建哪个类，因此 XML 中写 `BookDaoImpl`；调用方仍然可以用 `BookDao` 接口接收结果。

### 4. 为什么配置文件放在 `resources` 中？

Maven 会把这里的资源复制到构建输出目录并放入 classpath，便于 `ClassPathXmlApplicationContext` 查找，也便于随应用一起打包。

### 5. 为什么不推荐绝对路径？

绝对路径绑定某台机器的目录结构，项目移动后容易失效。配置随应用发布时优先使用 classpath；只有确实需要外置配置时再使用文件系统路径。

### 6. 使用完容器要关闭吗？

具有生命周期和资源清理需求的应用应关闭可关闭的上下文，例如使用 `ClassPathXmlApplicationContext` 的 `close()`，从而触发 Bean 销毁回调并释放资源。本案例中的 Bean 没有持有外部资源，所以不关闭也不影响这个最小演示，但形成关闭容器的意识更稳妥。

## 十三、核心总结

1. **Spring 容器管理的不是一段 XML，而是 XML 描述的 Bean 对象及其生命周期。**
2. **加载 Bean 定义和创建 Bean 实例是两个不同阶段。**
3. `ClassPathXmlApplicationContext` 从 classpath 加载配置，适合本案例这种放在 `resources` 中的 XML。
4. `FileSystemXmlApplicationContext` 从文件系统加载配置，路径依赖更强，适合外置配置。
5. `getBean()` 可以按名称、名称加类型、或类型查找；按类型查找要求候选 Bean 唯一。
6. `ApplicationContext` 是日常开发更常用的容器，具备 BeanFactory 的核心能力并提供更多应用级功能。
7. `XmlBeanFactory` 是过时 API，本案例主要用它帮助理解 BeanFactory 的基础定位。
8. 当前配置的 `lazy-init="true"` 会让 `bookDao` 在第一次被获取时才实例化。
9. 默认 `singleton` 作用域保证同一容器中重复获取该 Bean 时通常得到同一个对象。
10. 当前程序没有输出是符合设计的：Bean 定义已经注册，但实例化与业务调用都未被触发。

一句话概括本案例：

> Spring 先把 XML 中的 `<bean>` 解析成对象定义放进容器，再根据容器类型、Bean 配置和实际获取行为决定何时创建对象；应用代码只需向容器索取所需接口，而不再自己直接 `new` 实现类。

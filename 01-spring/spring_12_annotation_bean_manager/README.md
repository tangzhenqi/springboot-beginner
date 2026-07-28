# spring_12_annotation_bean_manager —— 注解开发 Bean 管理

## 一、案例目标

上一个案例 [`spring_11_annotation_bean`](../spring_11_annotation_bean) 解决了"**用注解定义 Bean**"，本案例解决"**用注解管理 Bean**"——即之前在 XML 里用 `scope`、`init-method`、`destroy-method` 属性配置的东西，全部换成注解。

| XML 写法 | 注解写法 |
| --- | --- |
| `<bean scope="singleton">` | `@Scope("singleton")` |
| `<bean init-method="init">` | `@PostConstruct` |
| `<bean destroy-method="destroy">` | `@PreDestroy` |

## 二、工程结构

```
spring_12_annotation_bean_manager
├── pom.xml                       spring-context + javax.annotation-api
└── src/main/java/com/spring
    ├── App.java                          启动类，验证作用范围与生命周期
    ├── config/SpringConfig.java          @Configuration + @ComponentScan
    ├── dao/BookDao.java                  接口
    └── dao/impl/BookDaoImpl.java         @Repository + @Scope + @PostConstruct + @PreDestroy
```

本案例已经是**纯注解开发**，没有 `applicationContext.xml`。

## 三、核心知识点

### 1. `@Scope` —— 设置 Bean 的作用范围

```java
@Repository
@Scope("singleton")     // 单例（默认值），整个容器只造一个对象
public class BookDaoImpl implements BookDao { }
```

| 取值 | 含义 | 创建时机 |
| --- | --- | --- |
| `singleton` | 单例，**默认值** | 容器初始化时就创建 |
| `prototype` | 非单例，每次 `getBean()` 都造一个新对象 | 每次获取时创建 |

> 还有 `request` / `session` 等，只在 Web 环境下有效，此处不涉及。

**验证方式**：`App` 中连续两次 `ctx.getBean(BookDao.class)` 并打印。

- `@Scope("singleton")` → 两次打印的地址**相同**
- 改成 `@Scope("prototype")` → 两次打印的地址**不同**

### 2. `@PostConstruct` / `@PreDestroy` —— 设置生命周期方法

```java
@PostConstruct          // 构造方法执行、属性注入完成后调用
public void init() {
    System.out.println("init ...");
}

@PreDestroy             // 容器关闭、bean 销毁前调用
public void destroy() {
    System.out.println("destroy ...");
}
```

注意这两个注解**不是 Spring 提供的**，属于 JSR-250 规范（`javax.annotation` 包），Spring 只是负责识别并调用。

**执行顺序**：构造方法 → 依赖注入 → `@PostConstruct` → 正常使用 → `@PreDestroy` → 对象销毁。

### 3. 两个注解的一个重要前提：只对单例生效

`@PreDestroy` 想被执行，必须满足两个条件：

1. Bean 是 `singleton` 的。`prototype` 的 Bean 容器交出去就不再管理，**销毁方法永远不会被调用**。
2. 容器**正常关闭**。所以 `App` 中用的是 `AnnotationConfigApplicationContext`（而不是接口 `ApplicationContext`），因为只有具体实现类才有 `close()` 方法：

```java
AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
...
ctx.close();            // 不调用 close()，就看不到 destroy 输出
```

> 也可以用 `ctx.registerShutdownHook()` 注册钩子，让 JVM 退出时自动关闭容器，效果相同且不用手动控制关闭时机。

### 4. pom 中为什么要额外引 `javax.annotation-api`

```xml
<dependency>
  <groupId>javax.annotation</groupId>
  <artifactId>javax.annotation-api</artifactId>
  <version>1.3.2</version>
</dependency>
```

JDK 8 自带 `javax.annotation` 包，直接用即可；但从 **JDK 11 起该模块被移除**，不引入这个依赖就会编译报错「找不到 `javax.annotation.PostConstruct`」。pom 里的注释说明的就是这件事。

## 四、运行方式

直接运行 `com.spring.App` 的 `main` 方法。

**当前配置（singleton）的输出：**

```
init ...
com.spring.dao.impl.BookDaoImpl@2b71fc7e
com.spring.dao.impl.BookDaoImpl@2b71fc7e     ← 地址相同，同一个对象
destroy ...
```

可以看到：`init` 在**获取 Bean 之前**就打印了（容器初始化时已创建），`destroy` 在 `ctx.close()` 后打印。

**把 `@Scope` 改成 `prototype` 后的输出：**

```
com.spring.dao.impl.BookDaoImpl@2b71fc7e
init ...
com.spring.dao.impl.BookDaoImpl@5ce65a89     ← 地址不同，两个对象
init ...
```

变化有三处，建议动手改一遍对比：
- 两次地址不同 → 造了两个对象
- `init` 打印了两次，且时机推迟到每次 `getBean()` 时
- **`destroy` 完全没有打印** → 印证了上面第 3 点

## 五、小结

```
XML  <bean scope="prototype">              →  @Scope("prototype")
XML  <bean init-method="init">             →  @PostConstruct
XML  <bean destroy-method="destroy">       →  @PreDestroy
```

到此为止，XML 中关于 Bean 的**定义**和**管理**已经全部可以用注解替代。唯一还没解决的是**依赖注入**——`BookService` 里的 `BookDao` 谁来赋值？这就是下一个案例 [`spring_13_annotation_di`](../spring_13_annotation_di) 中 `@Autowired` 要做的事。

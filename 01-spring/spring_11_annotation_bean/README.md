# spring_11_annotation_bean —— 注解开发定义 Bean

## 一、案例目标

摆脱在 XML 中一个个写 `<bean>` 标签的方式，改用**注解**声明 Bean，并进一步过渡到**纯注解开发**（连 XML 配置文件都不要）。

本案例同时保留了两条路线，方便对照：

| 路线 | 配置来源 | 容器实现类 | 启动类 |
| --- | --- | --- | --- |
| 半注解（XML + 注解） | `applicationContext.xml` | `ClassPathXmlApplicationContext` | `com.spring.App` |
| 纯注解 | `SpringConfig` 配置类 | `AnnotationConfigApplicationContext` | `AppForAnnotation` |

## 二、工程结构

```
spring_11_annotation_bean
├── pom.xml                       仅依赖 spring-context 5.2.10.RELEASE
└── src/main
    ├── java
    │   ├── AppForAnnotation.java             纯注解启动类（默认包）
    │   └── com/spring
    │       ├── App.java                      XML + 注解启动类
    │       ├── config/SpringConfig.java      Spring 配置类
    │       ├── dao/BookDao.java              接口
    │       ├── dao/impl/BookDaoImpl.java     @Repository("bookDao")
    │       ├── service/BookService.java      接口
    │       └── service/impl/BookServiceImpl.java  @Service
    └── resources/applicationContext.xml      仅一行组件扫描
```

## 三、核心知识点

### 1. 用注解定义 Bean

`@Component` 是定义 Bean 的基础注解，写在类上，Spring 扫描到就把它造成一个 Bean。

```java
@Component("bookDao")   // 括号里是 bean 的 id
public class BookDaoImpl implements BookDao { }
```

- **写了名字**：`@Component("bookDao")` → bean id 就是 `bookDao`，可用 `ctx.getBean("bookDao")` 取。
- **没写名字**：`@Component` → bean id 默认是**类名首字母小写**（`bookServiceImpl`），此时更适合按类型取：`ctx.getBean(BookService.class)`。

### 2. 三个衍生注解

为了让代码语义更清晰，Spring 提供了 `@Component` 的三个衍生注解，**功能完全一样**，只是按分层区分用途：

| 注解 | 用于 | 本案例中 |
| --- | --- | --- |
| `@Repository` | 数据层（Dao） | `BookDaoImpl` |
| `@Service` | 业务层（Service） | `BookServiceImpl` |
| `@Controller` | 表现层（Controller） | 本案例未用到 |
| `@Component` | 不好归类的其他组件 | — |

源码中被注释掉的 `//@Component` 就是演示"衍生注解可以替换 @Component"。

### 3. 组件扫描：告诉 Spring 去哪儿找注解

光加注解没用，必须开启扫描。

**XML 方式**（`applicationContext.xml`）：

```xml
<context:component-scan base-package="com.spring"/>
```

> 注意：使用 `context` 命名空间需要在 `<beans>` 上引入 `xmlns:context` 和对应的 xsd，否则标签报错。

**注解方式**（`SpringConfig.java`）：

```java
@Configuration                                       // 声明这是一个 Spring 配置类
@ComponentScan({"com.spring.service","com.spring.dao"})  // 多个包写成字符串数组
public class SpringConfig { }
```

- `@Configuration`：让这个普通类具备 XML 配置文件的地位。
- `@ComponentScan`：等价于 `<context:component-scan>`；单个包可写 `@ComponentScan("com.spring")`，多个包用 `{}` 数组。
- 扫描是**递归**的，扫 `com.spring.dao` 会连 `com.spring.dao.impl` 一起扫到。

### 4. 加载配置类初始化容器

```java
// XML：加载配置文件
ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");

// 纯注解：加载配置类
ApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
```

之后取 Bean 的两种方式一致：

```java
BookDao bookDao = (BookDao) ctx.getBean("bookDao");   // 按名称，需强转
BookService bookService = ctx.getBean(BookService.class); // 按类型，无需强转（推荐）
```

## 四、运行方式

在模块目录下执行任一启动类的 `main` 方法（IDEA 中直接右键 Run）：

- 运行 `AppForAnnotation` → 走纯注解路线
- 运行 `com.spring.App` → 走 XML + 注解路线

两者输出形如：

```
com.spring.dao.impl.BookDaoImpl@2b71fc7e
com.spring.service.impl.BookServiceImpl@5ce65a89
```

打印出对象地址即说明 Bean 已被容器成功创建和管理。

## 五、注意事项

1. **本案例只定义 Bean，没有做依赖注入。** `BookServiceImpl` 里的 `bookDao` 字段虽然有 setter，但既没有 XML 的 `<property>`，也没有 `@Autowired`，所以它**始终是 null**。如果调用 `bookService.save()` 会抛 `NullPointerException`——这正是下一个案例 `spring_13_annotation_di` 用 `@Autowired` 要解决的问题。
2. `AppForAnnotation` 放在**默认包**（无 package 声明），只是教学演示图省事，实际项目中所有类都应放进具体包内。
3. `SpringConfig` 的扫描路径写的是 `com.spring.service` 和 `com.spring.dao`，没有包含 `com.spring` 根包，所以 `com.spring.App` 类本身不会被扫描——这没有影响，因为它没加任何 Bean 注解。

## 六、小结

```
XML 配置 <bean>              →  注解 @Component / @Repository / @Service / @Controller
XML 配置文件 applicationContext.xml  →  配置类 @Configuration
<context:component-scan>     →  @ComponentScan
ClassPathXmlApplicationContext  →  AnnotationConfigApplicationContext
```

一句话：**注解定义 Bean + 配置类替代 XML = 纯注解开发**，这也是后面 SpringBoot 全自动配置的基础。

# spring_13_annotation_di —— 注解开发依赖注入

## 一、案例目标

补上注解开发的最后一块拼图：**依赖注入（DI）**。

前两个案例遗留的问题——`BookServiceImpl` 里的 `bookDao` 字段一直是 null——在这里被解决。同时演示了简单类型注入和读取 properties 配置文件。

| XML 写法 | 注解写法 |
| --- | --- |
| `<property name="bookDao" ref="bookDao"/>` | `@Autowired` |
| 指定注入哪个同类型的 bean | `@Qualifier("bookDao")` |
| `<property name="name" value="itheima888"/>` | `@Value("${name}")` |
| `<context:property-placeholder location="jdbc.properties"/>` | `@PropertySource("jdbc.properties")` |

## 二、工程结构

```
spring_13_annotation_di
├── pom.xml
└── src/main
    ├── java/com/spring
    │   ├── App.java                          启动类
    │   ├── config/SpringConfig.java          @Configuration + @ComponentScan + @PropertySource
    │   ├── dao/BookDao.java                  接口
    │   ├── dao/impl/BookDaoImpl.java         @Repository("bookDao")，含 @Value 注入
    │   ├── dao/impl/BookDaoImpl2.java        @Repository("bookDao2")，同接口的第二个实现
    │   ├── service/BookService.java          接口
    │   └── service/impl/BookServiceImpl.java @Autowired + @Qualifier 注入
    └── resources/jdbc.properties             name=itheima888
```

注意 `BookDao` 故意准备了**两个实现类**，这正是本案例要演示 `@Qualifier` 的原因。

## 三、核心知识点

### 1. `@Autowired` —— 注入引用类型

```java
@Service
public class BookServiceImpl implements BookService {
    @Autowired
    private BookDao bookDao;        // 没有 setter，也能注入
    ...
}
```

几个要点：

- **不需要提供 setter 方法**。`@Autowired` 底层通过暴力反射（`Field.setAccessible(true)`）直接给私有字段赋值，所以上一个案例里那种 `setBookDao()` 可以删掉。
- **默认按类型（byType）装配**。Spring 在容器里找 `BookDao` 类型的 Bean 注入进来。
- **默认要求必须找到**。找不到会抛 `NoSuchBeanDefinitionException`。允许为空时写 `@Autowired(required = false)`。

### 2. `@Qualifier` —— 按名称指定注入哪一个

按类型装配有个天然问题：**当同一个类型有多个 Bean 时，Spring 不知道选哪个**。

本案例中 `BookDaoImpl` 和 `BookDaoImpl2` 都实现了 `BookDao`，只写 `@Autowired` 会直接报错：

```
NoUniqueBeanDefinitionException: expected single matching bean but found 2: bookDao,bookDao2
```

此时用 `@Qualifier` 指定 Bean 的名称：

```java
@Autowired
@Qualifier("bookDao")       // 明确要 id 为 bookDao 的那个
private BookDao bookDao;
```

> **`@Qualifier` 不能独立使用**，必须配合 `@Autowired` 一起写。
>
> 补充：Spring 按类型找到多个时，其实还有一条兜底规则——会拿**字段名**去匹配 Bean 的 id。所以本例即使不写 `@Qualifier`，字段名 `bookDao` 恰好等于 Bean id `bookDao`，也能注入成功。但显式写出来意图更清晰，不依赖字段名。

### 3. `@Value` —— 注入简单类型

```java
@Repository("bookDao")
public class BookDaoImpl implements BookDao {
    @Value("${name}")
    private String name;
    ...
}
```

- 同样**不需要 setter**。
- 可以直接写字面值：`@Value("itheima")`；
- 也可以写 `${...}` 占位符，从 properties 文件中读取——但前提是配置文件已经被加载。

### 4. `@PropertySource` —— 加载 properties 配置文件

```java
@Configuration
@ComponentScan("com.spring")
@PropertySource({"jdbc.properties"})    // 多个文件写成数组
public class SpringConfig { }
```

- 路径默认从 **classpath 根目录**（即 `src/main/resources`）开始找，也可以显式写成 `classpath:jdbc.properties`。
- 多个文件用数组：`@PropertySource({"jdbc.properties","msg.properties"})`。
- **不支持通配符** `*.properties`，写了会直接运行报错，必须逐个列出。

三者的配合关系是一条链：

```
@PropertySource 加载文件  →  属性进入 Spring 环境  →  @Value("${name}") 取出并注入字段
```

### 5. 深入：`${name}` 的值到底是怎么取到的

这段代码里出现了**两个 `name`**，很容易混为一谈：

```java
@Value("${name}")
private String name;
```

| 位置 | 含义 |
| --- | --- |
| `${name}` 里的 name | **占位符的 key**，拿去配置文件里查的键名 |
| `private String name` | 字段名，只是接收结果的容器，**与查找过程无关** |

完整链路分四步：

1. **加载**：解析配置类时（`ConfigurationClassParser`），`@PropertySource` 把 `jdbc.properties` 读成一个 `PropertySource` 对象，塞进 `Environment` 的属性源列表。
2. **触发**：`AutowiredAnnotationBeanPostProcessor` 给 `bookDao` 填充属性时发现字段上有 `@Value`，拿到字符串 `"${name}"`，交给容器的 embedded value resolver——默认实现就是 `strVal -> getEnvironment().resolvePlaceholders(strVal)`。
3. **查找**：`Environment` 按属性源顺序查 key = `name`，命中 `jdbc.properties`，返回 `"itheima888"`。
4. **写入**：经 `TypeConverter` 转成字段类型，再用 `Field.setAccessible(true)` 直接赋值——所以不需要 setter，它绕过了 JavaBean 内省，和 XML 的 `<property name="">` 走的是完全不同的路径。

> **对比记忆**：XML 的 `<property name="xxx"/>` 中，`name` 是**从 setter 方法名推导的属性名**（`setDatabaseName` → `databaseName`），没有 setter 就注入失败；而 `@Value` / `@Autowired` 靠反射直接写字段，与 setter 无关。参见 [`spring_05_di_set`](../spring_05_di_set)。

**可以动手验证：**

- 把字段名 `name` 改成 `dbName`，`@Value("${name}")` 不动 → 依然注入成功，证明查找只认 `${}` 里的 key；
- 把 `${name}` 改成 `${username}` → 启动即报错 `Could not resolve placeholder 'username'`。

### 6. 配置文件加载后的数据结构

properties 加载后并不会变成某个业务类，而是一个通用的键值容器：

```java
public abstract class PropertySource<T> {
    private final String name;   // "class path resource [jdbc.properties]"
    protected final T source;    // 这里 T = Map<String, Object>，内容是 {"name" -> "itheima888"}
}
```

就单个文件而言，存的确实就是**一对对 key-value**。两个细节：

- **值全是 String**。properties 没有类型概念，`connectionNum=100` 读进来也是字符串 `"100"`，转成 `int` 是注入那一刻由 `TypeConverter` 完成的，不是加载时。
- **key 是扁平字符串**，没有嵌套结构。`jdbc.url=xxx` 里的点只是名字的一部分，不代表层级。

但 `Environment` 持有的不是一个 Map，而是 `MutablePropertySources`——一个**有序列表**：

```
[0] systemProperties      ← JVM -D 参数
[1] systemEnvironment     ← 操作系统环境变量
[2] jdbc.properties       ← @PropertySource 用 addLast 追加在最后
```

`resolvePlaceholders("${name}")` 是**从上往下逐个询问，第一个答"有"的就返回**，后面的不再看。

> ⚠️ 由此带来一个坑：`@PropertySource` 的优先级**低于**系统属性和环境变量。本例的 key 直接叫 `name`，如果 JVM 系统属性或操作系统环境变量里恰好也存在 `name`（Windows 上 `SystemEnvironmentPropertySource` 还会做 `name` → `NAME` 的模糊匹配），取到的就不是 `itheima888` 了。实际项目中应给 key 加前缀，如 `jdbc.name`、`app.name`。

至于把配置**绑定成强类型对象**（`@ConfigurationProperties`），是构建在上述 key-value 结构之上的一层，属于 Spring Boot 的内容，见 [`springboot_03_read_data`](../../04-springboot/springboot_03_read_data)。

## 四、运行方式

运行 `com.spring.App` 的 `main` 方法：

```java
AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
BookService bookService = ctx.getBean(BookService.class);
bookService.save();
```

预期输出：

```
book service save ...
book dao save ...itheima888
```

这两行分别验证了两件事：

1. `bookDao.save()` 能正常调用 → `@Autowired` 注入成功，字段不再是 null；
2. 末尾拼上了 `itheima888` → `@PropertySource` + `@Value` 从 `jdbc.properties` 取值成功。

**建议动手验证的两个改动：**

- 把 `@Qualifier("bookDao")` 改成 `@Qualifier("bookDao2")` → 输出变成 `book dao save ...2`；
- 把 `@Qualifier` 整行删掉，同时把字段名改成 `bookDaoX` → 触发 `NoUniqueBeanDefinitionException`，直观感受为什么需要 `@Qualifier`。

## 五、小结

```
XML  <property ref="...">                        →  @Autowired（+ @Qualifier 指定名称）
XML  <property value="...">                      →  @Value
XML  <context:property-placeholder location=""/> →  @PropertySource
```

至此，Bean 的**定义**（[11](../spring_11_annotation_bean)）、**管理**（[12](../spring_12_annotation_bean_manager)）、**依赖注入**（本案例）三件事全部完成了注解化，XML 配置文件可以彻底删除。

不过还有一个场景注解搞不定：**第三方类库的类**（比如 Druid 的 `DataSource`）源码不是自己的，没法在上面加 `@Component`。这就是下一个案例 [`spring_14_annotation_third_bean_manager`](../spring_14_annotation_third_bean_manager) 中 `@Bean` 要解决的问题。

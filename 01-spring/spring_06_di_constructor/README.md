# spring_06_di_constructor —— 构造器方式依赖注入

## 一、案例目标

上个案例 `spring_05_di_set` 用 **setter 注入**装配依赖，本案例换成 **构造器注入**：依赖不再通过 set 方法赋值，而是**在对象创建的那一刻就通过构造方法参数传进去**。

核心区别：

| | setter 注入 | 构造器注入 |
| --- | --- | --- |
| 依赖何时具备 | 对象创建后 | **对象创建时** |
| 类中需要 | set 方法 | 有参构造方法 |
| XML 标签 | `<property>` | `<constructor-arg>` |
| 漏配的后果 | 容器正常启动，运行时 NPE | **容器启动直接报错** |

本案例的重点在于：`<constructor-arg>` 有 **name / type / index** 三种参数匹配方式，配置文件里三套写法都保留了，只放开了最后一套。

## 二、工程结构

```
spring_06_di_constructor
├── pom.xml                       仅依赖 spring-context 5.2.10.RELEASE
└── src/main
    ├── java/com/spring
    │   ├── AppForDIConstructor.java                启动类
    │   ├── dao/BookDao.java
    │   ├── dao/UserDao.java
    │   ├── dao/impl/BookDaoImpl.java               有参构造：(String, int)
    │   ├── dao/impl/UserDaoImpl.java               无参
    │   ├── service/BookService.java
    │   └── service/impl/BookServiceImpl.java       有参构造：(BookDao, UserDao)
    └── resources/applicationContext.xml            三套写法，仅第三套生效
```

对比上个案例：**所有的 set 方法都没了，换成了有参构造方法。**

```java
public class BookDaoImpl implements BookDao {
    private String databaseName;
    private int connectionNum;

    public BookDaoImpl(String databaseName, int connectionNum) {   // 没有无参构造
        this.databaseName = databaseName;
        this.connectionNum = connectionNum;
    }
}
```

> 注意：一旦写了有参构造，编译器就**不再自动生成无参构造**。这意味着这个类**不能**再用 `spring_03` 里的"方式一（构造方法实例化）"那样只写 `<bean class="..."/>`，必须配全构造参数，否则报 `NoSuchMethodException: <init>()`。

## 三、三种参数匹配方式

### 写法一：按参数名称匹配（`name`）—— 标准写法

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl">
    <!--根据构造方法参数名称注入-->
    <constructor-arg name="connectionNum" value="10"/>
    <constructor-arg name="databaseName" value="mysql"/>
</bean>

<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <constructor-arg name="userDao" ref="userDao"/>
    <constructor-arg name="bookDao" ref="bookDao"/>
</bean>
```

- `name` 写的是**构造方法的形参名**。
- 按名字匹配，所以**书写顺序无所谓**——上面 `connectionNum` 写在前、`databaseName` 写在后，而构造方法的顺序是 `(databaseName, connectionNum)`，照样能对上。`bookService` 那组也是反着写的。
- 引用类型用 `ref`，简单类型用 `value`，这点和 setter 注入完全一致。

**缺点**：配置和**形参名强耦合**。别人重构代码把形参 `databaseName` 改成 `dbName`，XML 不同步改就崩了——而形参名在多数人眼里是"随便改都不影响"的东西。

> 还有个隐患：`name` 匹配依赖 class 文件里保留了参数名信息。如果编译时没开 `-parameters`、且 class 被压缩优化过，参数名可能丢失导致匹配失败。本项目 Maven 编译正常，不会遇到。

### 写法二：按参数类型匹配（`type`）—— 解耦形参名

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl">
    <!--根据构造方法参数类型注入-->
    <constructor-arg type="int" value="10"/>
    <constructor-arg type="java.lang.String" value="mysql"/>
</bean>
```

- 不再关心形参叫什么，只看类型，**形参改名不影响配置**。
- 基本类型直接写 `int`、`boolean`；引用类型要写**全限定名** `java.lang.String`。
- 同样与书写顺序无关。

**缺点**：**类型重复时失效**。如果构造方法是 `BookDaoImpl(String databaseName, String userName)`，两个都是 `String`，Spring 无法判断哪个配给哪个。

### 写法三：按参数位置匹配（`index`）—— 解决类型重复

这是本案例**当前生效**的配置：

```xml
<!--解决参数类型重复问题，使用位置解决参数匹配-->
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl">
    <!--根据构造方法参数位置注入-->
    <constructor-arg index="0" value="mysql"/>
    <constructor-arg index="1" value="100"/>
</bean>
```

- `index` 从 **0** 开始，对应构造方法形参的位置。
- 构造方法是 `BookDaoImpl(String databaseName, int connectionNum)`，所以 `index="0"` 是 `mysql`，`index="1"` 是 `100`——**顺序必须和构造方法严格一致**，写反了会因为类型转换失败而报错。

**缺点**：配置和**参数位置强耦合**。有人调整了构造方法的参数顺序，XML 不改就出错，而且如果两个参数类型相同，还不会报错，只是**悄悄注错了值**——这是最危险的一种。

### 三种方式对比总结

| 方式 | 耦合点 | 失效场景 | 推荐度 |
| --- | --- | --- | --- |
| `name` | 形参名 | 形参改名 | ★★★ 首选 |
| `type` | 参数类型 | 存在同类型参数 | ★★ 备选 |
| `index` | 参数位置 | 参数顺序调整 | ★ 兜底 |

**选型建议**：优先用 `name`（可读性最好）；形参名不稳定时用 `type`；只有当同类型参数重复、`type` 无法区分时才用 `index`。

## 四、运行方式

配置文件默认放开的是**写法三（index）**，直接运行 `com.spring.AppForDIConstructor` 的 `main`：

```
book service save ...
book dao save ...mysql,100
user dao save ...
```

要试另外两种写法，把对应的 XML 注释块放开、把当前生效的这套注释掉即可（三套都定义了同名 bean，**不能同时放开**）。

> 注意写法一注入的是 `connectionNum=10`，写法三是 `100`，所以切换写法后第二行输出会从 `mysql,100` 变成 `mysql,10`——正好可以用来确认当前生效的是哪套配置。

可以自己验证的点：

1. **删掉一个 `<constructor-arg>`** → **容器启动阶段**就报 `UnsatisfiedDependencyException`，而不是等到调用时才 NPE。这正是构造器注入相对 setter 注入的核心优势。
2. **把 `index="0"` 和 `index="1"` 的值对调** → 报类型转换错误，因为 `mysql` 转不成 `int`。
3. **给 `BookDaoImpl` 补一个无参构造，然后删掉所有 `<constructor-arg>`** → 容器能启动，但输出变成 `book dao save ...null,0`。

## 五、setter 注入 vs 构造器注入，到底用哪个？

| 场景 | 建议 |
| --- | --- |
| 必须的依赖（缺了就不能工作） | **构造器注入**——启动即校验，且字段可以声明为 `final` |
| 可选的依赖、有默认值的配置项 | setter 注入 |
| 依赖需要在运行期被替换 | setter 注入 |
| 存在循环依赖 | 只能 setter 注入（构造器注入会直接报错） |

Spring 官方推荐**强制依赖用构造器注入**，理由就是"启动期暴露问题"和"对象一旦创建就是完整可用的状态"。

不过在实际开发中，**XML 时代 setter 注入用得更多**（配置简单、不怕循环依赖）；到了注解时代，`@Autowired` 配合构造器注入才成为主流写法——这个变化在后面 `spring_13_annotation_di` 会看到。

## 六、小结

```
构造器注入三要素：
  ① 类中提供有参构造方法（此时无参构造不再自动生成）
  ② <constructor-arg> 配置每一个参数，一个都不能少
  ③ 三选一匹配参数：name（推荐）/ type / index

引用类型用 ref，简单类型用 value —— 与 setter 注入一致
```

下一个案例 `spring_07_di_autoware` 会引入**自动装配**，直接省掉这些手写的 `<property>` 和 `<constructor-arg>`。

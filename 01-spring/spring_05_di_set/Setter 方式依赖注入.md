# spring_05_di_set —— setter 方式依赖注入

## 一、案例目标

前面三个案例讲的都是 **IoC（控制反转）**：把对象的创建权交给容器。但光有对象还不够——`BookServiceImpl` 里要用 `BookDao`，这个 `bookDao` 字段谁来赋值？

这就是 **DI（依赖注入）** 要解决的问题：**容器不仅帮你造对象，还帮你把对象之间的依赖关系装配好。**

本案例演示 **setter 注入**，覆盖两种数据类型：

| 类型 | XML 标签属性 | 演示 |
| --- | --- | --- |
| 简单类型（基本类型 + String） | `value` | `BookDaoImpl` 的 `databaseName` / `connectionNum` |
| 引用类型（其他 bean） | `ref` | `BookServiceImpl` 的 `bookDao` / `userDao` |

## 二、工程结构

```
spring_05_di_set
├── pom.xml                       仅依赖 spring-context 5.2.10.RELEASE
└── src/main
    ├── java/com/spring
    │   ├── AppForDISet.java                        启动类
    │   ├── dao/BookDao.java
    │   ├── dao/UserDao.java
    │   ├── dao/impl/BookDaoImpl.java               被注入两个简单类型
    │   ├── dao/impl/UserDaoImpl.java
    │   ├── service/BookService.java
    │   └── service/impl/BookServiceImpl.java       被注入两个引用类型
    └── resources/applicationContext.xml            全部注入配置都在这里
```

依赖关系一目了然：

```
bookService ──ref──> bookDao ──value──> databaseName="mysql", connectionNum=100
            └─ref──> userDao
```

## 三、setter 注入引用类型

### 第一步：在类中提供 set 方法

**这是 setter 注入的硬性前提**——没有 set 方法，注入必定失败。

```java
public class BookServiceImpl implements BookService {
    private BookDao bookDao;
    private UserDao userDao;

    //setter注入需要提供要注入对象的set方法
    public void setBookDao(BookDao bookDao) {
        this.bookDao = bookDao;
    }
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }
}
```

### 第二步：在配置文件中用 `<property>` 装配

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>
<bean id="userDao" class="com.spring.dao.impl.UserDaoImpl"/>

<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <property name="bookDao" ref="bookDao"/>
    <property name="userDao" ref="userDao"/>
</bean>
```

三个属性的含义：

| 属性 | 作用 |
| --- | --- |
| `name` | 要注入的**属性名**，本质上对应 **set 方法名** |
| `ref` | 引用另一个 bean 的 id（引用类型用） |
| `value` | 直接给一个字面值（简单类型用） |

## 四、关键坑点：`name` 到底对应什么？

**`name` 对应的不是字段名，而是 set 方法名去掉 `set` 前缀、首字母小写后的结果。**

这两者绝大多数时候一致，所以容易被忽略，但一旦不一致就会出问题：

```java
private BookDao bookDao;

public void setDao(BookDao bookDao) {   // set 方法名叫 setDao
    this.bookDao = bookDao;
}
```

此时 XML 必须写 `<property name="dao" ref="bookDao"/>`，写成 `name="bookDao"` 会报错：

```
Bean property 'bookDao' is not writable or has an invalid setter method
```

> 记住这条报错信息，它是 setter 注入最高频的错误，原因永远是：**set 方法不存在，或者名字对不上。**

## 五、setter 注入简单类型

简单类型指基本数据类型（`int`、`boolean`…）及其包装类，加上 `String`。

```java
public class BookDaoImpl implements BookDao {
    private String databaseName;
    private int connectionNum;

    public void setConnectionNum(int connectionNum) {
        this.connectionNum = connectionNum;
    }
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public void save() {
        System.out.println("book dao save ..." + databaseName + "," + connectionNum);
    }
}
```

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl">
    <property name="connectionNum" value="100"/>
    <property name="databaseName" value="mysql"/>
</bean>
```

注意两点：

1. **`value` 写的永远是字符串**，XML 里没有类型概念。`value="100"` 注入给 `int connectionNum` 时，Spring 会自动做类型转换。
2. **转换失败会在容器启动时报错**。比如把 `value="100"` 写成 `value="abc"`，会抛 `NumberFormatException`——这是好事，问题暴露在启动期而不是运行期。

**`value` 和 `ref` 不能混用**：给引用类型写 `value`，Spring 会把 bean 的 id 当成普通字符串处理，导致类型不匹配报错；反之给简单类型写 `ref`，会找不到叫 `mysql` 的 bean。

## 六、运行方式

直接运行 `com.spring.AppForDISet` 的 `main` 方法：

```java
ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");
BookService bookService = (BookService) ctx.getBean("bookService");
bookService.save();
```

预期输出：

```
book service save ...
book dao save ...mysql,100
user dao save ...
```

三行输出分别证明了：

1. `bookService` 被容器创建成功；
2. `bookDao` **被注入进了 service**（否则第 2 行会抛 `NullPointerException`），并且它自己的两个简单类型属性也注入成功；
3. `userDao` 同样注入成功。

可以自己验证的点：

- **注释掉 `<property name="bookDao" ref="bookDao"/>`** → 运行时抛 `NullPointerException`，因为字段还是 null。注意容器**能正常启动**，错误延后到调用时才爆发。
- **把某个 set 方法删掉** → 容器启动阶段就报 `not writable or has an invalid setter method`。

## 七、小结

```
IoC：对象由容器创建           <bean id="..." class="..."/>
 DI：依赖由容器装配           <property name="..." ref="..."/>

setter 注入三要素：
  ① 类中提供 set 方法
  ② <property> 的 name 对应 set 方法名（去 set、首字母小写）
  ③ 引用类型用 ref，简单类型用 value
```

setter 注入的特点是**灵活但不安全**：属性可以后续被改掉，也可以漏配而不报错（直到运行时 NPE）。下一个案例 `spring_06_di_constructor` 会介绍**构造器注入**，它能在容器启动阶段就强制保证依赖必须存在。

再往后 `spring_07_di_autoware` 会用**自动装配**省掉这些 `<property>` 配置，`spring_08_di_collection` 则处理集合类型的注入。

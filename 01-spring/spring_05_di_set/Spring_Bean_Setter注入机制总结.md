# Spring Bean 的 Setter 注入与容器构建机制

## 问题

在 XML 中已经通过 `id` 标识了 Bean，为什么注入时还需要填写 `<property>` 的 `name`？Spring 容器构建 Bean 时，Service 为什么不能直接获取它所依赖的 DAO？整个构造和注入过程是怎样的？

## 结论

`id` 和 `property name` 表达的是两件不同的事情：

- `id` 或 `ref`：确定使用容器中的**哪个对象**。
- `property name`：确定把这个对象注入当前 Bean 的**哪个属性**。

例如：

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>

<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <property name="bookDao" ref="bookDao"/>
</bean>
```

其中：

| 配置 | 含义 |
| --- | --- |
| `id="bookService"` | 当前 Bean 在 Spring 容器中的名字 |
| `name="bookDao"` | `bookService` 中需要赋值的 JavaBean 属性 |
| `ref="bookDao"` | 要注入的 Bean 的 ID |

可以把它记成：

```xml
<property name="注入到哪里" ref="注入哪个Bean"/>
```

## `name` 与 Setter 方法的关系

`name="bookDao"` 填写的是**属性名**，不是完整的方法名。Spring 会按照 JavaBean 规范寻找对应的 Setter 方法：

```text
属性名 bookDao → Setter 方法 setBookDao(...)
```

因此，上面的 XML 配置大致等价于：

```java
BookDao bookDaoObject = new BookDaoImpl();
BookServiceImpl bookServiceObject = new BookServiceImpl();

bookServiceObject.setBookDao(bookDaoObject);
```

如果 Java 类中存在：

```java
public void setBookDao(BookDao bookDao) {
    this.bookDao = bookDao;
}
```

那么 `<property name="bookDao" .../>` 就会匹配这个方法。

## 为什么仅有 Bean ID 还不够

Bean ID 只能唯一定位容器中的对象，无法说明这个对象应该被放到目标 Bean 的哪个位置。

例如，一个 Service 可能有两个相同类型的属性：

```java
public class BookServiceImpl {
    private BookDao primaryDao;
    private BookDao backupDao;

    public void setPrimaryDao(BookDao primaryDao) {
        this.primaryDao = primaryDao;
    }

    public void setBackupDao(BookDao backupDao) {
        this.backupDao = backupDao;
    }
}
```

同一个 DAO Bean 可以被注入不同属性：

```xml
<bean id="mysqlBookDao" class="com.spring.dao.impl.BookDaoImpl"/>

<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <property name="primaryDao" ref="mysqlBookDao"/>
    <property name="backupDao" ref="mysqlBookDao"/>
</bean>
```

这里 `ref` 相同，但 `name` 不同。Spring 必须根据 `name` 判断调用 `setPrimaryDao()` 还是 `setBackupDao()`。

## Service 和 DAO 不是父子 Bean

虽然从业务分层来看，Service 会调用 DAO，但它们在 Spring 容器中通常是两个平级、独立的 Bean：

```text
Spring 容器
├── bookDao
├── userDao
└── bookService
    ├── 持有 bookDao 的引用
    └── 持有 userDao 的引用
```

`bookService` 只是依赖并持有 DAO 的引用，不是 DAO 的“父 Bean”。因此，容器不会根据所谓的父子关系自动把 DAO 交给 Service。

Spring 中真正的“父子 Bean”一般指 Bean 定义的继承：

```xml
<bean id="baseBean" abstract="true">
    <!-- 公共配置 -->
</bean>

<bean id="childBean" parent="baseBean">
    <!-- 子 Bean 配置 -->
</bean>
```

这里的 `parent` 表示**配置继承**，不是 Java 对象之间的包含关系，也不表示父对象能自动获得子对象。
## Spring 容器的构建过程

以默认的单例 Bean 和 Setter 注入为例，过程大致如下：

1. Spring 读取 `applicationContext.xml`。
2. 将每个 `<bean>` 解析为 `BeanDefinition`，保存类名、作用域、属性和依赖信息。
3. 根据类名，通过反射调用构造方法创建 Bean 实例。
4. 解析 Bean 中的 `<property>` 配置。
5. 根据 `ref` 从容器中查找依赖对象；如果该对象还没有创建，则先创建它。
6. 根据 `name` 查找对应的 JavaBean Setter 方法。
7. 调用 Setter，将依赖对象注入目标 Bean。
8. 执行 Bean 后置处理器和初始化方法。
9. 将完整的单例 Bean 保存到单例池，供后续获取和使用。

简化后的效果相当于：

```java
BookDao bookDao = new BookDaoImpl();
UserDao userDao = new UserDaoImpl();

BookServiceImpl bookService = new BookServiceImpl();
bookService.setBookDao(bookDao);
bookService.setUserDao(userDao);
```

可以概括为：

```text
读取配置
  ↓
注册 BeanDefinition
  ↓
实例化 Bean
  ↓
解析 ref，取得依赖 Bean
  ↓
根据 property name 调用 Setter
  ↓
初始化 Bean
  ↓
放入单例池
```

## 为什么 Spring 不自动猜测依赖关系

仅仅注册了 `bookDao` 和 `bookService`，只能说明容器需要管理这两个对象，并不能说明：

- `bookService` 是否需要 `bookDao`；
- 需要注入到哪个属性；
- 存在多个 `BookDao` 实现时应该选择哪一个；
- 应该使用 Setter 注入、构造器注入，还是其他方式。

所以使用纯 XML 的显式 Setter 注入时，必须通过 `name` 和 `ref` 把依赖关系描述清楚。

## 构造器注入

除了 Setter 注入，也可以使用构造器注入：

```java
public class BookServiceImpl implements BookService {
    private final BookDao bookDao;

    public BookServiceImpl(BookDao bookDao) {
        this.bookDao = bookDao;
    }
}
```

对应 XML 可以写成：

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>

<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <constructor-arg ref="bookDao"/>
</bean>
```

现代 Spring 项目通常更推荐构造器注入，因为依赖更加明确，并且对象一旦构造完成就处于依赖完整的可用状态。

## 一句话记忆

```text
Bean 的 id/ref 负责“找对象”，property 的 name 负责“找注入位置”。
```

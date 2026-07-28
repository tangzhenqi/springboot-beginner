# spring_03_bean_instance —— Bean 实例化的四种方式

## 一、案例目标

搞清楚一个问题：**Spring 容器到底是怎么把对象造出来的？**

本案例用 `BookDao`、`OrderDao`、`UserDao` 三个 Dao，演示 Spring 实例化 Bean 的四种方式：

| 方式 | 配置写法 | 演示类 | 启动类 |
| --- | --- | --- | --- |
| 一、构造方法 | `<bean class="..."/>` | `BookDaoImpl` | `AppForInstanceBook` |
| 二、静态工厂 | `factory-method` | `OrderDaoFactory` | `AppForInstanceOrder` |
| 三、实例工厂 | `factory-bean` + `factory-method` | `UserDaoFactory` | `AppForInstanceUser` |
| 四、FactoryBean | `<bean class="XxxFactoryBean"/>` | `UserDaoFactoryBean` | `AppForInstanceUser` |

## 二、工程结构

```
spring_03_bean_instance
├── pom.xml                       仅依赖 spring-context 5.2.10.RELEASE
└── src/main
    ├── java/com/spring
    │   ├── AppForInstanceBook.java          方式一启动类
    │   ├── AppForInstanceOrder.java         方式二启动类
    │   ├── AppForInstanceUser.java          方式三 / 四启动类
    │   ├── dao/BookDao.java                 三个接口
    │   ├── dao/OrderDao.java
    │   ├── dao/UserDao.java
    │   ├── dao/impl/*.java                  三个实现类
    │   └── factory
    │       ├── OrderDaoFactory.java         静态工厂
    │       ├── UserDaoFactory.java          实例工厂
    │       └── UserDaoFactoryBean.java      FactoryBean 实现
    └── resources/applicationContext.xml     四种方式的配置（三种被注释）
```

> **重要**：`applicationContext.xml` 中四种方式**同一时间只放开一种**，其余三种是注释状态。要跑哪种方式，就把对应的注释打开、其他的注释掉，否则启动类会因为找不到 bean 抛 `NoSuchBeanDefinitionException`。

## 三、四种方式详解

### 方式一：构造方法实例化（最常用）

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"/>
```

Spring 拿到 `class` 属性后，通过**反射调用无参构造方法**创建对象。

```java
public class BookDaoImpl implements BookDao {
    public BookDaoImpl() {
        System.out.println("book dao constructor is running ....");
    }
}
```

运行 `AppForInstanceBook` 会先看到 `book dao constructor is running ....`，这就是构造方法被容器调用的证据。

- **必须有无参构造方法**（不写任何构造方法时，编译器会自动生成一个）。
- 如果只提供了有参构造而没有无参构造，容器启动会报 `BeanCreationException`，根因是 `NoSuchMethodException: <init>()`。
- 构造方法即使是 `private` 的，Spring 也能通过反射调用。

### 方式二：静态工厂实例化

有些对象不是 `new` 出来的，而是由某个工厂类的**静态方法**提供的（常见于遗留代码、第三方 SDK）。

```java
public class OrderDaoFactory {
    public static OrderDao getOrderDao(){
        System.out.println("factory setup....");   // 工厂里可以做额外的初始化逻辑
        return new OrderDaoImpl();
    }
}
```

```xml
<bean id="orderDao" class="com.spring.factory.OrderDaoFactory" factory-method="getOrderDao"/>
```

- `class` 写的是**工厂类**，不是最终对象的类。
- `factory-method` 指定那个静态方法，Spring 把方法**返回值**当作 bean 存进容器。
- 使用价值：可以在工厂方法里塞入构造对象之外的业务逻辑（如日志、缓存、参数校验）。

对比原生写法（启动类里被注释的部分）：

```java
OrderDao orderDao = OrderDaoFactory.getOrderDao();
```

### 方式三：实例工厂实例化

工厂方法不是静态的，得**先有工厂对象，再调方法**。

```java
public class UserDaoFactory {
    public UserDao getUserDao(){
        return new UserDaoImpl();
    }
}
```

```xml
<!-- 第一步：把工厂本身也交给容器管理 -->
<bean id="userFactory" class="com.spring.factory.UserDaoFactory"/>
<!-- 第二步：用工厂 bean 去调方法 -->
<bean id="userDao" factory-method="getUserDao" factory-bean="userFactory"/>
```

- 注意第二个 `<bean>` **没有 `class` 属性**，因为对象不由容器直接造，而是工厂造。
- `factory-bean` 指向工厂的 bean id，`factory-method` 指向实例方法。
- 缺点很明显：**要配两个 bean，还得记两个属性名**，配置繁琐——所以有了方式四。

对比原生写法：

```java
UserDaoFactory userDaoFactory = new UserDaoFactory();
UserDao userDao = userDaoFactory.getUserDao();
```

### 方式四：FactoryBean 实例化（Spring 推荐的工厂方案）

实现 Spring 提供的 `FactoryBean<T>` 接口，把"实例工厂"标准化。

```java
public class UserDaoFactoryBean implements FactoryBean<UserDao> {
    // 代替原始实例工厂中创建对象的方法
    public UserDao getObject() throws Exception {
        return new UserDaoImpl();
    }
    public Class<?> getObjectType() {
        return UserDao.class;
    }
    // 控制是否为单例，默认 true
    public boolean isSingleton(){
        return true;
    }
}
```

```xml
<bean id="userDao" class="com.spring.factory.UserDaoFactoryBean"/>
```

三个方法的职责：

| 方法 | 作用 | 是否必须实现 |
| --- | --- | --- |
| `getObject()` | 返回真正要放入容器的对象 | 必须 |
| `getObjectType()` | 返回对象类型，供按类型注入时使用 | 必须 |
| `isSingleton()` | `true` 单例（默认），`false` 每次获取都新建 | 可选，接口有默认实现 |

**关键理解**：配置里 `class` 写的是 `UserDaoFactoryBean`，但 `ctx.getBean("userDao")` 拿到的是 `UserDaoImpl`，不是工厂本身。容器识别出这是 `FactoryBean`，会自动调 `getObject()` 把产品交出来。

> 如果确实想拿到工厂对象本身，在 id 前加 `&`：`ctx.getBean("&userDao")`。

## 四、验证单例：`isSingleton()` 的效果

`AppForInstanceUser` 特意取了两次 bean 并打印：

```java
UserDao userDao1 = (UserDao) ctx.getBean("userDao");
UserDao userDao2 = (UserDao) ctx.getBean("userDao");
System.out.println(userDao1);
System.out.println(userDao2);
```

- `isSingleton()` 返回 `true`（当前代码）→ 两行地址**相同**，容器只造了一个对象：

  ```
  com.spring.dao.impl.UserDaoImpl@2e5c649
  com.spring.dao.impl.UserDaoImpl@2e5c649
  ```

- 改成 `return false;` 重跑 → 两行地址**不同**，每次 `getBean` 都调一次 `getObject()`。

这是理解 Spring bean 作用域（scope）最直观的一个小实验。

## 五、运行方式

`applicationContext.xml` 默认放开的是**方式四**，所以可以直接运行 `AppForInstanceUser`。

要跑其他方式，按下表改配置：

| 想运行 | 打开 XML 中的注释 | 运行启动类 |
| --- | --- | --- |
| 方式一 | 方式一那行 `<bean id="bookDao" .../>` | `AppForInstanceBook` |
| 方式二 | 方式二那行 `<bean id="orderDao" .../>` | `AppForInstanceOrder` |
| 方式三 | 方式三那两行 `userFactory` + `userDao` | `AppForInstanceUser` |
| 方式四 | 方式四那行（默认已打开） | `AppForInstanceUser` |

> 方式三和方式四都用 id `userDao`，所以两者不能同时放开——后定义的会覆盖先定义的。

## 六、小结与选型

```
方式一 构造方法      → 自己写的类，绝大多数场景用它
方式二 静态工厂      → 兼容遗留代码 / 第三方静态工厂
方式三 实例工厂      → 兼容遗留代码 / 第三方实例工厂（配置繁琐，已被方式四取代）
方式四 FactoryBean   → 需要复杂构造逻辑时的标准做法（Spring 内部大量使用）
```

实际开发中：

- **99% 的情况用方式一**，交给容器 new 就行。
- 需要把复杂的构造过程（读配置、建连接、动态代理）封装起来时，用 **FactoryBean**。MyBatis 整合 Spring 的 `SqlSessionFactoryBean`、`MapperFactoryBean` 就是这么干的，后面 `spring_15_spring_mybatis` 会再见到它。
- 方式二、方式三主要是为了理解历史演进和兼容老代码，了解即可。

# QA：Spring 容器造对象，必须是接口的实现类吗？普通类行不行？

> 配套案例：`spring_03_bean_instance`
> 相关文档：[README.md](./README.md)

## 一、结论

**普通类完全可以，Spring 从来没有"必须面向接口"的要求。**

接口是给**人**（架构解耦）和 **AOP** 用的，不是给容器用的。容器只关心一件事：**这个对象能不能被造出来**。

## 二、Spring 的真实要求

`<bean class="X"/>` 中的 `X` 只需要满足一条：**能被反射实例化**。具体来说：

- 必须是**具体类**——不能是 `interface`，不能是 `abstract class`
- 有可用的构造方法——无参构造，或用 `<constructor-arg>` 配合有参构造

### 反例：写成接口会直接报错

```xml
<!-- 错误写法：class 写了接口 -->
<bean id="userDao" class="com.spring.dao.UserDao"/>
```

启动时抛异常：

```
BeanInstantiationException: Failed to instantiate [com.spring.dao.UserDao]:
Specified class is an interface
```

抽象类同理，报 `Is it an abstract class?`。

## 三、本工程里的现成证据

`README.md` 方式三的配置中有这么一行：

```xml
<bean id="userFactory" class="com.spring.factory.UserDaoFactory"/>
```

看一下 `src/main/java/com/spring/factory/` 下三个类的声明：

| 类 | 声明 | 是否实现业务接口 |
| --- | --- | --- |
| `OrderDaoFactory` | `public class OrderDaoFactory { }` | ❌ 没有 |
| `UserDaoFactory` | `public class UserDaoFactory { }` | ❌ 没有 |
| `UserDaoFactoryBean` | `implements FactoryBean<UserDao>` | 实现的是 Spring 的**回调接口**，不是业务接口 |

`UserDaoFactory` 是个光秃秃的普通类，照样被容器正常管理、正常造对象。这就是最直接的证明。

## 四、那 Dao 为什么都写成接口 + 实现类？

这是**分层开发的编码习惯**，不是容器的约束。三个原因：

### 1. 解耦

Service 里声明的是接口类型：

```java
private BookDao bookDao;   // 声明接口
```

容器注入进来的是 `BookDaoImpl`。将来要换实现（比如从 JDBC 版换成 MyBatis 版），只改配置文件，Service 代码一行不动。

### 2. 配合方式二 / 三 / 四

工厂方法的返回值类型写成接口，返回的是实现类实例，调用方只认接口：

```java
public static OrderDao getOrderDao() {     // 返回类型是接口
    return new OrderDaoImpl();             // 实际给的是实现类
}
```

`FactoryBean` 的 `getObjectType()` 返回 `UserDao.class` 也是同样的思路。

### 3. `getBean` 的强转类型

```java
UserDao userDao = (UserDao) ctx.getBean("userDao");
```

强转成接口比强转成实现类更稳——尤其是当 bean 被 AOP 代理过之后（见下一节）。

## 五、什么时候接口才真的开始有影响

只有一个地方：**AOP 代理**（后面 `spring_18_aop_quickstart` 及之后的案例会遇到）。

| 目标类 | Spring 采用的代理方式 | 需要注意的坑 |
| --- | --- | --- |
| 实现了接口 | JDK 动态代理（默认） | 注入时**必须声明为接口类型**，声明成实现类会抛 `ClassCastException` |
| 没有接口 | CGLIB 生成子类 | 类不能是 `final`，被代理的方法不能是 `final` / `private` / `static` |

也就是说，接口的有无决定的是**代理策略**，而不是**能否被容器管理**。

## 六、一个反向的特例：MyBatis 的 Mapper

MyBatis 的 Mapper 是**纯接口，没有任何实现类**，但它照样能进 Spring 容器。靠的是 `MapperFactoryBean`——也就是 README 里方式四的套路，本质是用动态代理在运行期生成一个实现。

这个例子恰好反过来印证了本文的结论：

> 能不能进容器，取决于**有没有办法造出实例**（自己 new、工厂造、还是代理生成），跟"是不是接口"没有直接关系。

## 七、一句话总结

```
容器只关心「能不能造出来」  →  普通类完全没问题
接口是给人和 AOP 用的      →  不是容器的硬性要求
```

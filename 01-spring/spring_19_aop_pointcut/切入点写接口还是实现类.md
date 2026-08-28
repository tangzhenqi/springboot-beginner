# 切入点表达式：为什么写接口方法名也能匹配到实现类

> 起因：`spring_18_aop_quickstart` 的 `MyAdvice` 里写的是
>
> ```java
> @Pointcut("execution(void com.spring.dao.BookDao.update())")
> ```
>
> 表达式指向的是**接口** `BookDao`，但真正被增强的是**实现类** `BookDaoImpl.update()`。为什么能对上？

## 一、核心结论

**`execution` 的类型部分匹配的不是"方法定义在哪个类"，而是"这个类型及其所有子类型"。**

## 二、AspectJ 的匹配规则

`execution(void com.spring.dao.BookDao.update())` 里的 `BookDao` 是一个**类型模式**（type pattern），不是一个精确的类引用。它的判定逻辑是：

> 正在执行的方法，其所在的类是 `BookDao` **或它的子类型**，且这个方法**重写 / 实现了** `BookDao` 中签名匹配的方法。

套到本案例：

| 条件 | 是否满足 |
| --- | --- |
| `BookDaoImpl` 是 `BookDao` 的子类型？ | ✓ `implements BookDao` |
| `BookDaoImpl.update()` 实现了 `BookDao.update()`？ | ✓ 签名完全一致 |

两个条件都满足，所以匹配成功。

换句话说，写接口相当于说"**`BookDao` 这一族里所有的 `update()`**"，而不是"仅限 `BookDao` 这个接口本身声明的那个"。

## 三、写接口和写实现类的区别

这两种写法在 `spring_18` 那个案例中效果完全一样（都是 `MyAdvice` 里被注释掉的候选项）：

```java
@Pointcut("execution(void com.spring.dao.BookDao.update())")           // 接口，匹配所有实现类
@Pointcut("execution(void com.spring.dao.impl.BookDaoImpl.update())")  // 实现类，只匹配这一个
```

**差别在覆盖范围**：

```
          BookDao (接口)
              ↑
    ┌─────────┼─────────┐
BookDaoImpl  BookDaoMysqlImpl  BookDaoOracleImpl
```

- 写**接口** → 上图三个实现类的 `update()` **全部匹配**，以后再加新实现类也自动覆盖；
- 写**实现类** → 只管得住 `BookDaoImpl` 这一个，新增的实现类需要手工再加切入点。

这就是"**切入点表达式优先写接口**"这条经验的实际含义——它对扩展是开放的。

## 四、边界情况：接口里没有的方法

反过来就有限制了。如果实现类多出一个接口没声明的方法：

```java
public class BookDaoImpl implements BookDao {
    public void delete() { }   // BookDao 接口里没有
}
```

那么：

```java
@Pointcut("execution(void com.spring.dao.BookDao.delete())")       // ✗ 匹配不到
@Pointcut("execution(void com.spring.dao.impl.BookDaoImpl.delete())")  // ✓ 只能这么写
```

因为 `delete()` 没有"实现接口中的某个方法"，**不满足第二个条件**。

> 更麻烦的是：Spring AOP 用 JDK 动态代理时，代理对象只实现了 `BookDao` 接口，**根本不存在 `delete()` 这个方法**，外部也调不到。所以这种方法要么补进接口，要么改用 CGLIB 代理。

## 五、还有一层原因：Spring AOP 是基于代理的

`spring_18` 走的是 JDK 动态代理，生成的 `$Proxy19` **实现的就是 `BookDao` 接口**。所有调用本来就是从接口这一层进去的：

```java
BookDao bookDao = ctx.getBean(BookDao.class);
bookDao.update();     // 声明类型就是接口
```

所以对 Spring AOP 而言，**按接口写切入点是最自然、最贴合代理机制的方式**。

## 六、这条规则和本案例（spring_19）的关系

本案例 `MyAdvice` 里当前生效的表达式是：

```java
//执行com.spring包下的任意包下的名称以Service结尾的类或接口中的save方法，参数任意，返回值任意
@Pointcut("execution(* com.spring.*.*Service.save(..))")
```

注意注释里那句"**类或接口**"——正是本文讲的规则在起作用：

- `*Service` 这个模式既能匹配到接口 `BookService`，也能匹配到实现类 `BookServiceImpl`；
- 匹配到接口 `BookService` 时，**它的所有实现类的 `save()` 都会被增强**；
- 而 `BookServiceImpl` 这个名字本身其实**不以 `Service` 结尾**（是 `Impl` 结尾），单看类名模式匹配不上——它能被增强，完全是靠"实现了 `BookService.save()`"这条继承关系。

**这是本案例里最容易看错的一处。** 如果误以为"表达式只匹配类名"，就会觉得这条切入点应该完全不生效。

## 七、补充：Spring AOP 只是 AspectJ 的子集

Spring AOP **只支持方法执行（method execution）连接点**，不支持 AspectJ 完整的构造器调用、字段读写、静态初始化等连接点。

所以这里看到的 `execution` 语义，是 AspectJ 语法的一个子集：

| AspectJ 支持 | Spring AOP |
| --- | --- |
| `execution(...)` 方法执行 | ✓ 唯一支持的 |
| `call(...)` 方法调用 | ✗ |
| `get/set(...)` 字段访问 | ✗ |
| `initialization(...)` 构造 | ✗ |

用了不支持的类型会直接报表达式解析错误。

## 八、小结

```
execution(返回值 包名.类名/接口名.方法名(参数))
              ↑
        这里的类型是"类型模式"，含义是【该类型及其所有子类型】

写接口   → 覆盖所有实现类，推荐
写实现类 → 只覆盖这一个；接口中没有的方法只能这么写

前提是：该方法必须重写/实现了所匹配类型中的同签名方法
```

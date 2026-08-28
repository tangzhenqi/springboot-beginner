# spring_04_bean_lifecycle —— Bean 的生命周期

## 一、案例目标

上个案例（`spring_03_bean_instance`）解决的是**对象怎么造出来**，本案例解决的是**对象造出来之后、销毁之前，我能在哪些时机插入自己的代码**。

一句话概括：**Bean 从创建到销毁的完整过程叫生命周期，Spring 在初始化和销毁两个节点留了钩子给我们。**

本案例用两个 Bean 分别演示两套写法：

| Bean | 生命周期控制方式 | 特点 |
| --- | --- | --- |
| `BookDaoImpl` | XML 的 `init-method` / `destroy-method` | 方法名随便起，类不依赖 Spring |
| `BookServiceImpl` | 实现 `InitializingBean` / `DisposableBean` 接口 | 方法名固定，类和 Spring 耦合 |

## 二、工程结构

```
spring_04_bean_lifecycle
├── pom.xml                       仅依赖 spring-context 5.2.10.RELEASE
└── src/main
    ├── java/com/spring
    │   ├── AppForLifeCycle.java                    启动类（末尾附有生命周期笔记）
    │   ├── dao/BookDao.java
    │   ├── dao/impl/BookDaoImpl.java               init() / destory()
    │   ├── service/BookService.java
    │   └── service/impl/BookServiceImpl.java       实现两个 Spring 接口
    └── resources/applicationContext.xml            配置 init-method / destroy-method
```

## 三、生命周期全流程

```
初始化容器
  1. 创建对象（内存分配）
  2. 执行构造方法
  3. 执行属性注入（set 操作）
  4. 执行 bean 初始化方法        ← 钩子①

使用 bean
  5. 执行业务操作

关闭 / 销毁容器
  6. 执行 bean 销毁方法          ← 钩子②
```

**注意第 3 步和第 4 步的顺序**：初始化方法一定在属性注入**之后**执行。这个设计是有道理的——初始化逻辑往往要用到注入进来的依赖，如果先初始化后注入，依赖还是 null。`BookServiceImpl` 里 `set .....` 先于 `service init` 打印，就是这个顺序的直接证明。

## 四、两种控制方式

### 方式一：XML 配置 `init-method` / `destroy-method`

```java
public class BookDaoImpl implements BookDao {
    public void save() {
        System.out.println("book dao save ...");
    }
    //表示bean初始化对应的操作
    public void init(){
        System.out.println("init...");
    }
    //表示bean销毁前对应的操作
    public void destory(){
        System.out.println("destory...");
    }
}
```

```xml
<bean id="bookDao" class="com.spring.dao.impl.BookDaoImpl"
      init-method="init" destroy-method="destory"/>
```

- 方法名**任意**，只要 XML 里配的名字和方法名对得上就行（本例中 `destory` 是拼写笔误，但因为两边一致，照样能跑）。
- 方法必须**无参**，返回值随意（一般 void）。
- 类里没有任何 Spring 的 import，**代码零侵入**，这是这种方式的最大优势。
- 配错方法名会在容器启动时报错：`Couldn't find an init method named 'xxx'`。

### 方式二：实现 `InitializingBean` / `DisposableBean` 接口

```java
public class BookServiceImpl implements BookService, InitializingBean, DisposableBean {
    private BookDao bookDao;

    public void setBookDao(BookDao bookDao) {
        System.out.println("set .....");
        this.bookDao = bookDao;
    }

    // InitializingBean：属性设置完成后调用
    public void afterPropertiesSet() throws Exception {
        System.out.println("service init");
    }

    // DisposableBean：容器销毁 bean 前调用
    public void destroy() throws Exception {
        System.out.println("service destroy");
    }
}
```

```xml
<bean id="bookService" class="com.spring.service.impl.BookServiceImpl">
    <property name="bookDao" ref="bookDao"/>
</bean>
```

- **不需要在 XML 里配任何东西**，Spring 发现 bean 实现了这两个接口就会自动回调。
- 方法名是**固定**的：`afterPropertiesSet()` 和 `destroy()`，不能改。
- `afterPropertiesSet` 这个名字本身就说明了时机——"属性都设置完之后"。
- 缺点：业务类被迫 import `org.springframework.*`，**和 Spring 耦合了**。

### 两种方式的选择

| 对比项 | init-method | InitializingBean |
| --- | --- | --- |
| 侵入性 | 无，纯 POJO | 有，依赖 Spring 接口 |
| 方法名 | 自定义 | 固定 |
| 配置量 | 需要在 XML 配 | 无需配置 |
| 执行顺序 | 后 | **先** |

> 两种方式**可以同时用在一个 bean 上**，此时 `afterPropertiesSet()` 先执行，`init-method` 后执行。
>
> 实际开发中更推荐**注解方式** `@PostConstruct` / `@PreDestroy`（JSR-250 标准），既无需配置、又不和 Spring 耦合，后续注解开发的案例会用到。

## 五、销毁时机：容器不关，销毁方法就不会执行

这是本案例最容易踩坑的地方。**销毁方法只有在容器关闭时才会被调用**，如果 `main` 方法直接跑完退出，JVM 结束但容器没被正确关闭，`destory...` 和 `service destroy` 根本不会打印。

关闭容器有两种方式，都定义在 `ConfigurableApplicationContext` 接口上：

```java
// 注意：变量类型要写成 ClassPathXmlApplicationContext（或 ConfigurableApplicationContext）
// 写成 ApplicationContext 是没有 close() 和 registerShutdownHook() 的
ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");

// 方式一：手工关闭容器，必须放在所有业务代码之后
ctx.close();

// 方式二：注册关闭钩子，虚拟机退出前自动关闭容器，位置随意
ctx.registerShutdownHook();
```

| 对比项 | `close()` | `registerShutdownHook()` |
| --- | --- | --- |
| 触发时机 | 立即关闭 | JVM 退出前关闭 |
| 代码位置 | 必须在最后 | 任意位置 |
| 关闭后还能用吗 | 不能，再 `getBean` 会抛异常 | 关闭前正常使用 |

本案例的启动类默认用的是 `ctx.close()`，`registerShutdownHook()` 那行是注释状态，可以放开对比效果。

另外注意 XML 注释里的提醒：**`destroy-method` 仅适用于单例对象**。原型（`scope="prototype"`）的 bean 交给使用者后容器就不再管了，销毁方法永远不会被调用。

## 六、运行方式

直接运行 `com.spring.AppForLifeCycle` 的 `main` 方法，预期输出：

```
set .....            ← bookService 属性注入（setBookDao）
service init         ← bookService 初始化（afterPropertiesSet）
book dao save ...    ← 业务方法
destory...           ← bookDao 销毁（destroy-method）
service destroy      ← bookService 销毁（DisposableBean）
```

几个可以自己动手验证的点：

1. **把 `ctx.close()` 注释掉** → 后两行销毁日志消失，证明"容器不关就不销毁"。
2. **换成 `ctx.registerShutdownHook()`** → 销毁日志仍然打印，但时机是 JVM 退出前。
3. **在 `ctx.close()` 之后再调 `bookDao.save()`** → 抛 `IllegalStateException`，容器已关闭不能再取用 bean。
4. **给 `BookDaoImpl` 加个构造方法打印** → 可以看到构造方法在 `init...` 之前执行。

> 注意：`bookDao` 的 `init...` 会先于 `set .....` 打印，因为 XML 中 `bookDao` 定义在前，容器按顺序创建并初始化完 `bookDao` 之后，才轮到 `bookService`。

## 七、小结

```
构造方法        →  对象诞生
set 方法        →  依赖注入
初始化方法      →  init-method / afterPropertiesSet()   ← 资源准备（建连接、加载缓存）
业务方法        →  正常使用
销毁方法        →  destroy-method / destroy()           ← 资源释放（关连接、写日志）
```

实际项目中生命周期方法的典型用途：**初始化时建立数据库连接池、预热缓存、校验配置；销毁时关闭连接、刷盘、注销注册中心**。这也是为什么理解"容器必须显式关闭"很重要——否则资源就泄漏了。

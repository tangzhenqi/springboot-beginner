# 03 Servlet 生命周期（面试高频）

对应代码：`LifeCycleServlet.java`

## 一、四个阶段

**Servlet 对象由 Tomcat 创建和管理，我们只负责写逻辑，不负责 new。**

| 阶段 | 触发的方法 | 执行次数 | 时机 |
| --- | --- | :---: | --- |
| ① 实例化 | 构造方法 | **1 次** | 默认首次访问时；配了 `loadOnStartup>=0` 则服务器启动时 |
| ② 初始化 | `init(ServletConfig)` | **1 次** | 实例化之后立即执行 |
| ③ 提供服务 | `service()` → `doGet/doPost` | **N 次** | 每次请求执行一次 |
| ④ 销毁 | `destroy()` | **1 次** | 服务器正常关闭前 |

## 二、动手验证

1. 启动服务：`mvn -pl springmvc_00_servlet tomcat7:run`

   由于 `LifeCycleServlet` 配了 `loadOnStartup = 1`，**还没访问就已经打印**：

   ```
   【1-实例化】LifeCycleServlet 构造方法执行，对象地址：com.springmvc.servlet.LifeCycleServlet@2b05039f
   【2-初始化】init方法执行，servlet名称：LifeCycleServlet
              初始化参数 author = springmvc-beginner
              初始化参数 version = 1.0
   ```

2. 访问 <http://localhost/lifecycle>，**连续刷新 3 次**，控制台输出：

   ```
   【3-服务】doGet方法执行，第 1 次访问，对象地址：com.springmvc.servlet.LifeCycleServlet@2b05039f
   【3-服务】doGet方法执行，第 2 次访问，对象地址：com.springmvc.servlet.LifeCycleServlet@2b05039f
   【3-服务】doGet方法执行，第 3 次访问，对象地址：com.springmvc.servlet.LifeCycleServlet@2b05039f
   ```

   **对象地址完全相同**，构造方法和 init **没有再次执行** —— 这就是「单例」的证据。

3. 控制台按 `Ctrl + C` 停止服务：

   ```
   【4-销毁】destroy方法执行，共处理了 3 次请求，资源已释放
   ```

## 三、逐阶段详解

### 阶段① 实例化

Tomcat 通过**反射调用无参构造**创建对象：

```java
Class.forName("com.springmvc.servlet.LifeCycleServlet").newInstance();
```

**因此 Servlet 类必须有 public 的无参构造方法。**
如果你只写了带参构造，编译器不再生成默认无参构造，Tomcat 就创建不出对象，启动/访问时报
`java.lang.InstantiationException`。

#### loadOnStartup 的取值

| 值 | 含义 |
| --- | --- |
| 负数（默认 -1） | **懒加载**：第一次被访问时才创建 |
| 0 或正数 | **饿汉式**：服务器启动时就创建，数字越小优先级越高，越早创建 |

**该用哪个？**

- 默认（懒加载）：节省启动时的内存，但第一个访问的用户会感受到延迟
- 配置正数：适合初始化开销大的 Servlet（要读配置文件、建数据库连接池），
  把耗时提前到启动阶段，用户请求时直接可用

本案例中 `LifeCycleServlet` 配 1、`XmlConfigServlet` 配 2，所以启动日志里前者先打印。

> SpringMVC 的 `DispatcherServlet` 通常也配 `load-on-startup=1`，
> 因为它初始化时要创建整个 Spring 容器、扫描所有 Bean，非常耗时。

### 阶段② 初始化

```java
@Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);      // ⚠️ 必须调用，否则getServletConfig()返回null
    // 你的初始化逻辑：读配置、建连接池、加载缓存...
}
```

**只执行一次**，是做「一次性准备工作」的地方。

⚠️ **常见错误**：忘记 `super.init(config)`，后面调用 `getServletContext()` 抛空指针。
避坑做法是重写**无参**的 `init()`：

```java
@Override
public void init() throws ServletException {
    // GenericServlet已经在init(config)里保存好了config，再回调这个无参方法
    System.out.println("初始化参数：" + getInitParameter("author"));
}
```

### 阶段③ 提供服务

每来一次请求执行一次。调用链：

```
Tomcat线程
  └─> HttpServlet.service(ServletRequest, ServletResponse)   ← 强转类型
        └─> HttpServlet.service(HttpServletRequest, HttpServletResponse)  ← 按请求方式分发
              └─> doGet(...) / doPost(...) / doPut(...) / doDelete(...)
```

所以有三种"接管请求"的写法，粒度由粗到细：

| 重写的方法 | 效果 | 案例 |
| --- | --- | --- |
| `service(ServletRequest, ServletResponse)` | 接管一切，但要自己强转类型 | 一般不用 |
| `service(HttpServletRequest, HttpServletResponse)` | 接管所有请求方式，不再分发到 doXxx | **`BaseServlet`** |
| `doGet` / `doPost` | 只处理特定请求方式 | `HelloServlet` 等 |

### 阶段④ 销毁

```java
@Override
public void destroy() {
    // 释放资源：关闭连接池、停止定时任务、写日志...
}
```

**只在服务器"正常关闭"时执行**。以下情况 `destroy()` **不会**执行：

- `kill -9` 强杀进程
- IDEA 里点红色方块「强制停止」（点一次是正常关闭，连点两次是强杀）
- 服务器断电、JVM 崩溃

所以 **`destroy()` 里不能放"必须执行"的关键逻辑**（如订单落库），只能做尽力而为的资源清理。

## 四、核心结论：单例多线程

**一个 Servlet 类，在一个 Web 应用中只有一个实例，所有请求共用它。**

```
请求1（线程A）─┐
请求2（线程B）─┼─→  同一个 UserServlet 实例  ─→  可能同时执行 doGet()
请求3（线程C）─┘
```

### 由此产生的线程安全问题

`LifeCycleServlet` 里的这行代码，**是反面教材**：

```java
private int count = 0;      // ❌ 有状态的成员变量，线程不安全

protected void doGet(...) {
    count++;                // 多线程同时执行，会丢失更新
}
```

`count++` 不是原子操作（读→加→写三步），并发访问时统计结果会偏小。
本案例保留它，只是为了**直观证明"多次请求共用一个实例"**，实际开发中禁止这样写。

### 正确的做法

| 场景 | 做法 |
| --- | --- |
| 请求相关的数据 | **定义成局部变量**，每个线程有自己的栈帧，天然安全 |
| 必须共享的计数器 | 用 `AtomicInteger`（见 `AppContextListener` 的 `ONLINE_COUNT`） |
| 无状态的工具/业务对象 | 可以做成员变量（见 `UserServlet` 的 `userService`，它没有可变字段） |
| 有状态的资源 | 用 `ThreadLocal` 或每次请求新建 |

```java
public class UserServlet extends BaseServlet {
    // ✅ 安全：UserService内部没有可变的成员变量，是无状态的
    private final UserService userService = new UserService();

    public String list(HttpServletRequest request, HttpServletResponse response) {
        // ✅ 安全：局部变量，每个线程独立
        List<User> userList = userService.findAll();
        request.setAttribute("userList", userList);
        return "/WEB-INF/pages/list.jsp";
    }
}
```

> **这条规则对 Spring 同样适用。** Spring 的 Bean 默认也是单例的，
> 所以 `@Controller`、`@Service` 里同样不能定义有状态的成员变量。
> 你在 SpringMVC 里写的 `@Autowired private UserService userService;`
> 和这里的 `private final UserService userService = new UserService();` 是一回事，
> 区别只是对象由谁创建。

## 五、面试答法参考

> **问：说一下 Servlet 的生命周期？**
>
> 答：Servlet 的生命周期由 Web 容器管理，分四个阶段。
> 第一是实例化，容器通过反射调用无参构造创建对象，默认是第一次被访问时创建，
> 也可以通过 `load-on-startup` 配置成启动时创建；
> 第二是初始化，调用 `init(ServletConfig)`，整个生命周期只执行一次，
> 一般在这里做加载配置、初始化连接池等一次性工作；
> 第三是提供服务，每次请求都会调用 `service()`，`HttpServlet` 的 `service` 会根据
> 请求方式分发到 `doGet`、`doPost` 等方法；
> 第四是销毁，容器正常关闭前调用 `destroy()`，也只执行一次，用来释放资源。
>
> 需要补充的是，**Servlet 在容器中是单例的**，多个请求由多个线程共用同一个实例，
> 所以不能在 Servlet 中定义有状态的成员变量，否则会有线程安全问题。

---

上一篇：[02 Servlet 入门与配置](02-Servlet入门与配置.md)　|　下一篇：[04 Request 请求对象详解](04-Request请求对象详解.md)

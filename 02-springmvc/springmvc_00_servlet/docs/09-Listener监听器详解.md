# 09 Listener 监听器详解

对应代码：`AppContextListener.java`

## 一、Listener 是什么

**监听器用来监听 Web 应用中「对象的创建/销毁」和「数据的增删改」，在事件发生时自动执行回调代码。**

这是**观察者模式**在 Servlet 规范中的应用：

- **事件源**：`ServletContext`、`HttpSession`、`HttpServletRequest`
- **监听器**：我们写的类
- **事件**：创建、销毁、属性变化
- **注册**：`@WebListener` 或 `web.xml` 的 `<listener>`

**Filter 是"拦在路上"，Listener 是"站在旁边看"** —— 监听器不能阻止事件发生，只能感知并响应。

## 二、8 个监听器接口

Servlet 规范一共定义了 8 个监听器，按监听对象分为三类。

### 第一类：监听域对象的创建与销毁（3 个）

| 接口 | 监听对象 | 触发时机 | 常见用途 |
| --- | --- | --- | --- |
| **`ServletContextListener`** | ServletContext | 应用启动 / 关闭 | **⭐最常用**：加载配置、创建容器、初始化连接池、启动定时任务 |
| **`HttpSessionListener`** | HttpSession | 会话创建 / 销毁 | **⭐统计在线人数** |
| `ServletRequestListener` | ServletRequest | 请求开始 / 结束 | 请求日志、耗时统计 |

### 第二类：监听域对象的属性变化（3 个）

| 接口 | 触发时机 |
| --- | --- |
| `ServletContextAttributeListener` | application 域 `setAttribute`/`removeAttribute`/`replace` |
| `HttpSessionAttributeListener` | session 域属性变化 |
| `ServletRequestAttributeListener` | request 域属性变化 |

每个接口都有三个方法：`attributeAdded`、`attributeRemoved`、`attributeReplaced`。

### 第三类：感知型监听器（2 个，用得少）

这两个比较特殊：**不需要注册**，而是让**存进 session 的 JavaBean 自己实现**：

| 接口 | 作用 |
| --- | --- |
| `HttpSessionBindingListener` | 感知自己被绑定到 session / 从 session 解绑 |
| `HttpSessionActivationListener` | 感知自己随 session 被钝化到磁盘 / 活化回内存 |

```java
// 例如让User自己知道被存进了session
public class User implements HttpSessionBindingListener {
    public void valueBound(HttpSessionBindingEvent event) {
        System.out.println(username + " 上线了");
    }
    public void valueUnbound(HttpSessionBindingEvent event) {
        System.out.println(username + " 下线了");
    }
}
```

**实际开发中，前两个（`ServletContextListener` 和 `HttpSessionListener`）占了 95% 的使用场景。**

## 三、案例代码解析

`AppContextListener` **同时实现了两个接口**，一个类干两件事：

```java
@WebListener
public class AppContextListener implements ServletContextListener, HttpSessionListener {
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);
    ...
}
```

### 1. ServletContextListener：应用启动初始化

```java
@Override
public void contextInitialized(ServletContextEvent sce) {
    ServletContext context = sce.getServletContext();
    context.setAttribute("appName", "Servlet详细案例");
    context.setAttribute("startTime", System.currentTimeMillis());

    System.out.println("【Listener】应用启动完成：" + context.getServletContextName());
    System.out.println("【Listener】服务器信息：" + context.getServerInfo());
    System.out.println("【Listener】真实磁盘路径：" + context.getRealPath("/"));
}
```

**关键点：这个方法在所有 Servlet 初始化之前执行**，是整个应用最早的执行点。
所以「Servlet 用到的公共资源」都可以在这里准备好。

`ServletContextEvent` 只有一个有用的方法 `getServletContext()`，用来拿到 application 域。

存进去的数据，任何页面都能取：

```jsp
<%-- index.jsp --%>
应用名称：${applicationScope.appName}　|　当前在线：${applicationScope.onlineCount}
```

### 2. HttpSessionListener：统计在线人数

```java
@Override
public void sessionCreated(HttpSessionEvent se) {
    int count = ONLINE_COUNT.incrementAndGet();
    se.getSession().getServletContext().setAttribute("onlineCount", count);
    System.out.println("【Listener】新会话创建 " + se.getSession().getId() + "，当前在线：" + count);
}

@Override
public void sessionDestroyed(HttpSessionEvent se) {
    int count = ONLINE_COUNT.decrementAndGet();
    se.getSession().getServletContext().setAttribute("onlineCount", count);
}
```

**为什么用 `AtomicInteger` 而不是 `int`？**
监听器和 Servlet 一样是**单例**的，多个用户同时创建 session 时会有多个线程并发执行
`sessionCreated`。普通的 `count++` 不是原子操作，会丢失更新，统计出来的人数偏小。
`AtomicInteger` 内部用 CAS 保证原子性。

**触发时机提醒：**
- `sessionCreated` 在**第一次调用 `request.getSession()`** 时触发，不是打开浏览器就触发
- `sessionDestroyed` 在 `invalidate()` 或**超时**时触发。超时是被动的，
  Tomcat 后台线程定期扫描，所以人走了之后可能几分钟才减 1

> 这也说明：**这种在线人数统计是"估算值"**，只能反映活跃会话数。
> 生产环境的精确统计一般靠 Redis + 心跳。

## 四、注册方式

### 注解（推荐）

```java
@WebListener
public class AppContextListener implements ServletContextListener { }
```

`@WebListener` **没有任何属性**（不像 `@WebServlet` 要配路径），因为监听器不需要路径，
它监听的是对象的生命周期，不是某个 url。

### web.xml

```xml
<listener>
    <listener-class>com.springmvc.listener.AppContextListener</listener-class>
</listener>
```

**多个监听器的执行顺序**按 `<listener>` 的配置顺序；注解方式则不保证顺序。

## 五、最重要的应用：Spring 的 ContextLoaderListener

**这是学 Listener 最大的意义 —— 理解 Spring 是怎么和 Web 容器集成的。**

传统 SSM 项目的 `web.xml` 里必有这一段：

```xml
<!-- 告诉Spring配置文件在哪 -->
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:applicationContext.xml</param-value>
</context-param>

<!-- 就是一个 ServletContextListener -->
<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
```

`ContextLoaderListener` 做的事，和本案例的 `AppContextListener` 一模一样：

```java
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        // ① 服务器启动时，读取 <context-param> 指定的配置文件
        // ② 创建 Spring 容器（WebApplicationContext），扫描并初始化所有Bean
        // ③ 把容器存入 ServletContext 域，key是 WebApplicationContext.class.getName() + ".ROOT"
        initWebApplicationContext(event.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        closeWebApplicationContext(event.getServletContext());   // 关闭容器，销毁Bean
    }
}
```

**所以：Spring 容器就是在 `contextInitialized` 里创建的，然后存进了 application 域。**
之后任何地方都能取出来：

```java
WebApplicationContext ctx = WebApplicationContextUtils
        .getWebApplicationContext(getServletContext());
UserService service = ctx.getBean(UserService.class);
```

理解了这一点，就明白了：

- 为什么 Spring 容器和 Web 应用的生命周期一致（应用启动就有，关闭就销毁）
- 为什么 Servlet 里可以拿到 Spring 管理的 Bean
- SpringMVC 快速入门里的 `AbstractDispatcherServletInitializer`，
  它的 `createRootApplicationContext()` 就对应 `ContextLoaderListener` 的工作

> 本案例 `springmvc_01_quickstart` 中 `createRootApplicationContext()` 返回 null，
> 就是"不需要 Spring 根容器，只用 SpringMVC 容器"的意思。

## 六、其他实战用途

| 场景 | 做法 |
| --- | --- |
| 应用启动时加载字典数据到内存 | `contextInitialized` 里查数据库，存 application 域 |
| 启动定时任务 | `contextInitialized` 里 `ScheduledExecutorService.scheduleAtFixedRate` |
| 优雅关闭 | `contextDestroyed` 里关线程池、注销服务注册中心 |
| 单点登录踢人 | `HttpSessionListener` 维护 `Map<用户id, session>`，新登录时踢掉旧 session |
| 统计请求耗时 | `ServletRequestListener` 的 `requestInitialized`/`requestDestroyed` 记时间戳 |

## 七、动手验证

1. **启动服务**，控制台最先打印的就是监听器的日志（早于 Servlet 的初始化）：

   ```
   ==================================================
   【Listener】应用启动完成：Servlet详细案例
   【Listener】服务器信息：Apache Tomcat/7.0.47
   【Listener】真实磁盘路径：/Users/.../target/springmvc_00_servlet/
   ==================================================
   【1-实例化】LifeCycleServlet 构造方法执行 ...
   ```

2. **访问首页**，页面顶部显示 `应用名称（取自ServletContext域）：Servlet详细案例`

3. **登录**（会调用 `request.getSession()`）→ 控制台：

   ```
   【Listener】新会话创建 A1B2C3D4E5...，当前在线：1
   ```

4. **开一个无痕窗口**再访问登录 → 在线人数变成 2（两个独立会话）

5. **点击退出登录**（触发 `invalidate()`）→ 控制台：

   ```
   【Listener】会话销毁，当前在线：1
   ```

6. **`Ctrl+C` 关闭服务** → 控制台：

   ```
   【Listener】应用关闭，释放全局资源（连接池、定时任务等）
   ```

---

上一篇：[08 Filter 过滤器详解](08-Filter过滤器详解.md)　|　下一篇：[10 JSP 与 EL、JSTL](10-JSP与EL、JSTL.md)

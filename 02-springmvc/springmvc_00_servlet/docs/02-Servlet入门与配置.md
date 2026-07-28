# 02 Servlet 入门与配置

对应代码：`HelloServlet.java`、`XmlConfigServlet.java`、`web.xml`

## 一、Servlet 是什么

**Servlet = Server + Applet，运行在服务器端的小程序。**

它是 Java 提供的一套**规范（接口）**，定义了「Java 类如何被 Web 服务器调用、如何处理请求」。
我们写的类实现这套接口，Tomcat 这样的 Web 服务器就知道该怎么调用它。

```
浏览器  --HTTP请求-->  Tomcat  --解析成request/response对象-->  你的Servlet
                        ↑                                        |
                        └────────  HTTP响应  ←─────────────────────┘
```

**Tomcat 帮我们做了什么？**

1. 监听端口，接收 TCP 连接
2. 按 HTTP 协议解析请求报文，封装成 `HttpServletRequest` 对象
3. 创建空的 `HttpServletResponse` 对象
4. 根据请求路径找到对应的 Servlet，调用它的 `service()` 方法，把两个对象传进去
5. 从 `response` 中取出数据，拼装成 HTTP 响应报文返回给浏览器

**我们只需要做一件事：从 request 取数据，处理，把结果放进 response。**

## 二、Servlet 的继承体系

```
javax.servlet.Servlet                （接口，5个抽象方法）
        ↑
javax.servlet.GenericServlet         （抽象类，与协议无关，实现了除service外的所有方法）
        ↑
javax.servlet.http.HttpServlet       （抽象类，针对HTTP协议，实现了service的分发逻辑）
        ↑
    你的Servlet                       （只需重写doGet/doPost）
```

### 1. Servlet 接口的 5 个方法

```java
public interface Servlet {
    void init(ServletConfig config);              // 初始化，执行1次
    void service(ServletRequest req, ServletResponse res);  // 提供服务，每次请求执行1次
    void destroy();                               // 销毁，执行1次
    ServletConfig getServletConfig();             // 获取配置对象
    String getServletInfo();                      // 获取Servlet信息（作者、版本等，一般不用）
}
```

直接实现这个接口的话，5 个方法全都要写，很麻烦。

### 2. GenericServlet

抽象类，把不常用的方法都做了空实现，只留 `service()` 抽象。
但它是**协议无关**的，参数是 `ServletRequest` 而不是 `HttpServletRequest`，
拿不到请求方式、请求头等 HTTP 特有的信息。

### 3. HttpServlet（实际开发用这个）

它重写了 `service()`，核心逻辑就是**按请求方式分发**（简化后的源码）：

```java
protected void service(HttpServletRequest req, HttpServletResponse resp) {
    String method = req.getMethod();          // 获取请求方式
    if (method.equals("GET")) {
        doGet(req, resp);
    } else if (method.equals("POST")) {
        doPost(req, resp);
    } else if (method.equals("PUT")) {
        doPut(req, resp);
    } else if (method.equals("DELETE")) {
        doDelete(req, resp);
    }
    // 还有 HEAD、OPTIONS、TRACE
}
```

而 `HttpServlet` 里的 `doGet/doPost` 默认实现是**直接返回 405 错误**：

```java
protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "...");  // 405
}
```

**这解释了一个高频错误**：表单用 `method="post"` 提交，但 Servlet 里只重写了 `doGet`，
于是走进父类的 `doPost`，浏览器就报 `HTTP Status 405 - Method Not Allowed`。

> 本案例的 `BaseServlet` 反其道而行之：直接重写 `service()`，
> 不再区分 GET/POST，所有请求方式都走同一套反射分发逻辑。SpringMVC 的
> `DispatcherServlet` 也是重写 `service()`/`doService()` 来接管所有请求的。

## 三、哪些操作会发出 GET 请求，哪些是 POST

| 请求方式 | 触发场景 | 参数位置 | 特点 |
| --- | --- | --- | --- |
| GET | 地址栏输入、`<a>` 标签、`<img src>`、`<link href>`、`<form method="get">`、ajax 默认 | url 后的查询字符串 `?k=v&k2=v2` | 参数可见、长度受限（浏览器约 2~8KB）、可被缓存和收藏 |
| POST | `<form method="post">`、ajax 指定 post | 请求体（body） | 参数不可见、长度不限、不被缓存 |

**注意：POST 只是参数不显示在地址栏，并不等于安全**，用抓包工具照样能看到明文。
真正的安全靠 HTTPS。

## 四、创建 Servlet 的完整步骤

以 `HelloServlet` 为例：

```java
@WebServlet(urlPatterns = "/hello", name = "helloServlet")   // 3.配置访问路径
public class HelloServlet extends HttpServlet {              // 1.继承HttpServlet

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {           // 2.重写doGet/doPost
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<h2>Hello Servlet</h2>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);      // 实际开发中两者逻辑常常一致
    }
}
```

访问：<http://localhost/hello>

## 五、配置方式一：注解（Servlet 3.0+，推荐）

### @WebServlet 的属性

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `value` / `urlPatterns` | String[] | 访问路径，二者等价但**不能同时使用**。只有一个值时可简写 `@WebServlet("/hello")` |
| `name` | String | Servlet 名称，默认是全类名，`getServletName()` 能取到 |
| `loadOnStartup` | int | 启动时机，`>=0` 则服务器启动时创建，值越小越先创建；默认 `-1` 表示首次访问时创建 |
| `initParams` | WebInitParam[] | 初始化参数，通过 `ServletConfig` 读取 |
| `asyncSupported` | boolean | 是否支持异步处理，默认 false |

完整用法见 `LifeCycleServlet`：

```java
@WebServlet(
        urlPatterns = "/lifecycle",
        loadOnStartup = 1,
        initParams = {
                @WebInitParam(name = "author", value = "springmvc-beginner"),
                @WebInitParam(name = "version", value = "1.0")
        }
)
```

### 注解生效的前提

**`web.xml` 的版本必须是 3.0 及以上**，否则服务器不会扫描注解。本案例的 `web.xml` 头部：

```xml
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" ... version="3.1">
```

另外，如果写了 `metadata-complete="true"`，表示「描述符已完整，不用扫描注解」，注解同样失效。

## 六、配置方式二：web.xml（Servlet 3.0 之前唯一方式）

对应代码 `XmlConfigServlet`，它**没有任何注解**，路径全靠 `web.xml` 配置：

```xml
<servlet>
    <!-- 内部名称，在web.xml中唯一，用来把下面两块配置关联起来 -->
    <servlet-name>xmlConfigServlet</servlet-name>
    <!-- 全类名，Tomcat通过反射 Class.forName(...).newInstance() 创建对象 -->
    <servlet-class>com.springmvc.servlet.XmlConfigServlet</servlet-class>
    <!-- 初始化参数 -->
    <init-param>
        <param-name>desc</param-name>
        <param-value>我是web.xml中配置的初始化参数</param-value>
    </init-param>
    <!-- 启动时创建 -->
    <load-on-startup>2</load-on-startup>
</servlet>

<servlet-mapping>
    <servlet-name>xmlConfigServlet</servlet-name>
    <url-pattern>/xmlServlet</url-pattern>
</servlet-mapping>
```

访问：<http://localhost/xmlServlet>

### 两种方式的对应关系

| 注解 | web.xml |
| --- | --- |
| `@WebServlet(urlPatterns="/x")` | `<url-pattern>/x</url-pattern>` |
| `@WebServlet(name="xxx")` | `<servlet-name>xxx</servlet-name>` |
| `loadOnStartup = 1` | `<load-on-startup>1</load-on-startup>` |
| `initParams = @WebInitParam(...)` | `<init-param>...</init-param>` |

**为什么还要学 web.xml？** 因为：
- 老项目全是 xml 配置
- 第三方组件（如 SpringMVC 的 `DispatcherServlet`）的类不是你写的，没法加注解，只能用 xml 或配置类注册
- 理解了 web.xml，才能看懂 SpringMVC 快速入门里的 `AbstractDispatcherServletInitializer` 到底替代了什么

## 七、url-pattern 的四种匹配规则（重点）

| # | 规则 | 写法 | 匹配示例 | 优先级 |
| --- | --- | --- | --- | :---: |
| 1 | 精确匹配 | `/hello` | 只匹配 `/hello` | 最高 |
| 2 | 目录匹配 | `/user/*` | `/user/list`、`/user/a/b` | 次之 |
| 3 | 扩展名匹配 | `*.do` | `/a.do`、`/user/save.do` | 再次 |
| 4 | 缺省匹配 | `/` | 所有没被上面匹配到的请求 | 最低 |

**注意事项：**

- 路径必须以 `/` 开头（扩展名匹配除外）
- **扩展名匹配不能以 `/` 开头**，`/*.do` 是非法配置，启动直接报错
- 一个 Servlet 可以配多个路径：`@WebServlet({"/a", "/b"})`
- `/*` 和 `/` 的区别很关键：
  - `/*` 拦截**所有**请求，包括 `.jsp`
  - `/` 拦截所有请求，但**不拦截 `.jsp`**（JSP 由容器内置的 JspServlet 处理，它配的是 `*.jsp`，属于扩展名匹配，优先级高于缺省匹配）

**SpringMVC 的 `DispatcherServlet` 用的就是 `/`**，正是为了让 JSP 继续由容器处理，
同时接管其余所有请求。本案例 `ServletContainersInitConfig`（在 `springmvc_01_quickstart` 模块）中
`getServletMappings()` 返回 `new String[]{"/"}` 就是这个意思。

### 本案例的实际使用

| Servlet | url-pattern | 匹配规则 |
| --- | --- | --- |
| `HelloServlet` | `/hello` | 精确 |
| `LifeCycleServlet` | `/lifecycle` | 精确 |
| `XmlConfigServlet` | `/xmlServlet` | 精确（web.xml 配置） |
| `RequestDemoServlet` | `/req` | 精确 |
| `ResponseDemoServlet` | `/resp` | 精确 |
| **`UserServlet`** | **`/user/*`** | **目录匹配 —— 一个 Servlet 处理整个模块的所有请求** |
| `CharacterEncodingFilter` | `/*` | 拦截全部 |
| `LoginCheckFilter` | `/user/*` | 只拦截用户模块 |

`UserServlet` 用目录匹配是本案例的核心设计，详见 `11-综合案例代码走读.md`。

## 八、ServletConfig 与 ServletContext

这是两个容易混淆的对象。

| | ServletConfig | ServletContext |
| --- | --- | --- |
| 数量 | **每个 Servlet 一个** | **整个应用只有一个** |
| 作用 | 获取当前 Servlet 的配置信息 | 获取应用级信息、共享数据 |
| 获取方式 | `getServletConfig()` | `getServletContext()` |
| 参数配置 | `<init-param>` / `initParams` | `<context-param>` |
| 生命周期 | 随 Servlet 创建销毁 | 应用启动时创建，关闭时销毁 |

### ServletConfig 常用方法

```java
config.getServletName()              // Servlet名称
config.getInitParameter("author")    // 单个初始化参数
config.getInitParameterNames()       // 所有参数名
config.getServletContext()           // 拿到ServletContext
```

⚠️ **重写 `init(ServletConfig)` 时必须调用 `super.init(config)`**，
否则 `HttpServlet` 内部保存 config 的字段是 null，后续 `getServletConfig()`、
`getServletContext()` 全会返回 null 或抛异常。见 `LifeCycleServlet.init()` 的注释。

> 更省事的做法是重写无参的 `init()`，`GenericServlet` 已经帮你处理好了 config 的保存：
> ```java
> @Override
> public void init() throws ServletException {
>     // 直接写自己的初始化逻辑，不用管super
> }
> ```

### ServletContext 常用方法

```java
context.getRealPath("/")             // 获取文件在服务器上的真实磁盘路径（文件上传下载常用）
context.getMimeType("a.jpg")         // 根据文件名获取MIME类型 → image/jpeg
context.setAttribute("k", v)         // 作为域对象存数据，整个应用共享
context.getAttribute("k")
context.getInitParameter("k")        // 读取<context-param>
context.getServerInfo()              // 服务器信息，如 Apache Tomcat/7.0.47
```

本案例在 `AppContextListener` 中用它存了应用名和在线人数，
在 `index.jsp` 中用 `${applicationScope.appName}` 取出，详见 `09-Listener监听器详解.md`。

---

上一篇：[01 环境搭建与运行](01-环境搭建与运行.md)　|　下一篇：[03 Servlet 生命周期](03-Servlet生命周期.md)

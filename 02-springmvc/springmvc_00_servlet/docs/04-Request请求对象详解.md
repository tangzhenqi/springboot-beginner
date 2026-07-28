# 04 Request 请求对象详解

对应代码：`RequestDemoServlet.java`、`WEB-INF/pages/request.jsp`

访问地址：<http://localhost/req?username=张三&hobby=java&hobby=mysql>

## 一、request 是什么

浏览器发来的是一段**符合 HTTP 协议的纯文本**：

```http
POST /req?from=index HTTP/1.1          ← 请求行：请求方式 + URI + 协议版本
Host: localhost                        ┐
User-Agent: Mozilla/5.0 ...            │
Content-Type: application/x-www-form-urlencoded │ 请求头
Cookie: JSESSIONID=A1B2C3...           │
Referer: http://localhost/             ┘
                                       ← 空行，分隔头和体
username=张三&hobby=java&hobby=mysql    ← 请求体（只有POST等有）
```

**Tomcat 把这段文本解析后，封装成一个 `HttpServletRequest` 对象**，我们通过它的方法取数据，
不用自己解析字符串。

### 继承体系

```
javax.servlet.ServletRequest              （接口，协议无关）
        ↑
javax.servlet.http.HttpServletRequest     （接口，HTTP专用，多了getMethod、getHeader、getCookies等）
        ↑
org.apache.catalina.connector.RequestFacade   （Tomcat的实现类，用了门面模式）
```

打印 `request.getClass()` 会看到是 `RequestFacade`。Tomcat 内部真正的实现类是 `Request`，
它有 `setXxx` 之类的危险方法，为了不暴露给开发者，用**门面模式**包了一层只读的 `RequestFacade`。

**一次请求对应一个 request 对象**，请求结束就销毁。

## 二、获取请求行数据

| 方法 | 返回示例 | 用途 |
| --- | --- | --- |
| `getMethod()` | `GET` / `POST` | 判断请求方式，REST 风格中区分增删改查 |
| `getContextPath()` | `""` 或 `/demo` | **虚拟目录**，动态拼接路径必用 |
| `getServletPath()` | `/req` | Servlet 映射路径 |
| `getPathInfo()` | `/list`（`/user/*` 时） | 目录匹配中 `*` 匹配到的部分 |
| `getRequestURI()` | `/req` 或 `/demo/req` | 虚拟目录 + 资源路径 |
| `getRequestURL()` | `http://localhost/req` | 完整 url（StringBuffer） |
| `getQueryString()` | `username=张三&hobby=java` | 问号后的字符串，POST 返回 null |
| `getProtocol()` | `HTTP/1.1` | 协议版本 |
| `getRemoteAddr()` | `127.0.0.1` | **客户端 IP**，日志、限流、风控常用 |

### getContextPath 为什么重要

```java
// ❌ 写死路径：项目部署到 /demo 下就全挂了
response.sendRedirect("/user/list");

// ✅ 动态拼接
response.sendRedirect(request.getContextPath() + "/user/list");
```

JSP 里对应的写法是 `${pageContext.request.contextPath}`，本案例所有页面的链接都用了它。

### URI 与 URL 的区别

- **URI**（统一资源**标识**符）：`/demo/req`，范围大，只标识资源
- **URL**（统一资源**定位**符）：`http://localhost/demo/req`，范围小，是 URI 的子集，能定位到资源

### 关于真实 IP

`getRemoteAddr()` 拿到的是**直接连接过来的那台机器的 IP**。如果生产环境前面有 Nginx 反向代理，
拿到的就是 Nginx 的内网 IP。真实客户端 IP 要从代理加的请求头里取：

```java
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
    ip = request.getHeader("X-Real-IP");
}
if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
    ip = request.getRemoteAddr();
}
// X-Forwarded-For可能是"客户端IP, 代理1, 代理2"，取第一个
```

## 三、获取请求头数据

```java
String value = request.getHeader("User-Agent");   // 获取单个，key不区分大小写
Enumeration<String> names = request.getHeaderNames();  // 获取所有头名称
```

> `Enumeration` 是 JDK 1.0 的老式迭代器，Servlet API 定型早所以一直沿用。
> 遍历方式：`while (names.hasMoreElements()) { String n = names.nextElement(); }`

### 常用请求头及其用途

| 请求头 | 作用 | 典型应用 |
| --- | --- | --- |
| `User-Agent` | 浏览器/操作系统信息 | 判断 PC 还是手机，返回不同页面；下载时判断浏览器解决文件名乱码 |
| `Referer` | **从哪个页面跳过来的** | 防盗链（判断来源是不是自己站点）、统计流量来源 |
| `Cookie` | 携带的所有 Cookie | Session 就靠里面的 JSESSIONID 识别用户 |
| `Content-Type` | 请求体的数据格式 | `application/x-www-form-urlencoded`（普通表单）、`multipart/form-data`（文件上传）、`application/json`（ajax 传 json） |
| `Content-Length` | 请求体长度 | |
| `Accept` | 浏览器能接收的数据类型 | 内容协商 |
| `Accept-Encoding` | 支持的压缩方式 | gzip 压缩 |
| `X-Requested-With` | 值为 `XMLHttpRequest` 表示 ajax 请求 | **本案例 `LoginCheckFilter` 用它区分 ajax 和页面请求** |

`LoginCheckFilter` 中的实际应用：

```java
// ajax请求返回json，页面请求做重定向，避免ajax收到一整个登录页的html
if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
    response.getWriter().write("{\"code\":40100,\"msg\":\"未登录\"}");
} else {
    response.sendRedirect(request.getContextPath() + "/user/toLogin");
}
```

## 四、获取请求参数（最常用）

**关键点：GET 和 POST 的获取方式完全一样**，Tomcat 已经屏蔽了差异。

| 方法 | 返回值 | 适用场景 |
| --- | --- | --- |
| `getParameter(String name)` | `String`，不存在返回 **null** | 单值参数：文本框、单选框、下拉框 |
| `getParameterValues(String name)` | `String[]`，不存在返回 **null** | 多值参数：**复选框**、多选下拉 |
| `getParameterNames()` | `Enumeration<String>` | 遍历所有参数名 |
| `getParameterMap()` | `Map<String, String[]>` | **一次拿全部**，框架封装实体用的就是它 |

### 三个必须注意的坑

**坑 1：所有参数都是 String，需要手动转类型**

```java
// ❌ 参数不存在时 Integer.valueOf(null) 抛 NumberFormatException
Integer age = Integer.valueOf(request.getParameter("age"));

// ✅ 本案例 UserServlet 的做法
private Integer parseInt(String value) {
    if (value == null || value.trim().isEmpty()) {
        return null;
    }
    try {
        return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
        return null;
    }
}
```

**坑 2：`getParameter` 可能返回 null，直接 `.equals()` 会空指针**

```java
// ❌ username为null时空指针
if (username.equals("admin")) { ... }

// ✅ 常量在前
if ("admin".equals(username)) { ... }
```

**坑 3：参数存在但没填值，返回的是空串 `""` 而不是 null**

表单里文本框留空提交，`getParameter("username")` 返回 `""`。
所以校验要同时判断 null 和空串：

```java
if (username == null || username.trim().isEmpty()) { ... }
```

### 手动封装实体 vs 框架自动封装

本案例 `UserServlet.buildUserFromRequest()` 是纯手工活：

```java
private User buildUserFromRequest(HttpServletRequest request) {
    User user = new User();
    user.setId(parseInt(request.getParameter("id")));
    user.setUsername(request.getParameter("username"));
    user.setGender(request.getParameter("gender"));
    user.setAge(parseInt(request.getParameter("age")));
    user.setAddress(request.getParameter("address"));
    return user;
}
```

**字段一多，这段代码就会长得没法看，而且每个 Servlet 都要写一遍。**

SpringMVC 里这段代码彻底消失，只要**表单的 name 和实体的属性名一致**：

```java
@RequestMapping("/save")
public String save(User user) {     // 就这样，自动封装 + 类型转换
    userService.save(user);
    return "redirect:/user/list";
}
```

它底层做的事和 `buildUserFromRequest` 一模一样：拿 `getParameterMap()`，
用反射+内省找到实体的 setter，做类型转换后调用。

## 五、中文乱码（必考）

### 乱码的本质

编码和解码用的字符集不一致：

```
浏览器：  "张三" --UTF-8编码--> E5 BC A0 E4 B8 89（字节）
服务器：  E5 BC A0 E4 B8 89 --ISO-8859-1解码--> "å¼ ä¸‰"（乱码）
```

`ISO-8859-1` 是单字节的西欧字符集，**不包含任何中文**，用它解码中文必乱。

### POST 请求乱码

POST 的参数在**请求体**里，解码字符集由 `request.setCharacterEncoding()` 决定，
Servlet 3.x 之前默认是 `ISO-8859-1`。

```java
// ⚠️ 必须在第一次调用getParameter之前执行，否则无效！
request.setCharacterEncoding("UTF-8");
String username = request.getParameter("username");
```

**为什么必须在取参数之前？** 因为 Tomcat 是**懒解析**的：第一次调用 `getParameter` 时才
真正去解析请求体，解析完就把结果缓存起来。之后再改编码，已经解析好的结果不会重新解析。

**每个 Servlet 都写一遍太啰嗦 → 用过滤器统一处理**，这正是 `CharacterEncodingFilter` 的价值：

```java
@WebFilter(urlPatterns = "/*", initParams = @WebInitParam(name = "encoding", value = "UTF-8"))
public class CharacterEncodingFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        request.setCharacterEncoding(encoding);    // 放行前统一设置
        response.setCharacterEncoding(encoding);
        chain.doFilter(request, response);
    }
}
```

> Spring 提供了现成的 `org.springframework.web.filter.CharacterEncodingFilter`，
> 用法一样，SpringBoot 里更是默认就配好了，完全不用操心。

### GET 请求乱码

GET 的参数在 **url** 上，解码字符集由**服务器配置**决定，`setCharacterEncoding` 对它无效。

| Tomcat 版本 | url 默认解码字符集 | 中文是否乱码 |
| --- | --- | --- |
| Tomcat 7 及以下 | ISO-8859-1 | **乱码** |
| Tomcat 8 及以上 | UTF-8 | 正常 |

**Tomcat 8+ 无需任何处理。** 老版本的两种解决方案：

方案一，改 Tomcat 的 `conf/server.xml`（本案例在 pom 的插件配置里配了等价的 `<uriEncoding>UTF-8</uriEncoding>`）：

```xml
<Connector port="8080" protocol="HTTP/1.1" URIEncoding="UTF-8"/>
```

方案二，手动转码（不推荐，代码丑）：

```java
String username = request.getParameter("username");
username = new String(username.getBytes("ISO-8859-1"), "UTF-8");
```

### 一张表记住

| 位置 | 乱码原因 | 解决 |
| --- | --- | --- |
| POST 请求参数 | 请求体解码字符集 | `request.setCharacterEncoding("UTF-8")`，取参数前调用 |
| GET 请求参数 | url 解码字符集 | Tomcat 8+ 无需处理；老版本配 `URIEncoding` |
| 响应输出 | 响应体编码字符集 | `response.setContentType("text/html;charset=UTF-8")`，获取流之前调用 |
| JSP 页面 | 页面编码 | `<%@ page contentType="text/html;charset=UTF-8" %>` |
| 下载文件名 | 文件名编码 | `new String(name.getBytes("UTF-8"), "ISO-8859-1")` |

## 六、request 作为域对象

request 实现了域对象接口，可以存取数据，**作用范围是一次请求（含转发）**。

```java
request.setAttribute("msg", "数据");     // 存
Object value = request.getAttribute("msg");  // 取，不存在返回null
request.removeAttribute("msg");          // 删
```

**典型用法：Servlet 查出数据 → 存入 request 域 → 转发给 JSP → JSP 用 EL 取出展示。**

`UserServlet.list()` 就是标准模板：

```java
public String list(HttpServletRequest request, HttpServletResponse response) {
    List<User> userList = userService.findAll();
    request.setAttribute("userList", userList);        // 存
    return "/WEB-INF/pages/list.jsp";                  // 转发（由BaseServlet执行）
}
```

`list.jsp` 中取出：

```jsp
<c:forEach items="${userList}" var="user">
    <td>${user.username}</td>
</c:forEach>
```

> 这套「存数据 + 返回视图名」，在 SpringMVC 里就是：
> ```java
> public String list(Model model) {
>     model.addAttribute("userList", userService.findAll());
>     return "list";
> }
> ```
> `Model` 底层数据最终就是放进了 request 域。

⚠️ **注意：attribute 和 parameter 完全是两回事**

| | parameter | attribute |
| --- | --- | --- |
| 数据来源 | 浏览器传来的 | 服务器程序自己存的 |
| 能否修改 | 只读 | 可读可写 |
| 值类型 | 只能是 String | 任意 Object |
| API | `getParameter` | `getAttribute` / `setAttribute` |

## 七、请求转发 forward

```java
request.getRequestDispatcher("/WEB-INF/pages/request.jsp").forward(request, response);
```

**转发是服务器内部的资源跳转**，浏览器完全不知情：

```
浏览器 ──请求 /req──> [ Servlet ──内部转发──> JSP ] ──响应──> 浏览器
                       └────── 一次请求，共用同一对 request/response ──────┘
```

### 三个特征

1. **浏览器地址栏不变**（还是 `/req?forward=true`）
2. **只有一次请求**，所以 request 域数据能共享
3. **可以访问 `WEB-INF` 下的资源**

### 路径写法

转发的路径是**给服务器用的**，所以：
- **不需要**加虚拟目录（`getContextPath()`）
- `/` 代表当前项目的根

```java
// ✅ 正确
request.getRequestDispatcher("/WEB-INF/pages/list.jsp").forward(request, response);

// ❌ 多加了虚拟目录，会404
request.getRequestDispatcher(request.getContextPath() + "/WEB-INF/pages/list.jsp")...
```

与重定向的完整对比见 `06-域对象与转发重定向.md`。

## 八、案例实操

访问 <http://localhost/req?username=张三&hobby=java&hobby=mysql>，页面会依次展示：

1. **请求行数据** —— 观察 `getRequestURI` 和 `getRequestURL` 的差别
2. **请求头数据** —— 找一找 `User-Agent` 和 `Cookie`
3. **请求参数** —— `hobby` 传了两个值，`getParameter` 只能拿到第一个，
   `getParameterValues` 才能拿到 `[java, mysql]`
4. **点击转发链接** —— 跳转后**注意地址栏没变**，且页面能取出 request 域里的 `msg`

再从首页的 POST 表单提交「李四中文」，验证 `CharacterEncodingFilter` 是否解决了 POST 乱码
（把过滤器里的 `request.setCharacterEncoding(encoding)` 注释掉重启，就能看到乱码了）。

---

上一篇：[03 Servlet 生命周期](03-Servlet生命周期.md)　|　下一篇：[05 Response 响应对象详解](05-Response响应对象详解.md)

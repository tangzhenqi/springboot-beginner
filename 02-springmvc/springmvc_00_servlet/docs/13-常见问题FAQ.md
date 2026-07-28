# 13 常见问题 FAQ

这一篇按「报错现象」组织，遇到问题直接查。

## 一、404 Not Found

**含义：服务器找不到你请求的资源。** 排查顺序：

| # | 检查项 | 说明 |
| --- | --- | --- |
| 1 | **url 拼写** | 大小写敏感！`/Hello` ≠ `/hello` |
| 2 | **虚拟目录** | 项目部署在 `/demo` 下，就得访问 `http://localhost/demo/hello` |
| 3 | **url-pattern 配置** | 是不是漏了开头的 `/`？`@WebServlet("hello")` 是非法的 |
| 4 | **注解是否生效** | `web.xml` 的 version 必须 ≥ 3.0，且没有 `metadata-complete="true"` |
| 5 | **项目是否真的部署了** | 看 `target/` 下有没有 class 文件；IDEA 里检查 Deployment 配置 |
| 6 | **访问了 WEB-INF 下的资源** | 浏览器无法直接访问 `WEB-INF`，只能转发 |
| 7 | **BaseServlet 找不到方法** | 方法名和 url 最后一段不一致，或方法不是 `public` |

### 本案例特有的 404

访问 `/user/xxx` 时报「找不到处理方法：UserServlet#xxx()」，
说明 `BaseServlet` 反射没找到方法。检查 `UserServlet` 里的方法是否满足：

```java
public String 方法名(HttpServletRequest request, HttpServletResponse response)
```

- 必须 `public`
- 返回值必须是 `String`
- 参数必须是这两个，顺序不能反

## 二、405 Method Not Allowed

**原因：请求方式和你重写的方法对不上。**

最典型的场景：表单是 `method="post"`，但 Servlet 里只重写了 `doGet`，
于是走进 `HttpServlet` 默认的 `doPost`，它的实现就是返回 405。

```java
// ✅ 解决方案：两个都重写，让doPost调用doGet
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
    doGet(request, response);
}
```

> 本案例的 `UserServlet` 不会遇到这个问题，因为 `BaseServlet` 重写的是 `service()`，
> 不区分请求方式。

## 三、500 Internal Server Error

**含义：你的代码抛异常了。** 页面上的信息通常没用，**去看控制台的异常堆栈**。

### 高频异常速查

#### `NullPointerException`

```java
// 场景1：参数不存在
String username = request.getParameter("username");   // 返回null
if (username.equals("admin")) { }                     // 💥

// ✅ 常量在前
if ("admin".equals(username)) { }
```

```java
// 场景2：cookie数组为null
Cookie[] cookies = request.getCookies();              // 一个cookie都没有时返回null
for (Cookie c : cookies) { }                          // 💥

// ✅ 判空
if (cookies != null) { for (Cookie c : cookies) { } }
```

```java
// 场景3：忘记调用super.init(config)
public void init(ServletConfig config) {
    // 少了 super.init(config)
}
getServletContext();                                  // 💥 返回null
```

#### `NumberFormatException`

```java
Integer.valueOf(request.getParameter("age"));   // 参数为null或""或"abc"时 💥
```

本案例的 `parseInt()` 方法就是为了兜住这个：

```java
private Integer parseInt(String value) {
    if (value == null || value.trim().isEmpty()) return null;
    try {
        return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
        return null;
    }
}
```

#### `IllegalStateException: Cannot forward after response has been committed`

**原因：响应已经提交（数据已发给浏览器）之后又想转发/重定向。**

```java
// ❌ 已经写了内容再转发
response.getWriter().write("已经输出了");
request.getRequestDispatcher("/a.jsp").forward(request, response);   // 💥

// ❌ 转发/重定向后没有return，代码继续执行
response.sendRedirect("/a.jsp");
request.getRequestDispatcher("/b.jsp").forward(request, response);   // 💥

// ✅ 跳转之后立即return
response.sendRedirect("/a.jsp");
return;
```

**过滤器里最容易犯**：

```java
// ❌ 拦截后忘记return，又放行了
if (未登录) {
    response.sendRedirect("/login");
}
chain.doFilter(request, response);   // 💥

// ✅
if (未登录) {
    response.sendRedirect("/login");
    return;
}
chain.doFilter(request, response);
```

#### `IllegalStateException: getOutputStream() has already been called`

**原因：一次响应中同时用了字符流和字节流。** 二者互斥，只能用一个。

#### `IllegalArgumentException: Control character in cookie value`

**原因：Cookie 值里有中文或特殊字符。** 用 `URLEncoder.encode()` 编码后再存
（本案例 `UserServlet.encode()`）。

#### `ClassNotFoundException: javax.servlet.Servlet`

**原因：`servlet-api` 的 scope 不是 `provided`**，和 Tomcat 自带的冲突。
见 `01-环境搭建与运行.md`。

## 四、中文乱码

**先定位是哪一环乱了**，再对症下药：

| 乱码位置 | 判断方法 | 解决 |
| --- | --- | --- |
| **POST 请求参数** | 表单提交中文，后台 `System.out.println` 是乱码 | `request.setCharacterEncoding("UTF-8")`，**必须在取参数之前** |
| **GET 请求参数** | url 带中文，后台打印乱码 | Tomcat 8+ 默认 UTF-8 无需处理；老版本配 `URIEncoding="UTF-8"` |
| **响应输出** | 后台打印正常，浏览器显示乱码 | `response.setContentType("text/html;charset=UTF-8")`，**必须在获取流之前** |
| **JSP 页面** | 页面上写死的中文乱码 | `<%@ page contentType="text/html;charset=UTF-8" %>` |
| **下载文件名** | 下载的文件名是乱码 | `new String(name.getBytes("UTF-8"), "ISO-8859-1")` |
| **控制台日志** | IDEA 控制台中文乱码 | IDEA 设置文件编码为 UTF-8，或加 JVM 参数 `-Dfile.encoding=UTF-8` |
| **源码文件** | 代码里的中文注释是乱码 | Maven 加 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` |

**记住两个"必须在...之前"**，这是最容易踩的坑：

```java
// ❌ 无效
String name = request.getParameter("name");
request.setCharacterEncoding("UTF-8");     // 太晚了，参数已经解析完了

// ❌ 无效
PrintWriter writer = response.getWriter();
response.setContentType("text/html;charset=UTF-8");   // 太晚了，流已经创建了
```

## 五、Filter 相关

### Filter 不生效

| 检查项 | 说明 |
| --- | --- |
| 实现的接口对不对 | 必须是 `javax.servlet.Filter`，IDEA 可能导入其他包 |
| `urlPatterns` 匹配吗 | `/user/*` 匹配不到 `/userlist` |
| `web.xml` version | ≥ 3.0 注解才生效 |
| 是不是转发进来的 | **默认只拦截 REQUEST，不拦截 FORWARD**，见 `08-Filter过滤器详解.md` |

### Filter 顺序不对

注解方式按**类名字典序**。要精确控制顺序，改用 `web.xml` 的 `<filter-mapping>` 顺序。

### 浏览器报 `ERR_TOO_MANY_REDIRECTS`（无限重定向）

**原因：登录过滤器把登录页本身也拦截了。**

```
访问 /user/toLogin → 未登录 → 重定向到 /user/toLogin → 未登录 → 重定向... ♾️
```

**解决：把登录相关路径加入白名单**（本案例 `LoginCheckFilter.WHITE_LIST`）：

```java
private static final List<String> WHITE_LIST = Arrays.asList("/user/toLogin", "/user/login");
```

同理，静态资源（css/js/图片）也不该被登录过滤器拦截。

## 六、Session 相关

### 每次请求 session 都是新的 / 登录状态保不住

| 原因 | 排查 |
| --- | --- |
| 浏览器禁用了 Cookie | 看 F12 → Application → Cookies 有没有 `JSESSIONID` |
| Cookie 的 path 不对 | JSESSIONID 的 path 应该是项目根路径 |
| 用了无痕窗口/换了浏览器 | 每个浏览器是独立的会话 |
| 重启了服务器 | 内存里的 session 全没了 |
| 代码里误调了 `invalidate()` | 搜一下 |

### 关闭浏览器后 session 还在？

**是的，这是正常的。** 关闭浏览器只是删掉了会话级的 JSESSIONID Cookie，
服务器内存里的 Session 对象要等超时（默认 30 分钟）才回收。

### 在线人数统计不准

`sessionDestroyed` 依赖超时触发，Tomcat 后台线程定期扫描，有延迟。
教学演示够用，生产环境要用 Redis + 心跳。

## 七、JSP 相关

### `${xxx}` 原样显示，没有被解析

| 原因 | 解决 |
| --- | --- |
| `web.xml` 版本太低（2.3 及以下默认关闭 EL） | 改成 3.0+ |
| 页面上写了 `<%@ page isELIgnored="true" %>` | 改成 false 或删掉 |
| 在 `.html` 文件里写的 EL | EL 只在 JSP 中生效 |

### `${xxx}` 显示为空

| 原因 | 排查 |
| --- | --- |
| Servlet 里没存进域 | 检查 `setAttribute` 的 key 和 EL 里的名字是否一致（大小写！） |
| **用了重定向而不是转发** | 重定向是两次请求，request 域数据丢失 |
| 实体类没有 getter | EL 取属性靠的是 getter 方法，不是字段 |
| 域范围选错了 | 显式写 `${requestScope.xxx}` 试试 |

### `<c:forEach>` 报错 / 标签原样输出

| 原因 | 解决 |
| --- | --- |
| 没引 jstl 依赖 | pom 加 `javax.servlet:jstl:1.2` |
| 页面没写 taglib 指令 | `<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>` |
| uri 写错 | JSTL 1.2 是 `http://java.sun.com/jsp/jstl/core`（注意有 `/jsp/`） |
| jstl 的 scope 写成了 provided | Tomcat 不提供 JSTL，必须打进 war 包 |

### 修改 JSP 不生效

Tomcat 有翻译后的缓存。删掉 `work` 目录或重启服务；
IDEA 里把 `On 'Update' action` 设为 `Update classes and resources`。

## 八、构建与启动

### `Address already in use`

端口被占用。改 pom 里的 `<port>`，或找出占用进程：

```bash
lsof -i :80          # macOS / Linux
netstat -ano | findstr :80    # Windows
```

### `Cannot access ... in offline mode`

Maven 处于离线模式且本地仓库没有该依赖。去掉 `-o` 参数联网构建一次。

### 改了 Java 代码不生效

`tomcat7:run` 不支持热部署，必须停掉重新执行。
IDEA 配置本地 Tomcat 时可以开启热部署（只对方法体内的修改有效，加字段、改方法签名仍需重启）。

### `war` 包里没有 class 文件

检查 `<packaging>war</packaging>` 是否配置，
以及 Java 源码是否在标准目录 `src/main/java` 下。

## 九、调试技巧

### 1. 用 F12 的 Network 面板

这是 Web 开发最重要的调试工具：

| 看什么 | 能发现什么 |
| --- | --- |
| **Status Code** | 404/405/500 一目了然 |
| **Request Headers** | Cookie 带了没、Content-Type 对不对 |
| **Request Payload / Form Data** | 参数到底传了什么 |
| **Response Headers** | Set-Cookie、Location、Content-Type |
| **Response** | 服务器实际返回了什么 |
| 请求条数 | 重定向会有两条记录 |

### 2. 打断点的位置

| 想确认什么 | 断点打哪 |
| --- | --- |
| 请求有没有进来 | Servlet 方法第一行 |
| 参数对不对 | `request.getParameter()` 之后 |
| 过滤器有没有放行 | `chain.doFilter()` 那行 |
| 反射找没找到方法 | `BaseServlet` 的 `method.invoke` |
| 数据查出来没有 | `userService.findAll()` 之后 |

### 3. 加日志比断点更实用的场景

过滤器、监听器这类「一闪而过」的组件，打日志比断点方便：

```java
System.out.println("【Filter】拦截未登录请求：" + path);
```

本案例所有 Filter 和 Listener 都加了带前缀的日志，便于在控制台里 grep。

### 4. 确认注解是否被扫描

启动时看控制台有没有打印 `init` 里的日志。
没打印说明组件压根没被注册（检查 `web.xml` 版本）。

---

上一篇：[12 从 Servlet 到 SpringMVC](12-从Servlet到SpringMVC.md)　|　返回：[README](../README.md)

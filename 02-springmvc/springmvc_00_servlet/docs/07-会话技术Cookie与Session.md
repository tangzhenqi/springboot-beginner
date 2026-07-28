# 07 会话技术：Cookie 与 Session

对应代码：`UserServlet.login()` / `logout()` / `toLogin()`、`LoginCheckFilter`

## 一、为什么需要会话技术

**HTTP 协议是无状态的**：每次请求都是独立的，服务器不记得你上一次做过什么。

```
请求1：登录，用户名admin密码123456   → 服务器：验证通过 ✅
请求2：查看用户列表                  → 服务器：你是谁？？？
```

这显然不行。**会话技术就是用来在多次请求之间保存状态的。**

> **一次会话**：浏览器第一次给服务器发请求，会话建立；直到有一方断开（关闭浏览器 / 服务器宕机 / 会话超时），会话结束。
> 一次会话中可以包含多次请求。

会话技术分两类：

| | 数据存在哪 | 代表 |
| --- | --- | --- |
| **客户端会话技术** | 浏览器 | Cookie |
| **服务器端会话技术** | 服务器 | Session |

## 二、Cookie

### 1. 原理

Cookie 本质就是**两个 HTTP 头的配合**：

```http
① 服务器响应时，通过 Set-Cookie 响应头把数据发给浏览器
   HTTP/1.1 200 OK
   Set-Cookie: rememberedUsername=admin; Max-Age=604800; Path=/

② 浏览器保存下来，之后每次请求都通过 Cookie 请求头自动带回来
   GET /user/toLogin HTTP/1.1
   Cookie: rememberedUsername=admin; JSESSIONID=A1B2C3D4
```

**注意「自动带回来」这四个字** —— 不需要写任何代码，浏览器自己会做。这是 Cookie 的核心价值。

### 2. API

```java
// 创建并发送
Cookie cookie = new Cookie("key", "value");   // 只能是String
response.addCookie(cookie);                   // 底层就是加 Set-Cookie 响应头

// 获取（没有 getCookie(name) 这种方法，只能拿全部再遍历）
Cookie[] cookies = request.getCookies();      // ⚠️ 一个cookie都没有时返回 null，不是空数组！
if (cookies != null) {
    for (Cookie c : cookies) {
        if ("key".equals(c.getName())) {
            String value = c.getValue();
        }
    }
}
```

### 3. setMaxAge：存活时间（重点）

| 值 | 含义 | 存储位置 |
| --- | --- | --- |
| **负数**（默认 -1） | 会话级 Cookie，**关闭浏览器就消失** | 浏览器内存 |
| **0** | **立即删除**该 Cookie | — |
| **正数** | 持久化 N 秒，关闭浏览器也在 | 浏览器所在电脑的硬盘 |

`UserServlet.login()` 里同时用到了正数和 0：

```java
boolean remember = "true".equals(request.getParameter("remember"));
Cookie cookie = new Cookie("rememberedUsername", remember ? encode(username) : "");
cookie.setMaxAge(remember ? 7 * 24 * 60 * 60 : 0);   // 勾选：存7天；不勾选：删除已有的
cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
response.addCookie(cookie);
```

> **"删除 Cookie"的标准做法**：新建一个同名、同 path 的 Cookie，
> `setMaxAge(0)` 后发给浏览器，浏览器就会把原来那个删掉。没有 `deleteCookie` 方法。

### 4. setPath：携带范围（易错）

`path` 决定了**访问哪些路径时浏览器会带上这个 Cookie**：

```java
cookie.setPath("/");           // 整个服务器下的所有项目都会带
cookie.setPath("/demo");       // 只有访问 /demo 开头的路径才带
cookie.setPath("/demo/user");  // 只有访问 /demo/user 开头的路径才带
```

**默认值是「当前请求路径的目录」**，这是个大坑：
在 `/user/login` 里创建的 Cookie，默认 path 是 `/user`，那么访问 `/index.jsp` 时就带不上了。

所以本案例显式设置成项目根路径，保证整个项目的请求都能拿到。

### 5. setDomain：共享范围

```java
cookie.setDomain(".baidu.com");   // news.baidu.com 和 tieba.baidu.com 都能拿到
```

用于同一一级域名下的多个二级域名之间共享 Cookie（单点登录的简易实现）。

### 6. Cookie 存中文的问题

**Cookie 的值不允许包含中文、空格、分号、逗号等特殊字符**，
Tomcat 8+ 会直接抛 `java.lang.IllegalArgumentException: Control character in cookie value`。

解决方案：**URL 编码/解码**（本案例 `UserServlet` 的做法）

```java
// 存的时候编码："张三" → "%E5%BC%A0%E4%B8%89"
private String encode(String value) {
    try {
        return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
        return "";
    }
}

// 取的时候解码
private String decode(String value) {
    try {
        return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
        return "";
    }
}
```

### 7. Cookie 的限制

| 限制 | 说明 |
| --- | --- |
| 数据类型 | 只能是 String |
| 单个大小 | 约 4KB |
| 每个域名数量 | 浏览器一般限制 50 个左右 |
| 安全性 | **存在浏览器，用户可以随意查看和篡改**，不能存密码、余额等敏感数据 |
| 网络开销 | 每次请求都会自动带上，Cookie 太多会拖慢请求 |

**因为这些限制，重要数据必须存服务器端 —— 也就是 Session。**

## 三、Session

### 1. 原理（面试重点）

**Session 的数据存在服务器内存里，但服务器怎么知道这次请求是哪个用户的？靠 Cookie。**

```
① 浏览器第一次访问，服务器执行 request.getSession()
   服务器：创建一个HttpSession对象，生成唯一id（如 A1B2C3D4）
           把它存进一个Map：{ "A1B2C3D4" → session对象 }
   响应：Set-Cookie: JSESSIONID=A1B2C3D4

② 浏览器保存这个名为 JSESSIONID 的Cookie

③ 之后每次请求都自动携带
   请求：Cookie: JSESSIONID=A1B2C3D4
   服务器：根据 A1B2C3D4 从Map里找到对应的session对象 → 这就是你的session
```

**结论：Session 底层依赖 Cookie。** 用户如果禁用了 Cookie，Session 就失效了。

> **禁用 Cookie 的补救方案：URL 重写**
> ```java
> String url = response.encodeRedirectURL(request.getContextPath() + "/user/list");
> // 生成 /user/list;jsessionid=A1B2C3D4，把id拼在url上传递
> ```
> 现在基本不用了，因为几乎没人禁用 Cookie，而且拼在 url 上极不安全。

### 2. API

```java
// 获取session
HttpSession session = request.getSession();        // 没有就创建一个
HttpSession session = request.getSession(false);   // 没有就返回null，不创建

// 存取数据（Session是域对象）
session.setAttribute("loginUser", user);   // 可以存任意对象，不像Cookie只能存String
User user = (User) session.getAttribute("loginUser");
session.removeAttribute("loginUser");

// 其他
session.getId();                    // 会话id，就是JSESSIONID的值
session.setMaxInactiveInterval(1800);  // 最大不活动时间（秒）
session.invalidate();               // 销毁整个session
```

### 3. getSession() 与 getSession(false) 的区别

这是个容易忽略但很重要的细节：

```java
// UserServlet.login() —— 要往里存数据，所以必须创建
HttpSession session = request.getSession();
session.setAttribute(SESSION_USER, user);

// LoginCheckFilter —— 只是检查有没有登录，不该为未登录用户创建session
HttpSession session = request.getSession(false);
if (session != null && session.getAttribute(SESSION_USER) != null) { ... }

// UserServlet.logout() —— 没有session就没什么可销毁的
HttpSession session = request.getSession(false);
if (session != null) {
    session.invalidate();
}
```

**为什么校验时要用 `getSession(false)`？**
如果用 `getSession()`，那么每个爬虫、每个未登录的访客访问一次，
服务器就白白创建一个 Session 对象存在内存里，等 30 分钟超时才回收。
高并发下这是实实在在的内存浪费，还会把在线人数统计得虚高。

### 4. Session 的销毁时机

| 方式 | 说明 |
| --- | --- |
| **超时**（默认 30 分钟） | 从**最后一次访问**开始计时，期间有请求会重新计时 |
| **`invalidate()`** | 主动销毁，退出登录时用 |
| **服务器关闭** | 非正常关闭时直接丢失 |

⚠️ **关闭浏览器 Session 并不会立即销毁！**
关闭浏览器只是让存 JSESSIONID 的那个会话级 Cookie 消失了，
服务器内存里的 Session 对象还在，直到超时才被回收。

#### 配置超时时间

```xml
<!-- web.xml，单位：分钟 -->
<session-config>
    <session-timeout>30</session-timeout>
</session-config>
```

```java
// 代码方式，单位：秒
session.setMaxInactiveInterval(30 * 60);
```

### 5. Session 的钝化与活化

服务器正常关闭时，Tomcat 会把内存中的 Session **序列化到磁盘**（`work` 目录下的 `SESSIONS.ser`），
下次启动时再**反序列化恢复**。这叫钝化（passivate）和活化（activate）。

⚠️ **因此存进 Session 的对象最好实现 `Serializable` 接口**，否则钝化时会失败。
本案例的 `User` 类没实现，属于教学简化。

## 四、Cookie 与 Session 对比

| 对比项 | Cookie | Session |
| --- | --- | --- |
| 数据存储位置 | **浏览器** | **服务器内存** |
| 数据类型 | 只能 String | 任意 Object |
| 数据大小 | 约 4KB | 无限制（受服务器内存约束） |
| 安全性 | 低，用户可见可改 | 高，用户接触不到 |
| 服务器压力 | 无 | 有，用户越多占内存越多 |
| 生命周期 | 由 `setMaxAge` 决定，可以很长 | 默认 30 分钟不活动即销毁 |
| 依赖关系 | 独立 | **依赖 Cookie 传递 JSESSIONID** |
| 适用场景 | 记住用户名、语言偏好、埋点标识 | 登录状态、购物车、验证码 |

**本案例正好演示了两者的配合**：

- 「记住用户名」→ **Cookie**：不敏感、要长期保存、下次打开浏览器还要用
- 「登录状态」→ **Session**：敏感、不能让用户篡改、关闭浏览器就该失效

## 五、案例完整流程

### 登录

```java
public String login(HttpServletRequest request, HttpServletResponse response) {
    String username = request.getParameter("username");
    String password = request.getParameter("password");

    User user = userService.login(username, password);
    if (user == null) {
        request.setAttribute("errorMsg", "用户名或密码错误");
        request.setAttribute("username", username);          // 回显用户填的用户名
        return "/WEB-INF/pages/login.jsp";                   // 转发，保住errorMsg
    }

    // ① 登录状态存Session
    HttpSession session = request.getSession();
    session.setAttribute(SESSION_USER, user);

    // ② 用户名存Cookie（勾了"记住我"才存）
    boolean remember = "true".equals(request.getParameter("remember"));
    Cookie cookie = new Cookie("rememberedUsername", remember ? encode(username) : "");
    cookie.setMaxAge(remember ? 7 * 24 * 60 * 60 : 0);
    cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
    response.addCookie(cookie);

    return "redirect:/user/list";                            // 重定向，防重复提交
}
```

### 回显

```java
public String toLogin(HttpServletRequest request, HttpServletResponse response) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {                                   // ⚠️ 必须判空
        for (Cookie cookie : cookies) {
            if ("rememberedUsername".equals(cookie.getName())) {
                request.setAttribute("rememberedUsername", decode(cookie.getValue()));
                break;
            }
        }
    }
    return "/WEB-INF/pages/login.jsp";
}
```

```jsp
<%-- login.jsp：登录失败时回显刚填的，首次进入时回显cookie里记住的 --%>
<input type="text" name="username" value="${not empty username ? username : rememberedUsername}">
```

### 校验

```java
// LoginCheckFilter
HttpSession session = request.getSession(false);
if (session != null && session.getAttribute(UserServlet.SESSION_USER) != null) {
    chain.doFilter(request, response);   // 已登录，放行
    return;
}
response.sendRedirect(request.getContextPath() + "/user/toLogin");   // 未登录，赶去登录页
```

### 退出

```java
public String logout(HttpServletRequest request, HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    if (session != null) {
        session.invalidate();       // 销毁整个session，比逐个removeAttribute更彻底
    }
    return "redirect:/user/toLogin";
}
```

## 六、动手验证

1. 登录时**勾选**「记住用户名」→ 退出登录 → 再进登录页，用户名已自动填好
2. F12 → Application（Chrome）/ 存储（Firefox）→ Cookies，能看到两条：
   - `JSESSIONID` —— Expires 是 `Session`（关闭浏览器失效）
   - `rememberedUsername` —— Expires 是 7 天后的具体日期
3. 手动删掉 `JSESSIONID` 这个 Cookie，刷新页面 → **被踢回登录页**（服务器认不出你了）
4. 用 Chrome 正常窗口登录，再开一个**无痕窗口**访问 → 无痕窗口是未登录状态
   （两个窗口的 Cookie 互相隔离 = 两个不同的会话）
5. 观察控制台的在线人数变化（`AppContextListener` 的 `sessionCreated`/`sessionDestroyed`）

## 七、延伸：分布式下 Session 的问题

单机没问题，但线上通常是多台服务器 + 负载均衡：

```
                     ┌─→ 服务器A（session存在这里）
浏览器 → Nginx负载均衡 ┤
                     └─→ 服务器B（没有你的session → 认为你没登录）
```

**四种解决方案：**

| 方案 | 做法 | 缺点 |
| --- | --- | --- |
| 会话粘滞 | Nginx 按 ip_hash 让同一用户固定访问同一台 | 该服务器挂了用户就掉线，扩容不均衡 |
| Session 复制 | Tomcat 集群间同步 Session | 网络开销大，服务器越多越慢 |
| **Session 集中存储** | 存到 Redis（Spring Session 一行配置搞定） | 需要维护 Redis |
| **Token（JWT）** | 服务端不存状态，签名的 token 放在客户端 | 无法主动失效，需配合黑名单 |

现在主流是后两种，尤其前后端分离项目基本都用 **JWT**。
但**原理仍然是这一套**：客户端存标识 → 服务端识别身份，只是标识从 JSESSIONID 换成了 token，
存储从服务器内存换成了 Redis 或客户端。

---

上一篇：[06 域对象与转发重定向](06-域对象与转发重定向.md)　|　下一篇：[08 Filter 过滤器详解](08-Filter过滤器详解.md)

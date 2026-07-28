# 05 Response 响应对象详解

对应代码：`ResponseDemoServlet.java`

访问地址：<http://localhost/resp>（页面上有 6 个演示入口）

## 一、response 是什么

`HttpServletResponse` 用来**设置返回给浏览器的 HTTP 响应报文**。响应报文的结构：

```http
HTTP/1.1 200 OK                              ← 响应行：协议 + 状态码 + 状态描述
Content-Type: text/html;charset=UTF-8        ┐
Content-Length: 128                          │ 响应头
Set-Cookie: JSESSIONID=A1B2C3; Path=/        │
Location: /user/list                         ┘
                                             ← 空行
<h2>Hello Servlet</h2>                       ← 响应体
```

request 是 Tomcat 帮我们**解析好**的（只读），response 是 Tomcat 给我们的**空白模板**（只写），
我们往里填三部分内容，Tomcat 负责拼装成报文发出去。

| 报文部分 | 对应 API |
| --- | --- |
| 响应行 | `setStatus(int)`、`sendError(int, String)` |
| 响应头 | `setHeader(k,v)`、`addHeader(k,v)`、`setContentType()`、`addCookie()` |
| 响应体 | `getWriter()`（字符）、`getOutputStream()`（字节） |

## 二、响应体：两种输出流

| | 字符流 | 字节流 |
| --- | --- | --- |
| 获取方式 | `response.getWriter()` | `response.getOutputStream()` |
| 类型 | `PrintWriter` | `ServletOutputStream` |
| 适合 | 文本：html、json、xml、纯文本 | 二进制：图片、视频、Excel、压缩包 |
| 编码 | 受 `setContentType`/`setCharacterEncoding` 影响 | 原样输出，不涉及编码 |

### ⚠️ 两个流互斥

一次响应中**只能使用其中一个**，同时用会抛：

```
java.lang.IllegalStateException: getOutputStream() has already been called for this response
```

### ⚠️ 流不需要手动关闭

`response` 对象由 Tomcat 创建和销毁，响应结束时 Tomcat 会自动关闭它内部的流。
手动 `close()` 不会报错但没必要，反而在转发场景下容易引发「响应已提交」的问题。

### 案例代码

```java
private void writeText(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("text/html;charset=UTF-8");   // 先设置，再获取流
    PrintWriter writer = response.getWriter();
    writer.write("<h3>response响应案例</h3>");
}
```

## 三、响应乱码

### setContentType 与 setCharacterEncoding 的区别

```java
response.setCharacterEncoding("UTF-8");
// 只设置服务器用什么字符集把字符串转成字节，不告诉浏览器

response.setContentType("text/html;charset=UTF-8");
// 同时做两件事：①设置服务器编码字符集 ②在Content-Type响应头里告诉浏览器用UTF-8解码
```

**所以实际开发只用 `setContentType` 就够了**，它是前者的超集。

### 必须在获取流之前调用

```java
// ❌ 无效，中文乱码
PrintWriter writer = response.getWriter();
response.setContentType("text/html;charset=UTF-8");
writer.write("中文");

// ✅ 正确
response.setContentType("text/html;charset=UTF-8");
PrintWriter writer = response.getWriter();
writer.write("中文");
```

原因和 request 一样：`getWriter()` 被调用时，Tomcat 就已经按当前编码创建好了
`OutputStreamWriter`，之后再改编码来不及了。

### 常用的 Content-Type（MIME 类型）

| 数据类型 | Content-Type |
| --- | --- |
| html | `text/html;charset=UTF-8` |
| 纯文本 | `text/plain;charset=UTF-8` |
| json | `application/json;charset=UTF-8` |
| xml | `application/xml;charset=UTF-8` |
| 未知二进制（下载） | `application/octet-stream` |
| 图片 | `image/jpeg`、`image/png` |

## 四、响应 JSON（前后端分离的基础）

```java
private void writeJson(HttpServletResponse response) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":20000,\"msg\":\"操作成功\",\"data\":{\"id\":1,\"name\":\"张三\"}}");
}
```

**本质就是"设置 Content-Type + 输出一个字符串"**，没有任何魔法。

`UserServlet.listJson()` 演示了把 List 拼成 json 数组：

```java
public String listJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    StringBuilder json = new StringBuilder("{\"code\":20000,\"data\":[");
    // ... 手动拼接每个User ...
    response.getWriter().write(json.toString());
    return null;   // 返回null告诉BaseServlet：我已经自己响应了，别再跳页面
}
```

**手动拼 json 的问题**：转义麻烦、特殊字符会破坏格式、字段一多就没法维护、日期格式难处理。

**实际开发用 Jackson / FastJSON**：

```java
// 引入 jackson-databind 依赖后
String json = new ObjectMapper().writeValueAsString(userList);
response.getWriter().write(json);
```

**SpringMVC 里更简单**，加个 `@ResponseBody` 就行：

```java
@RequestMapping("/list")
@ResponseBody
public List<User> list() {
    return userService.findAll();     // 返回对象，框架自动转json写入响应体
}
```

它底层做的就是：找到 `MappingJackson2HttpMessageConverter` → 调 Jackson 序列化 →
设置 Content-Type → 写入 `response.getWriter()`。和上面手写的一模一样。

## 五、重定向 redirect

```java
response.sendRedirect(request.getContextPath() + "/index.jsp");
```

### 底层原理

`sendRedirect` 等价于手动写这两行：

```java
response.setStatus(302);                              // 状态码302：临时重定向
response.setHeader("Location", "/index.jsp");         // 告诉浏览器新地址
```

浏览器收到 302 响应后，**自动再发起一次请求**去访问 `Location` 里的地址：

```
浏览器 ──①请求 /resp?type=redirect──> 服务器
浏览器 <──②响应 302 + Location:/index.jsp── 服务器
浏览器 ──③自动请求 /index.jsp──> 服务器
浏览器 <──④响应 index.jsp 的内容── 服务器
```

所以是**两次请求、两对 request/response 对象**，地址栏变成了 `/index.jsp`。

### ⚠️ 路径必须带虚拟目录

重定向的地址是**给浏览器用的**，浏览器不知道你的项目部署在哪个虚拟目录下：

```java
// ✅ 正确
response.sendRedirect(request.getContextPath() + "/user/list");

// ❌ 项目部署在 /demo 下时会404
response.sendRedirect("/user/list");
```

`BaseServlet` 帮我们自动处理了这件事，所以业务方法里只要返回 `"redirect:/user/list"`：

```java
if (view.startsWith("redirect:")) {
    response.sendRedirect(request.getContextPath() + view.substring("redirect:".length()));
}
```

**SpringMVC 的 `return "redirect:/user/list"` 用的是同样的约定**，
这也是本案例故意采用这个前缀的原因。

### 重定向的经典用途：防止表单重复提交

`UserServlet.login()` 和 `save()` 成功后都用重定向而不是转发：

```java
return "redirect:/user/list";
```

**为什么？** 如果用转发，地址栏还停留在 `/user/save`，用户按 F5 刷新时，
浏览器会**重发上一次的 POST 请求**，导致重复插入数据。
重定向后地址栏变成 `/user/list`（GET 请求），刷新只是重新查列表，无副作用。

> 这个模式叫 **PRG（Post-Redirect-Get）**，是 Web 开发的基本规范。

## 六、文件下载

```java
private void download(HttpServletResponse response) throws IOException {
    String content = "这是一个由Servlet动态生成的文件\r\n";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

    // ① 告诉浏览器这是未知类型的二进制流，别自作主张打开
    response.setContentType("application/octet-stream");

    // ② 关键：Content-Disposition: attachment 才会触发"下载"而不是"在浏览器中打开"
    String fileName = new String("演示文件.txt".getBytes(StandardCharsets.UTF_8),
                                 StandardCharsets.ISO_8859_1);
    response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
    response.setContentLength(bytes.length);

    // ③ 字节流输出
    ServletOutputStream out = response.getOutputStream();
    out.write(bytes);
    out.flush();
}
```

### 三个关键点

1. **`Content-Disposition: attachment`** 是"下载"的开关。不设置的话，
   txt、jpg、pdf 这类浏览器认识的格式会**直接在浏览器里打开**。
2. **中文文件名乱码**：HTTP 响应头规定只能用 ISO-8859-1 编码，中文必须转码。
   `new String(name.getBytes("UTF-8"), "ISO-8859-1")` 这行看着奇怪，实际是
   「把 UTF-8 的字节，硬按 ISO-8859-1 拼成字符串」，浏览器收到后再按 UTF-8 还原。
   更规范的做法是用 `URLEncoder.encode(name, "UTF-8")`，但不同浏览器行为有差异。
3. **必须用字节流**，因为真实场景下载的是图片、Excel 等二进制文件。

### 真实场景的写法

案例里为了简化直接生成了内容，实际开发是读取服务器上的文件：

```java
// 拿到文件在服务器上的真实磁盘路径
String realPath = getServletContext().getRealPath("/files/report.xlsx");
try (FileInputStream in = new FileInputStream(realPath);
     ServletOutputStream out = response.getOutputStream()) {
    byte[] buffer = new byte[1024];
    int len;
    while ((len = in.read(buffer)) != -1) {
        out.write(buffer, 0, len);
    }
}
```

## 七、响应状态码

```java
response.setStatus(HttpServletResponse.SC_NOT_FOUND);   // 只设状态码，响应体自己控制
response.sendError(404, "资源不存在");                   // 设状态码 + 跳到服务器错误页
```

### 常见状态码

| 码 | 含义 | 常见原因 |
| --- | --- | --- |
| **200** | OK | 成功 |
| **302** | Found（临时重定向） | `sendRedirect` |
| 304 | Not Modified | 浏览器读缓存，静态资源常见 |
| 400 | Bad Request | 参数格式错误 |
| 401 | Unauthorized | 未登录/未认证 |
| 403 | Forbidden | 已登录但没权限 |
| **404** | Not Found | **路径写错、url-pattern 配错、资源不存在** |
| **405** | Method Not Allowed | **表单 POST 提交但只重写了 doGet** |
| 500 | Internal Server Error | **服务端代码抛异常，看控制台堆栈** |

**开发中最常遇到的就是加粗的这几个**，排查思路见 `13-常见问题FAQ.md`。

### setStatus 与 sendError 的区别

```java
// setStatus：状态码归你设，响应体也归你写，页面显示你写的内容
response.setStatus(404);
response.getWriter().write("<h3>自定义的404提示</h3>");

// sendError：交给容器处理，会跳转到web.xml中<error-page>配置的页面
response.sendError(404, "资源不存在");
```

本案例 `BaseServlet` 找不到方法时用的是 `sendError`：

```java
catch (NoSuchMethodException e) {
    response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "找不到处理方法：" + getClass().getSimpleName() + "#" + methodName + "()");
}
```

于是会跳到 `web.xml` 里配的 `/WEB-INF/pages/error.jsp`。
登录后访问 <http://localhost/user/notExist> 可以看到效果。

## 八、自定义响应头

```java
response.setHeader("Refresh", "3;URL=/index.jsp");   // 同名会覆盖
response.addHeader("Set-Cookie", "...");             // 同名会追加
```

### Refresh：定时跳转

```java
response.setHeader("Refresh", "3;URL=" + request.getContextPath() + "/index.jsp");
response.getWriter().write("<h3>操作成功，3秒后自动跳转到首页...</h3>");
```

用于「操作成功」提示页，比直接重定向的体验更友好。

### 其他常用响应头

| 响应头 | 作用 |
| --- | --- |
| `Content-Type` | 数据类型和字符集 |
| `Content-Disposition` | 触发下载 |
| `Location` | 重定向地址（配合 302） |
| `Refresh` | 定时刷新/跳转 |
| `Set-Cookie` | 写 Cookie（`response.addCookie()` 底层就是它） |
| `Cache-Control` / `Expires` | 缓存控制 |
| `Access-Control-Allow-Origin` | **跨域**，前后端分离必用 |

## 九、案例实操

访问 <http://localhost/resp>，逐个点击 6 个链接，配合浏览器 **F12 → Network** 面板观察：

| 链接 | 重点观察 |
| --- | --- |
| 响应 json | Response Headers 里的 `Content-Type: application/json` |
| 重定向 | Network 里有**两条**记录：第一条状态码 302 带 `Location` 头，第二条是 200 |
| 文件下载 | `Content-Disposition: attachment`，浏览器弹出下载框 |
| 状态码 404 | Status Code 显示 404，但页面仍显示我们写的内容（`setStatus` 的效果） |
| 定时刷新 | Response Headers 里的 `Refresh: 3;URL=/index.jsp` |

---

上一篇：[04 Request 请求对象详解](04-Request请求对象详解.md)　|　下一篇：[06 域对象与转发重定向](06-域对象与转发重定向.md)

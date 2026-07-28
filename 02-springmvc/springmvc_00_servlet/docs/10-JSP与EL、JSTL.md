# 10 JSP 与 EL、JSTL

对应代码：`index.jsp`、`WEB-INF/pages/` 下的 6 个页面

## 一、为什么需要 JSP

回顾 `HelloServlet` 输出页面的方式：

```java
response.getWriter().write("<h2>Hello Servlet</h2>");
```

页面稍微复杂一点，就变成了这种噩梦：

```java
out.write("<table>");
for (User user : userList) {
    out.write("<tr><td>" + user.getId() + "</td><td>" + user.getUsername() + "</td></tr>");
}
out.write("</table>");
```

**在 Java 里拼 HTML**，没有语法高亮、没有标签补全、改个样式要重新编译部署，完全没法维护。

**JSP（Java Server Pages）= HTML + Java**，把关系倒过来：以 HTML 为主体，需要动态数据的地方嵌入 Java。

## 二、JSP 的本质：它就是一个 Servlet

**JSP 在运行时会被 Tomcat 翻译成 `.java` 文件，编译成 `.class` 执行。**

```
list.jsp  ──Jasper引擎翻译──>  list_jsp.java  ──编译──>  list_jsp.class
                                    ↑
                            extends HttpJspBase
                                    ↑
                            extends HttpServlet   ← 本质是Servlet！
```

翻译后的文件在 Tomcat 的 `work/Catalina/localhost/项目名/org/apache/jsp/` 目录下，
可以打开看看，你会发现 JSP 里的 HTML 变成了 `out.write("<table>")` 这样的语句 ——
**又绕回了最开始拼字符串的写法，只不过是机器帮你拼的。**

这解释了几个现象：

- 第一次访问 JSP 特别慢（要翻译 + 编译），之后就快了
- JSP 里能直接用 `request`、`response`、`session` 这些对象（它们是翻译后 `_jspService` 方法的参数或局部变量）
- JSP 报错的行号有时对不上（那是翻译后 java 文件的行号）

## 三、JSP 语法

### 1. 指令（Directive）：配置页面

语法 `<%@ 指令名 属性="值" %>`

```jsp
<%-- page指令：配置当前页面 --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page isErrorPage="true" %>          <%-- 声明为错误页，才能用exception对象 --%>
<%@ page errorPage="/error.jsp" %>      <%-- 出错时跳转到指定页面 --%>
<%@ page import="java.util.List" %>     <%-- 导包 --%>

<%-- taglib指令：引入标签库 --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%-- include指令：静态包含，把另一个页面的内容合并进来 --%>
<%@ include file="/WEB-INF/pages/header.jsp" %>
```

本案例的用法：
- 每个页面开头都有 `<%@ page contentType="text/html;charset=UTF-8" %>` —— **解决 JSP 中文乱码**
- `list.jsp` 有 `<%@ taglib ... %>` —— 因为用到了 `<c:forEach>`
- `error.jsp` 有 `isErrorPage="true"`

### 2. 脚本（Script）：写 Java 代码

```jsp
<%  Java代码，翻译到 _jspService 方法内部（局部变量）  %>
<%= 表达式，翻译成 out.print(...)  %>
<%! 声明，翻译成类的成员变量/方法（几乎不用）  %>
```

例如：

```jsp
<%
    List<User> list = (List<User>) request.getAttribute("userList");
    for (User user : list) {
%>
        <tr><td><%= user.getUsername() %></td></tr>
<%
    }
%>
```

**⚠️ 本案例的 JSP 里一行脚本都没有，这是刻意的。**

Java 代码和 HTML 混在一起会导致：可读性极差、无法复用、前端人员看不懂、违背 MVC 分层。
**现代 JSP 开发的原则：页面里只能出现 EL 表达式和 JSTL 标签，不写任何 `<% %>`。**

上面那段代码用 EL + JSTL 只需要：

```jsp
<c:forEach items="${userList}" var="user">
    <tr><td>${user.username}</td></tr>
</c:forEach>
```

### 3. 注释

```jsp
<!-- HTML注释：会被发送到浏览器，查看源码能看到 -->
<%-- JSP注释：只存在于jsp文件中，不会输出到浏览器 --%>
```

**涉及内部逻辑的注释一定要用 JSP 注释**，否则等于把实现细节告诉了所有访客。

## 四、JSP 的 9 个内置对象

不用声明就能直接使用，因为翻译后它们是 `_jspService` 方法里现成的变量。

| 对象 | 类型 | 说明 |
| --- | --- | --- |
| `request` | `HttpServletRequest` | 请求对象，域对象 |
| `response` | `HttpServletResponse` | 响应对象 |
| `session` | `HttpSession` | 会话对象，域对象 |
| `application` | `ServletContext` | 应用对象，域对象 |
| `pageContext` | `PageContext` | **页面上下文，域对象，能获取其他 8 个对象** |
| `out` | `JspWriter` | 输出流 |
| `page` | `Object` | 当前页面对象（相当于 this） |
| `config` | `ServletConfig` | 配置对象 |
| `exception` | `Throwable` | **异常对象，只有 `isErrorPage="true"` 时才有** |

### pageContext 最特殊

它是"万能钥匙"，能拿到其他所有对象：

```jsp
${pageContext.request.contextPath}    <%-- 等价于 request.getContextPath() --%>
${pageContext.session.id}             <%-- 等价于 session.getId() --%>
```

本案例每个页面的链接都用 `${pageContext.request.contextPath}` 拼虚拟目录，
`list.jsp` 里还用 `${pageContext.session.id}` 展示了会话 id。

## 五、EL 表达式

**EL（Expression Language）用来替代 `<%= %>`，从域对象中取值并输出。**

语法：`${表达式}`

### 1. 取值规则：默认按域范围从小到大查找

```jsp
${username}
```

依次查找：**pageContext → request → session → application**，
**找到就返回，全找不到返回空串**（注意：不是 "null"，也不是报错）。

### 2. 指定域取值

```jsp
${pageScope.username}
${requestScope.userList}
${sessionScope.loginUser}          <%-- 本案例 list.jsp 用了它 --%>
${applicationScope.appName}        <%-- 本案例 index.jsp 用了它 --%>
```

**推荐显式指定域**，可读性更好，也避免同名 key 被小范围的域覆盖。

### 3. 取对象属性：用点，走 getter

```jsp
${sessionScope.loginUser.username}
```

**它调用的不是 `user.username` 字段，而是 `user.getUsername()` 方法。**
这就是为什么实体类**必须提供 getter**，否则 EL 取不到值（属性是 private 的更不用说）。

> 本案例 `User` 类的每个属性都写了 getter/setter，就是给 EL 和参数封装用的。

### 4. 取集合元素

```jsp
${userList[0].username}       <%-- List/数组按下标 --%>
${map.key}  或  ${map["key"]} <%-- Map按key --%>
```

### 5. 运算符

```jsp
${1 + 2}                          <%-- 算术：+ - * / % --%>
${age > 18}                       <%-- 比较：> < >= <= == !=  或 gt lt ge le eq ne --%>
${age > 18 && gender == '男'}      <%-- 逻辑：&& || !  或 and or not --%>
${empty userList}                 <%-- 判空：null、空串、空集合都返回true --%>
${not empty errorMsg}             <%-- 取反 --%>
${user.gender eq '男' ? 'checked' : ''}   <%-- 三元运算 --%>
```

本案例的实际应用：

```jsp
<%-- login.jsp：优先回显刚提交的用户名，否则回显cookie里记住的 --%>
<input type="text" name="username" value="${not empty username ? username : rememberedUsername}">

<%-- update.jsp：单选框回显 --%>
<input type="radio" name="gender" value="男" ${user.gender eq '男' ? 'checked' : ''}> 男

<%-- index.jsp：在线人数为null时显示0 --%>
${applicationScope.onlineCount == null ? 0 : applicationScope.onlineCount}
```

### 6. EL 的一个重要特性：null 安全

```jsp
${user.address}
```

即使 `user` 是 null，也**不会抛空指针**，只输出空串。
这比 `<%= user.getAddress() %>` 安全得多，也是应该用 EL 的重要理由。

## 六、JSTL 标签库

EL 只能取值输出，**不能做循环和判断**。JSTL（JSP Standard Tag Library）补上了这块。

### 1. 使用步骤

① 引入依赖（pom.xml，本案例已配）：

```xml
<dependency>
  <groupId>javax.servlet</groupId>
  <artifactId>jstl</artifactId>
  <version>1.2</version>
</dependency>
```

② 页面顶部引入标签库：

```jsp
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
```

### 2. 核心标签

#### `<c:forEach>` 遍历

```jsp
<%-- 遍历集合 --%>
<c:forEach items="${userList}" var="user">
    <tr>
        <td>${user.id}</td>
        <td>${user.username}</td>
    </tr>
</c:forEach>

<%-- 带状态：varStatus提供 index(下标)、count(第几个)、first、last --%>
<c:forEach items="${userList}" var="user" varStatus="s">
    <td>${s.count}</td>            <%-- 序号，从1开始 --%>
</c:forEach>

<%-- 普通for循环 --%>
<c:forEach begin="1" end="10" step="1" var="i">${i} </c:forEach>
```

#### `<c:if>` 判断

```jsp
<c:if test="${empty userList}">
    <tr><td colspan="6">暂无数据</td></tr>
</c:if>
```

⚠️ **JSTL 没有 `<c:else>`**，需要 if-else 时用 `<c:choose>`：

```jsp
<c:choose>
    <c:when test="${user.age >= 18}">成年</c:when>
    <c:when test="${user.age >= 12}">青少年</c:when>
    <c:otherwise>儿童</c:otherwise>
</c:choose>
```

#### 其他常用标签

```jsp
<c:set var="name" value="张三" scope="request"/>     <%-- 给域赋值 --%>
<c:out value="${content}" escapeXml="true"/>         <%-- 输出并转义html，防XSS --%>
<c:forEach items="${map}" var="entry">${entry.key}=${entry.value}</c:forEach>
```

### 3. `<c:out>` 与 XSS

**EL 直接输出 `${content}` 是不转义的**，如果内容来自用户输入且含 `<script>`，会被浏览器执行（XSS 攻击）。

```jsp
${content}                            <%-- ❌ 不转义，有XSS风险 --%>
<c:out value="${content}"/>           <%-- ✅ 默认转义，< 变成 &lt; --%>
```

本案例 `request.jsp` 里故意用了不转义的写法：

```jsp
<%-- 值中包含html标签，EL默认不转义，这里直接渲染 --%>
${html}
```

因为那段 HTML 是我们自己在 Servlet 里拼的，可控。
**但凡是用户输入的内容，一律用 `<c:out>` 输出。**

## 七、案例页面结构

```
webapp/
├── index.jsp                    案例导航（可直接访问）
├── css/common.css               公共样式（可直接访问）
└── WEB-INF/pages/               ← 受保护，只能转发访问
    ├── login.jsp                登录表单（回显 + 错误提示）
    ├── list.jsp                 用户列表（forEach + if + session取值）
    ├── add.jsp                  新增表单
    ├── update.jsp               修改表单（回显 + 隐藏域传id + 单选框回显）
    ├── request.jsp              转发目标页（演示request域取值）
    └── error.jsp                统一错误页（isErrorPage）
```

### 为什么把 JSP 放在 WEB-INF 下

如果放在 webapp 根目录，用户可以直接访问 `http://localhost/list.jsp`，
**绕过了 Servlet**，结果是：

- 没有经过 `LoginCheckFilter` 的登录校验（如果过滤器只配了 `/user/*`）
- 没有执行查询逻辑，`${userList}` 为空，页面是一张空表
- 暴露了页面路径

**放在 `WEB-INF` 下，就强制用户必须走 `/user/list` 这个 Servlet**，
校验、查数据、渲染一步不落。这是 Java Web 的标准实践。

### 错误页的特殊写法

```jsp
<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>

<%-- 容器转发到错误页时，会把这些信息放进request域 --%>
<p>状态码：${requestScope['javax.servlet.error.status_code']}</p>
<p>出错路径：${requestScope['javax.servlet.error.request_uri']}</p>
<p>错误信息：${requestScope['javax.servlet.error.message']}</p>
```

⚠️ 这些 key 里有点号，**必须用 `[' ']` 的写法**，
写成 `${requestScope.javax.servlet.error.status_code}` 会被 EL 当成多级属性调用而取不到值。

## 八、JSP 的现状：为什么现在很少用了

| 时代 | 架构 | 视图层 |
| --- | --- | --- |
| 早期 | Model1：JSP 里写全部逻辑 | JSP + 大量 `<% %>` |
| SSM 时代 | Model2（MVC）：Servlet 控制 + JSP 展示 | **JSP + EL + JSTL**（本案例） |
| 现在 | **前后端分离** | Vue / React，后端只返回 JSON |

**JSP 被淘汰的原因：**

- 每次修改页面都要重新翻译编译，开发体验差
- 前后端代码耦合在一个项目里，无法独立开发部署
- 前端工程师不熟悉 Java 语法和 JSP 标签
- 无法利用 CDN、无法做前端工程化（打包、Tree Shaking、组件化）
- 服务端渲染消耗服务器资源

**那还要不要学？** 要，但不必深挖：

1. 大量存量项目还在用 JSP，维护时必须能看懂
2. **理解「服务端渲染」的原理**，才能明白前后端分离解决了什么问题
3. SpringMVC 的 `ViewResolver`、`Model` 这些概念都是围绕视图技术设计的
4. Thymeleaf、FreeMarker 等现代模板引擎，思路和 JSP 是一脉相承的

**学到能看懂、能改就够了，新项目一律用前后端分离 + `@ResponseBody` 返回 JSON。**

---

上一篇：[09 Listener 监听器详解](09-Listener监听器详解.md)　|　下一篇：[11 综合案例代码走读](11-综合案例代码走读.md)

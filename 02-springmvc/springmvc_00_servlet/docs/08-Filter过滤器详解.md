# 08 Filter 过滤器详解

对应代码：`CharacterEncodingFilter.java`、`LoginCheckFilter.java`

## 一、Filter 是什么

**Filter 是 Web 三大组件之一（Servlet、Filter、Listener），能在请求到达 Servlet 之前、
响应返回浏览器之前做拦截和统一处理。**

```
             ┌── Filter1 ──┐  ┌── Filter2 ──┐
浏览器 ─请求─→│  放行前逻辑  │→ │  放行前逻辑  │→ Servlet
             │             │  │             │      ↓
浏览器 ←响应─│  放行后逻辑  │← │  放行后逻辑  │← ────┘
             └─────────────┘  └─────────────┘
```

**核心价值：把每个 Servlet 都要写的重复代码抽出来，写一次，全局生效。**

典型用途：

| 用途 | 本案例 | 实际项目 |
| --- | --- | --- |
| 统一编码 | ✅ `CharacterEncodingFilter` | Spring 的 `CharacterEncodingFilter` |
| 登录/权限校验 | ✅ `LoginCheckFilter` | Shiro、Spring Security 的过滤器链 |
| 跨域处理 | — | `CorsFilter` |
| 敏感词过滤 | — | 论坛、评论区 |
| 请求日志、耗时统计 | — | 全链路追踪 |
| 压缩、防 XSS | — | 包装 request 重写 `getParameter` |

## 二、编写步骤

```java
@WebFilter(urlPatterns = "/*")                      // ③ 配置拦截路径
public class CharacterEncodingFilter implements Filter {   // ① 实现Filter接口

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override                                        // ② 重写三个方法
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // 放行前：增强request
        chain.doFilter(request, response);           // ④ 放行（关键！）
        // 放行后：增强response
    }

    @Override
    public void destroy() { }
}
```

⚠️ **注意实现的是 `javax.servlet.Filter`**，别导成其他包（IDEA 常见误导入）。

## 三、生命周期

和 Servlet 几乎一样，但有一个重要差别：

| 阶段 | 方法 | 执行次数 | 时机 |
| --- | --- | :---: | --- |
| 实例化 | 构造 | 1 次 | **服务器启动时**（不是首次访问！） |
| 初始化 | `init(FilterConfig)` | 1 次 | 服务器启动时 |
| 拦截 | `doFilter()` | N 次 | 每次匹配的请求 |
| 销毁 | `destroy()` | 1 次 | 服务器正常关闭时 |

**Filter 一定在服务器启动时就创建好**，不像 Servlet 默认是懒加载的 ——
因为它必须在第一个请求到来之前就准备就绪。

启动日志可以验证：

```
【Filter】CharacterEncodingFilter 初始化完成，编码：UTF-8
【Filter】LoginCheckFilter 初始化完成，放行白名单：[/user/toLogin, /user/login]
```

## 四、doFilter 与 FilterChain（核心）

```java
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    // ===== A：放行前的代码 =====
    request.setCharacterEncoding("UTF-8");

    chain.doFilter(request, response);   // ★ 放行：把请求交给下一个Filter或目标Servlet

    // ===== B：放行后的代码 =====
    // 目标资源执行完了才会回到这里
}
```

### 执行顺序（责任链模式）

假设有两个 Filter：

```
Filter1的A → Filter2的A → Servlet执行 → Filter2的B → Filter1的B
```

像剥洋葱一样，**放行前是正序，放行后是倒序**。

### 不调用 chain.doFilter() 会怎样？

**请求到此为止，不会到达 Servlet。** 这正是"拦截"的实现方式：

```java
// LoginCheckFilter：未登录时不放行，直接重定向到登录页
response.sendRedirect(request.getContextPath() + "/user/toLogin");
// 注意这里没有调用 chain.doFilter()，UserServlet 根本不会被执行
```

⚠️ **常见 bug：忘记写 `return`**

```java
// ❌ 重定向之后又放行了，会抛 IllegalStateException
if (未登录) {
    response.sendRedirect(...);
}
chain.doFilter(request, response);

// ✅ 正确：拦截后立即return
if (未登录) {
    response.sendRedirect(...);
    return;
}
chain.doFilter(request, response);
```

本案例 `LoginCheckFilter` 用的是「放行后 return」的写法，逻辑更清晰：

```java
if (WHITE_LIST.contains(path)) {
    chain.doFilter(request, response);
    return;                                    // 放行完就结束
}
if (已登录) {
    chain.doFilter(request, response);
    return;
}
// 走到这里就是不放行
response.sendRedirect(...);
```

## 五、拦截路径配置

| 写法 | 拦截范围 |
| --- | --- |
| `/*` | **所有请求**（含 jsp、css、图片等静态资源） |
| `/user/*` | `/user` 开头的所有请求 |
| `*.do` | 以 `.do` 结尾的请求 |
| `/index.jsp` | 只拦截这一个具体资源 |

本案例：

```java
@WebFilter(urlPatterns = "/*")        // 编码过滤器：所有请求都要处理编码
@WebFilter(urlPatterns = "/user/*")   // 登录过滤器：只保护用户模块
```

### xml 配置方式

```xml
<filter>
    <filter-name>loginCheckFilter</filter-name>
    <filter-class>com.springmvc.filter.LoginCheckFilter</filter-class>
    <init-param>
        <param-name>encoding</param-name>
        <param-value>UTF-8</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>loginCheckFilter</filter-name>
    <url-pattern>/user/*</url-pattern>
</filter-mapping>
```

## 六、多个 Filter 的执行顺序（重点）

| 配置方式 | 顺序规则 |
| --- | --- |
| **注解 `@WebFilter`** | 按**类的全限定名字典序**（不是按定义顺序！） |
| **web.xml** | 按 `<filter-mapping>` 的**先后顺序**，写在前面的先执行 |

本案例故意利用了这个规则：

```
com.springmvc.filter.CharacterEncodingFilter   ← C 在前
com.springmvc.filter.LoginCheckFilter          ← L 在后
```

所以**编码过滤器先执行**，等到 `LoginCheckFilter` 拿到 request 时，编码已经设置好了。
这是必要的：如果顺序反了，登录过滤器读取的参数就可能是乱码。

### ⚠️ 注解方式无法可靠控制顺序

字典序这个规则太脆弱了 —— 有人重构改个类名，顺序就变了，而且改动不易察觉。
**对顺序有强要求时，改用 web.xml 配置**：

```xml
<!-- 明确写清顺序，一目了然 -->
<filter-mapping>
    <filter-name>characterEncodingFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
<filter-mapping>
    <filter-name>loginCheckFilter</filter-name>
    <url-pattern>/user/*</url-pattern>
</filter-mapping>
```

> SpringBoot 里可以用 `FilterRegistrationBean.setOrder(int)` 或 `@Order` 精确控制顺序。

## 七、dispatcherTypes：拦截时机（容易被忽略的坑）

```java
@WebFilter(urlPatterns = "/*", dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD})
```

| 类型 | 含义 | 是否默认拦截 |
| --- | --- | :---: |
| `REQUEST` | 浏览器直接发起的请求 | ✅ **默认只有这个** |
| `FORWARD` | 服务器内部转发 | ❌ |
| `INCLUDE` | 页面包含 | ❌ |
| `ERROR` | 跳转到错误页 | ❌ |
| `ASYNC` | 异步调用 | ❌ |

**默认只拦截 REQUEST**，这意味着：

```java
// UserServlet.list() 转发到 list.jsp 时，过滤器不会再执行一遍
return "/WEB-INF/pages/list.jsp";
```

**这个默认值通常是对的**（编码只需设一次，登录只需校验一次），但要知道它的存在。

**什么时候需要改？** 比如你想拦截「跳转到错误页」这个动作做日志记录，
就要加上 `DispatcherType.ERROR`。

⚠️ **反面案例**：如果 `LoginCheckFilter` 配了 `FORWARD`，
那么 `login()` 失败后转发到 `/WEB-INF/pages/login.jsp` 时会**再次触发过滤器**，
而此时用户未登录，又被重定向到登录页……虽然不至于死循环（登录页在白名单里），
但逻辑就乱了。

## 八、Filter 的进阶用法：包装 request

Filter 只能"看"请求还不够，有时需要"改"请求。但 `HttpServletRequest` 是接口，
Tomcat 的实现类不让改，怎么办？

**用装饰器模式**：Servlet 提供了 `HttpServletRequestWrapper`，继承它并重写想改的方法：

```java
public class XssRequestWrapper extends HttpServletRequestWrapper {
    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value == null ? null : value.replaceAll("<", "&lt;");   // 转义，防XSS
    }
}

// 在Filter里把包装后的对象传下去
chain.doFilter(new XssRequestWrapper(request), response);
```

之后所有 Servlet 调用 `getParameter` 拿到的就是处理过的值了，业务代码零改动。
敏感词过滤、防 XSS、请求体重复读取都是这个套路。

## 九、Filter 与 Interceptor（SpringMVC 拦截器）

这是面试高频对比题。

| 对比项 | Filter 过滤器 | Interceptor 拦截器 |
| --- | --- | --- |
| 来源 | **Servlet 规范**（Java EE） | **SpringMVC 框架** |
| 依赖 | 只依赖 Servlet 容器 | 依赖 Spring 容器 |
| 拦截范围 | **所有请求**，包括静态资源、jsp | **只拦截 DispatcherServlet 处理的请求**，静态资源默认不拦 |
| 能否被 Spring 管理 | 默认不能，拿不到 Bean（需特殊处理） | ✅ 本身就是 Bean，可 `@Autowired` |
| 能否拿到处理器信息 | ❌ 不知道请求会交给哪个方法 | ✅ `preHandle` 的 `handler` 参数就是 `HandlerMethod`，能拿到方法上的注解 |
| 方法粒度 | 只有 `doFilter` 一个方法 | `preHandle`、`postHandle`、`afterCompletion` 三个时机 |
| 执行位置 | DispatcherServlet **之前** | DispatcherServlet **之内** |

```
请求 → Filter → DispatcherServlet → Interceptor.preHandle → Controller
                                  → Interceptor.postHandle → 视图渲染
                                  → Interceptor.afterCompletion → Filter → 响应
```

**怎么选？**

- **编码、跨域、XSS、请求日志** → Filter（要覆盖所有请求）
- **登录校验、权限控制、参数校验** → Interceptor（要拿到方法上的 `@RequireLogin` 之类的注解）

本案例的 `LoginCheckFilter`，在 `springmvc_12_interceptor` 模块中会用
`HandlerInterceptor` 重新实现一遍，可以对照着看：

```java
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 返回true放行，返回false拦截 —— 比chain.doFilter()直观
        if (request.getSession().getAttribute("loginUser") != null) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/user/toLogin");
        return false;
    }
}
```

## 十、动手验证

1. 启动服务，看控制台两个 Filter 的初始化日志（**还没访问任何页面就已打印**）
2. 未登录直接访问 <http://localhost/user/list>，控制台打印：
   ```
   【Filter】拦截未登录请求：/user/list
   ```
   浏览器被重定向到登录页
3. 登录后再访问，能正常进入列表页（过滤器放行）
4. **实验**：把 `CharacterEncodingFilter` 的 `request.setCharacterEncoding(encoding)` 注释掉，
   重启后从首页提交 POST 中文表单 → 出现乱码，直观感受过滤器的作用
5. **实验**：把 `LoginCheckFilter` 的白名单清空 → 登录页也被拦截 → **无限重定向**，
   浏览器报 `ERR_TOO_MANY_REDIRECTS`。这说明白名单是必须的

---

上一篇：[07 会话技术 Cookie 与 Session](07-会话技术Cookie与Session.md)　|　下一篇：[09 Listener 监听器详解](09-Listener监听器详解.md)

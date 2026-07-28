# 12 从 Servlet 到 SpringMVC

这一篇是本模块和 `springmvc_01_quickstart` 之间的桥梁。
**看完这篇，再去看 SpringMVC 的代码，应该有「原来如此」的感觉，而不是「这是什么魔法」。**

## 一、纯 Servlet 开发的七个痛点

### 痛点 1：一个路径一个类

```
LoginServlet、UserListServlet、UserSaveServlet、UserDeleteServlet...
```

一个中等项目几百个 Servlet 类，`web.xml` 上千行。

**本案例的缓解方案**：`BaseServlet` 反射分发，一个类管一个模块。
**SpringMVC 的方案**：一个 `DispatcherServlet` 管所有请求，用 `@RequestMapping` 声明路径。

### 痛点 2：参数获取和封装全是体力活

```java
User user = new User();
user.setId(parseInt(request.getParameter("id")));
user.setUsername(request.getParameter("username"));
user.setGender(request.getParameter("gender"));
user.setAge(parseInt(request.getParameter("age")));
user.setAddress(request.getParameter("address"));
// 字段有20个就写20行，每个Servlet都要写一遍
```

还要自己处理：null 判断、空串判断、类型转换、转换失败的异常。

**SpringMVC**：
```java
public String save(User user) { }     // 完事
```

### 痛点 3：返回 JSON 要手动拼字符串

```java
StringBuilder json = new StringBuilder("{\"code\":20000,\"data\":[");
for (int i = 0; i < userList.size(); i++) { ... }
```

转义、日期格式、嵌套对象……全是坑。

**SpringMVC**：`@ResponseBody` + 返回对象。

### 痛点 4：跳转逻辑重复

每个方法都要写：

```java
request.getRequestDispatcher("/WEB-INF/pages/list.jsp").forward(request, response);
// 或
response.sendRedirect(request.getContextPath() + "/user/list");
```

**本案例的缓解方案**：`BaseServlet` 统一处理返回值。
**SpringMVC**：`ViewResolver` 配前缀后缀，方法只返回 `"list"`。

### 痛点 5：与业务对象强耦合

```java
private final UserService userService = new UserService();   // 硬编码new
```

换实现类要改代码，无法单元测试（没法注入 mock）。

**Spring**：IoC + DI，`@Autowired` 注入，实现类可配置可替换。

### 痛点 6：异常处理散落各处

每个方法都要 try-catch，或者干脆不处理让用户看到 500 页面和堆栈。

**SpringMVC**：`@ControllerAdvice` + `@ExceptionHandler` 全局统一处理。

### 痛点 7：没有 REST 风格支持

```java
// 想按请求方式区分？只能自己判断
if ("POST".equals(request.getMethod())) { ... }
```

**SpringMVC**：`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@PathVariable`。

## 二、SpringMVC 的本质

**SpringMVC = 一个叫 `DispatcherServlet` 的 Servlet + 一套围绕它的扩展机制。**

```java
public class DispatcherServlet extends FrameworkServlet { }
public abstract class FrameworkServlet extends HttpServletBean { }
public abstract class HttpServletBean extends HttpServlet { }   // ← 最终还是HttpServlet
```

**它就是我们写的 `BaseServlet`，只不过功能强大了几百倍。**

### 对照表：BaseServlet vs DispatcherServlet

| 本案例 `BaseServlet` 的代码 | SpringMVC 中的组件 | 作用 |
| --- | --- | --- |
| `service()` 方法整体 | `DispatcherServlet.doDispatch()` | 请求分发的总入口 |
| `uri.substring(uri.lastIndexOf('/') + 1)` 截取方法名 | **`HandlerMapping`** | 根据请求找到处理器 |
| `getClass().getDeclaredMethod(...)` | `HandlerMapping` 返回 `HandlerExecutionChain` | 处理器 + 拦截器链 |
| `method.invoke(this, request, response)` | **`HandlerAdapter`** | 执行处理器，解析参数、处理返回值 |
| 手动 `request.getParameter()` | `HandlerMethodArgumentResolver` 参数解析器 | 自动封装方法参数 |
| 返回值 `"/WEB-INF/pages/list.jsp"` | **`ViewResolver`** | 视图名 → 真实视图 |
| `request.getRequestDispatcher(...).forward(...)` | `View.render()` | 视图渲染 |
| 返回值 `"redirect:/user/list"` | `RedirectView` | 重定向视图 |
| 返回 `null` + 手写 json | `HttpMessageConverter` | 对象 ↔ JSON 自动转换 |
| `catch (NoSuchMethodException)` 返回 404 | `NoHandlerFoundException` | 找不到处理器 |
| `catch (InvocationTargetException)` 抛 500 | **`HandlerExceptionResolver`** | 统一异常处理 |
| `LoginCheckFilter` | **`HandlerInterceptor`** | 拦截器 |
| `CharacterEncodingFilter` | Spring 的同名 Filter | 编码处理 |
| `request.setAttribute()` | `Model` / `ModelAndView` | 数据模型 |
| `web.xml` 配置 Servlet | `AbstractDispatcherServletInitializer` 配置类 | 容器初始化 |

## 三、DispatcherServlet 的执行流程

把 `BaseServlet` 的流程「展开」，就是 SpringMVC 的流程：

```
浏览器请求
    │
    ▼
① DispatcherServlet（前端控制器，统一入口）
    │
    ▼
② HandlerMapping（处理器映射器）
    │  根据url找到对应的Controller方法，连同拦截器组成 HandlerExecutionChain
    │  ★ 对应 BaseServlet 的「截路径 + getDeclaredMethod」
    ▼
③ HandlerInterceptor.preHandle()（拦截器前置处理）
    │  ★ 对应 LoginCheckFilter
    ▼
④ HandlerAdapter（处理器适配器）
    │  解析方法参数（封装User、注入Model...）
    │  ★ 对应 BaseServlet 的「method.invoke」+ buildUserFromRequest
    ▼
⑤ Controller 方法执行 → 返回 ModelAndView 或 String 或 对象
    │  ★ 对应 UserServlet 的 list()/save()...
    ▼
⑥ 判断返回值
    ├─ 有 @ResponseBody → HttpMessageConverter 转 JSON 写入响应体 → 结束
    │                      ★ 对应 listJson() 里手写的 setContentType + write
    │
    └─ 返回视图名 → ⑦ ViewResolver（视图解析器）
                       │  "list" → "/WEB-INF/pages/list.jsp"
                       ▼
                    ⑧ View.render()  渲染视图
                       ★ 对应 BaseServlet 的 forward
    │
    ▼
⑨ HandlerInterceptor.postHandle() / afterCompletion()
    │
    ▼
响应返回浏览器
```

## 四、同一个功能的两种写法对比

### 查询列表

```java
// ===== 本模块：UserServlet =====
@WebServlet("/user/*")
public class UserServlet extends BaseServlet {
    private final UserService userService = new UserService();

    public String list(HttpServletRequest request, HttpServletResponse response) {
        List<User> userList = userService.findAll();
        request.setAttribute("userList", userList);
        return "/WEB-INF/pages/list.jsp";
    }
}

// ===== SpringMVC =====
@Controller
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @RequestMapping("/list")
    public String list(Model model) {
        model.addAttribute("userList", userService.findAll());
        return "list";                      // ViewResolver补全成 /WEB-INF/pages/list.jsp
    }
}
```

### 新增

```java
// ===== 本模块 =====
public String save(HttpServletRequest request, HttpServletResponse response) {
    User user = new User();
    user.setUsername(request.getParameter("username"));
    user.setGender(request.getParameter("gender"));
    user.setAge(parseInt(request.getParameter("age")));
    user.setAddress(request.getParameter("address"));
    userService.save(user);
    return "redirect:/user/list";
}

// ===== SpringMVC =====
@PostMapping("/save")
public String save(User user) {             // 自动封装 + 类型转换
    userService.save(user);
    return "redirect:/user/list";           // ★ 返回值约定完全一样
}
```

### JSON 接口

```java
// ===== 本模块 =====
public String listJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    StringBuilder json = new StringBuilder("{\"code\":20000,\"data\":[");
    for (int i = 0; i < userList.size(); i++) { /* 20行拼接 */ }
    json.append("]}");
    response.getWriter().write(json.toString());
    return null;
}

// ===== SpringMVC =====
@GetMapping("/listJson")
@ResponseBody
public Result listJson() {
    return new Result(20000, userService.findAll());
}
```

### 登录校验

```java
// ===== 本模块：LoginCheckFilter =====
public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
    if (WHITE_LIST.contains(path) || 已登录) {
        chain.doFilter(request, response);
        return;
    }
    response.sendRedirect(request.getContextPath() + "/user/toLogin");
}

// ===== SpringMVC：HandlerInterceptor =====
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (已登录) {
        return true;                        // 返回true放行，比chain.doFilter直观
    }
    response.sendRedirect(request.getContextPath() + "/user/toLogin");
    return false;                           // 返回false拦截
}
```

### 容器配置

```xml
<!-- ===== 传统 web.xml ===== -->
<servlet>
    <servlet-name>dispatcherServlet</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>classpath:springmvc.xml</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>dispatcherServlet</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

```java
// ===== 配置类方式（springmvc_01_quickstart 用的就是这个）=====
public class ServletContainersInitConfig extends AbstractDispatcherServletInitializer {
    protected WebApplicationContext createServletApplicationContext() {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.register(SpringMvcConfig.class);
        return ctx;
    }
    protected String[] getServletMappings() {
        return new String[]{"/"};        // ★ 就是 <url-pattern>/</url-pattern>
    }
    protected WebApplicationContext createRootApplicationContext() {
        return null;                     // ★ 就是不配 ContextLoaderListener
    }
}
```

**这两段是完全等价的。** 配置类能替代 web.xml，靠的是 Servlet 3.0 的
`ServletContainerInitializer` SPI 机制 —— 容器启动时会扫描 jar 包里
`META-INF/services/javax.servlet.ServletContainerInitializer` 声明的类并执行它。
Spring 的 `SpringServletContainerInitializer` 就是这样被调用的，
它再去找所有 `WebApplicationInitializer` 的实现类（也就是我们写的配置类）。

**为什么 `getServletMappings()` 返回 `/` 而不是 `/*`？**
见 `02-Servlet入门与配置.md` 的 url-pattern 章节 —— `/` 不拦截 JSP，`/*` 会拦截。

## 五、学习路径建议

按这个顺序看，每一步都能和本模块对应上：

| 模块 | 学什么 | 回头看本模块的哪一篇 |
| --- | --- | --- |
| `springmvc_01_quickstart` | DispatcherServlet 配置、@Controller | `02-Servlet入门与配置.md` |
| `springmvc_02_bean_load` | Spring 容器与 SpringMVC 容器的关系 | `09-Listener监听器详解.md`（ContextLoaderListener） |
| `springmvc_03_request_mapping` | @RequestMapping 路径映射 | `11-综合案例代码走读.md`（BaseServlet 的路径截取） |
| `springmvc_04_request_param` | 参数自动封装、类型转换 | `04-Request请求对象详解.md` |
| `springmvc_05_response` | @ResponseBody、JSON | `05-Response响应对象详解.md` |
| `springmvc_06_rest` | REST 风格 | `02` 的请求方式章节 |
| `springmvc_08_ssm` | 整合 MyBatis | `11` 的三层架构 |
| `springmvc_10_exception` | 全局异常处理 | `11` 的 BaseServlet 异常处理 |
| `springmvc_12_interceptor` | 拦截器 | `08-Filter过滤器详解.md` |

## 六、一句话总结

> **SpringMVC 没有创造任何新东西，它只是把「每个 Servlet 项目都要重复写的那些代码」
> 抽象成了框架，并用注解和约定让你少写配置。**
>
> 底层跑的还是 Servlet 那一套：`HttpServletRequest`、`HttpServletResponse`、
> 转发、重定向、Session、Filter —— 一个都没变。
>
> **所以 Servlet 学扎实了，SpringMVC 就只是「怎么用」的问题；
> Servlet 没学明白，SpringMVC 就全是「为什么」的问题。**

---

上一篇：[11 综合案例代码走读](11-综合案例代码走读.md)　|　下一篇：[13 常见问题 FAQ](13-常见问题FAQ.md)

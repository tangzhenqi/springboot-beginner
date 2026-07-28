# springmvc_00_servlet —— Servlet 详细案例

SpringMVC 是基于 Servlet 封装的 Web 框架，`DispatcherServlet` 本质上就是一个 Servlet。
本模块把 Servlet 的核心知识点用一个可运行的项目串起来，作为学习 SpringMVC 之前的铺垫。

## 快速开始

```bash
# 在 02-springmvc 目录下
mvn -pl springmvc_00_servlet tomcat7:run
```

访问 <http://localhost/>，首页列出了所有案例入口。测试账号：`admin` / `123456`。

> 端口被占用时，改 `pom.xml` 中 `tomcat7-maven-plugin` 的 `<port>` 即可。
> 详细的环境说明见 [docs/01-环境搭建与运行.md](docs/01-环境搭建与运行.md)。

## 📖 配套文档

**代码是骨架，文档是血肉。建议对照代码逐篇阅读。**

| # | 文档 | 内容 |
| --- | --- | --- |
| 01 | [环境搭建与运行](docs/01-环境搭建与运行.md) | 依赖为什么这样配、provided 的作用、war 包结构、WEB-INF 的意义、三种运行方式 |
| 02 | [Servlet 入门与配置](docs/02-Servlet入门与配置.md) | Servlet 是什么、继承体系、`HttpServlet.service` 源码、注解与 xml 两种配置、**url-pattern 四种匹配规则**、ServletConfig 与 ServletContext |
| 03 | [Servlet 生命周期](docs/03-Servlet生命周期.md) | **四阶段详解（面试高频）**、loadOnStartup、单例多线程与线程安全、面试答法参考 |
| 04 | [Request 请求对象详解](docs/04-Request请求对象详解.md) | 请求行/头/参数完整 API、真实 IP 获取、**中文乱码原理与解决**、request 域、请求转发 |
| 05 | [Response 响应对象详解](docs/05-Response响应对象详解.md) | 字符流与字节流、响应乱码、返回 JSON、**重定向原理（302+Location）**、文件下载、状态码速查 |
| 06 | [域对象与转发重定向](docs/06-域对象与转发重定向.md) | **四大域对象对比与选型**、转发 vs 重定向完整对比表、**路径该不该加虚拟目录** |
| 07 | [会话技术 Cookie 与 Session](docs/07-会话技术Cookie与Session.md) | Cookie 原理与 API、setMaxAge/setPath 的坑、**Session 底层原理（JSESSIONID）**、两者对比、分布式 Session 方案 |
| 08 | [Filter 过滤器详解](docs/08-Filter过滤器详解.md) | 生命周期、**FilterChain 责任链**、执行顺序规则、**dispatcherTypes 的坑**、包装 request、**Filter vs Interceptor** |
| 09 | [Listener 监听器详解](docs/09-Listener监听器详解.md) | 8 个监听器接口、在线人数统计、**Spring 的 ContextLoaderListener 剖析** |
| 10 | [JSP 与 EL、JSTL](docs/10-JSP与EL、JSTL.md) | **JSP 的本质就是 Servlet**、9 个内置对象、EL 取值规则、JSTL 常用标签、为什么 JSP 被淘汰了 |
| 11 | [综合案例代码走读](docs/11-综合案例代码走读.md) | 三层架构、**BaseServlet 反射分发完整剖析**、请求的完整生命周期、九个功能逐个拆解、离生产还差什么 |
| 12 | [从 Servlet 到 SpringMVC](docs/12-从Servlet到SpringMVC.md) | **纯 Servlet 的七个痛点**、DispatcherServlet 执行流程、**同一功能两种写法对比**、学习路径建议 |
| 13 | [常见问题 FAQ](docs/13-常见问题FAQ.md) | 按报错现象索引：404/405/500、各类乱码、Filter 不生效、无限重定向、EL 不解析、调试技巧 |

## 目录结构

```
src/main/java/com/springmvc/
├── domain/User.java                   实体类
├── dao/UserDao.java                   数据层（ConcurrentHashMap 模拟数据库）
├── service/UserService.java           业务层
├── servlet/
│   ├── HelloServlet.java              入门：@WebServlet 注解、doGet/doPost
│   ├── LifeCycleServlet.java          生命周期：构造 → init → service → destroy
│   ├── XmlConfigServlet.java          传统 web.xml 配置方式
│   ├── RequestDemoServlet.java        request：请求行/头/参数、域对象、转发
│   ├── ResponseDemoServlet.java       response：字符流/json/重定向/下载/状态码/响应头
│   ├── BaseServlet.java               反射分发的通用基类（DispatcherServlet 简化版）
│   └── UserServlet.java               综合案例：登录 + 用户增删改查
├── filter/
│   ├── CharacterEncodingFilter.java   统一编码，解决 POST 中文乱码
│   └── LoginCheckFilter.java          登录校验，拦截 /user/*
└── listener/AppContextListener.java   应用启动初始化 + 在线人数统计

src/main/webapp/
├── index.jsp                          案例导航首页
├── css/common.css                     公共样式
└── WEB-INF/
    ├── web.xml                        xml 配置 Servlet、会话超时、欢迎页、错误页
    └── pages/                         登录、列表、新增、修改、转发目标页、错误页
```

## 案例入口一览

| 访问地址 | 演示内容 | 对应文档 |
| --- | --- | --- |
| `/hello` | 最简单的 Servlet | 02 |
| `/lifecycle` | 生命周期四阶段（**看控制台输出**） | 03 |
| `/xmlServlet` | web.xml 配置方式 | 02 |
| `/req?username=张三&hobby=java&hobby=mysql` | request 全部 API + 转发 | 04 |
| `/resp` | response 六种响应方式 | 05 |
| `/user/list` | **综合案例**：登录 + 增删改查 | 11 |
| `/user/listJson` | JSON 接口 | 05 |
| `/user/notExist` | 404 错误页 | 13 |

## 学完这个模块，你应该能回答

1. Servlet 的生命周期是怎样的？为什么不能在 Servlet 里定义成员变量？
2. 转发和重定向有什么区别？什么时候用哪个？路径要不要加虚拟目录？
3. Cookie 和 Session 有什么区别？Session 的底层原理是什么？
4. POST 和 GET 的中文乱码分别怎么解决？为什么要在取参数之前设置编码？
5. Filter 和 Interceptor 有什么区别？
6. `url-pattern` 配 `/` 和 `/*` 有什么区别？为什么 DispatcherServlet 用 `/`？
7. 为什么 JSP 要放在 WEB-INF 目录下？
8. SpringMVC 到底帮我们做了什么？

答不上来的，回到对应的文档再看一遍。

---

下一站：[springmvc_01_quickstart](../springmvc_01_quickstart) —— 用 SpringMVC 重写这套逻辑，
先读 [12 从 Servlet 到 SpringMVC](docs/12-从Servlet到SpringMVC.md)。

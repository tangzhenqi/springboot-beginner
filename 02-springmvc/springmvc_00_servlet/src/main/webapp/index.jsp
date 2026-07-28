<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%--
    首页：所有案例的入口
    ${pageContext.request.contextPath} 取项目虚拟目录，避免部署路径变化后链接失效
--%>
<html>
<head>
    <title>Servlet详细案例</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container">
    <h2>Servlet 详细案例</h2>
    <p class="tip">
        应用名称（取自ServletContext域）：${applicationScope.appName}　|
        当前在线会话数：${applicationScope.onlineCount == null ? 0 : applicationScope.onlineCount}
    </p>

    <h3>一、Servlet 基础</h3>
    <ul>
        <li><a href="${pageContext.request.contextPath}/hello">入门案例 /hello</a> —— 注解配置、doGet与doPost</li>
        <li><a href="${pageContext.request.contextPath}/lifecycle">生命周期 /lifecycle</a> —— 构造、init、service、destroy（看控制台输出，反复刷新观察计数）</li>
        <li><a href="${pageContext.request.contextPath}/xmlServlet">xml配置 /xmlServlet</a> —— web.xml 配置方式与初始化参数</li>
    </ul>

    <h3>二、请求与响应</h3>
    <ul>
        <li><a href="${pageContext.request.contextPath}/req?username=张三&hobby=java&hobby=mysql">request对象 /req</a>
            —— 请求行、请求头、请求参数、域对象、请求转发
        </li>
        <li>
            <a href="${pageContext.request.contextPath}/resp">response对象 /resp</a>
            —— 字符流、json、重定向、下载、状态码、响应头
        </li>
        <li>
            POST请求中文测试：
            <form action="${pageContext.request.contextPath}/req" method="post" style="display:inline">
                <input type="text" name="username" value="李四中文" style="width:120px">
                <button class="btn" type="submit">提交</button>
            </form>
        </li>
    </ul>

    <h3>三、综合案例：登录 + 用户增删改查</h3>
    <ul>
        <li><a href="${pageContext.request.contextPath}/user/list">进入用户管理 /user/list</a>
            —— 未登录会被 LoginCheckFilter 拦截并跳转到登录页
        </li>
        <li>测试账号：<b>admin</b> / <b>123456</b></li>
        <li>涉及：BaseServlet反射分发、session会话、cookie记住我、过滤器、监听器、JSP+JSTL视图</li>
    </ul>

    <h3>四、异常处理</h3>
    <ul>
        <li><a href="${pageContext.request.contextPath}/user/notExist">访问不存在的方法 /user/notExist</a>
            —— 登录后访问会触发 BaseServlet 的404，交给 web.xml 中配置的错误页处理（未登录则先被过滤器拦截）
        </li>
    </ul>
</div>
</body>
</html>

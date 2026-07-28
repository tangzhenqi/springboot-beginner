<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%--
    登录页。放在WEB-INF下，浏览器无法直接访问，只能由Servlet转发过来（/user/toLogin）
--%>
<html>
<head>
    <title>用户登录</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container" style="max-width:460px">
    <h2>用户登录</h2>

    <%-- EL表达式${errorMsg}会依次从 page -> request -> session -> application 域中查找，取不到时输出空串 --%>
    <div class="error">${errorMsg}</div>

    <form action="${pageContext.request.contextPath}/user/login" method="post">
        <div class="form-item">
            <label>用户名</label>
            <%-- 登录失败回显用户名；首次进入则回显cookie中记住的用户名 --%>
            <input type="text" name="username" value="${not empty username ? username : rememberedUsername}">
        </div>
        <div class="form-item">
            <label>密码</label>
            <input type="password" name="password">
        </div>
        <div class="form-item">
            <label></label>
            <input type="checkbox" name="remember" value="true"> 记住用户名（写入cookie，7天有效）
        </div>
        <div class="form-item">
            <label></label>
            <button class="btn" type="submit">登录</button>
            <a class="btn btn-plain" href="${pageContext.request.contextPath}/">返回首页</a>
        </div>
    </form>

    <p class="tip">测试账号：admin / 123456</p>
</div>
</body>
</html>

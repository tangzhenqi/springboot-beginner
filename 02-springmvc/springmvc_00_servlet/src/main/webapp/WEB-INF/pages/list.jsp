<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%-- 引入JSTL核心标签库，用于遍历、判断（pom中已引入jstl依赖） --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
    <title>用户列表</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container">
    <h2>用户列表</h2>

    <p class="tip">
        <%-- sessionScope显式指定从session域取值，loginUser由UserServlet登录成功后存入 --%>
        当前登录用户：<b>${sessionScope.loginUser.username}</b>
        （session id：${pageContext.session.id}）
        <a href="${pageContext.request.contextPath}/user/logout">退出登录</a>
        ｜ <a href="${pageContext.request.contextPath}/">返回首页</a>
    </p>

    <div class="error">${errorMsg}</div>

    <a class="btn" href="${pageContext.request.contextPath}/user/toAdd">新增用户</a>
    <a class="btn btn-plain" href="${pageContext.request.contextPath}/user/listJson" target="_blank">查看json接口</a>

    <table>
        <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>性别</th>
            <th>年龄</th>
            <th>住址</th>
            <th>操作</th>
        </tr>
        <%-- items为要遍历的集合，var为每次遍历的变量名，varStatus可获取序号等状态 --%>
        <c:forEach items="${userList}" var="user">
            <tr>
                <td>${user.id}</td>
                <td>${user.username}</td>
                <td>${user.gender}</td>
                <td>${user.age}</td>
                <td>${user.address}</td>
                <td>
                    <a href="${pageContext.request.contextPath}/user/toUpdate?id=${user.id}">修改</a>
                    &nbsp;
                    <%-- onclick返回false则阻止a标签的默认跳转行为 --%>
                    <a href="${pageContext.request.contextPath}/user/delete?id=${user.id}"
                       onclick="return confirm('确定要删除【${user.username}】吗？')">删除</a>
                </td>
            </tr>
        </c:forEach>
        <%-- 集合为空时的兜底展示 --%>
        <c:if test="${empty userList}">
            <tr>
                <td colspan="6">暂无数据</td>
            </tr>
        </c:if>
    </table>
</div>
</body>
</html>

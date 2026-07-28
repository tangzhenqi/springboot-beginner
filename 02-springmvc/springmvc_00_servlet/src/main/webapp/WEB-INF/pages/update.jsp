<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>修改用户</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container" style="max-width:520px">
    <h2>修改用户</h2>

    <div class="error">${errorMsg}</div>

    <form action="${pageContext.request.contextPath}/user/update" method="post">
        <%-- 主键id不允许用户修改，用隐藏域随表单一起提交，服务端据此定位要更新的记录 --%>
        <input type="hidden" name="id" value="${user.id}">
        <div class="form-item">
            <label>用户名</label>
            <input type="text" name="username" value="${user.username}">
        </div>
        <div class="form-item">
            <label>密码</label>
            <input type="password" name="password" value="${user.password}">
        </div>
        <div class="form-item">
            <label>性别</label>
            <%-- 单选框回显：值相等时输出checked属性 --%>
            <input type="radio" name="gender" value="男" ${user.gender eq '男' ? 'checked' : ''}> 男
            <input type="radio" name="gender" value="女" ${user.gender eq '女' ? 'checked' : ''}> 女
        </div>
        <div class="form-item">
            <label>年龄</label>
            <input type="number" name="age" value="${user.age}">
        </div>
        <div class="form-item">
            <label>住址</label>
            <input type="text" name="address" value="${user.address}">
        </div>
        <div class="form-item">
            <label></label>
            <button class="btn" type="submit">保存修改</button>
            <a class="btn btn-plain" href="${pageContext.request.contextPath}/user/list">返回列表</a>
        </div>
    </form>
</div>
</body>
</html>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>新增用户</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container" style="max-width:520px">
    <h2>新增用户</h2>

    <div class="error">${errorMsg}</div>

    <%--
        表单提交要点：
        1.method必须是post，get会把密码等参数暴露在地址栏，且长度受限
        2.input的name属性要与User实体的属性名一致，服务端才能按名字取值封装
    --%>
    <form action="${pageContext.request.contextPath}/user/save" method="post">
        <div class="form-item">
            <label>用户名</label>
            <input type="text" name="username" value="${user.username}">
        </div>
        <div class="form-item">
            <label>密码</label>
            <input type="password" name="password" placeholder="不填默认123456">
        </div>
        <div class="form-item">
            <label>性别</label>
            <input type="radio" name="gender" value="男" checked> 男
            <input type="radio" name="gender" value="女"> 女
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
            <button class="btn" type="submit">保存</button>
            <a class="btn btn-plain" href="${pageContext.request.contextPath}/user/list">返回列表</a>
        </div>
    </form>
</div>
</body>
</html>

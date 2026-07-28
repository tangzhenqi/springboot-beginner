<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>请求转发的目标页面</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container">
    <h2>请求转发的目标页面</h2>
    <p class="tip">
        注意浏览器地址栏仍然是 /req?forward=true...，说明转发是服务器内部跳转，只发生了一次请求。<br/>
        本页面位于 WEB-INF 目录下，浏览器无法直接访问，只能通过转发到达。
    </p>

    <h3>从request域中取出的数据</h3>
    <%-- EL表达式取出Servlet中 request.setAttribute("msg", ...) 存入的数据 --%>
    <p>${msg}</p>

    <h3>Servlet中收集到的请求信息</h3>
    <%-- 值中包含html标签，EL默认不转义，这里直接渲染 --%>
    ${html}

    <p><a href="${pageContext.request.contextPath}/">返回首页</a></p>
</div>
</body>
</html>

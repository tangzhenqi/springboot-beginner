<%-- isErrorPage="true"后，页面中才能使用内置的exception对象 --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<html>
<head>
    <title>出错了</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css">
</head>
<body>
<div class="container">
    <h2>出错了</h2>
    <p class="tip">
        本页面由 web.xml 中的 &lt;error-page&gt; 配置，统一处理404与500，避免把异常堆栈直接暴露给用户。<br/>
        springmvc中对应的做法是 @ControllerAdvice + @ExceptionHandler（见 springmvc_10_exception 模块）。
    </p>

    <%-- 以下属性由容器在转发到错误页时放入request域 --%>
    <p>状态码：${requestScope['javax.servlet.error.status_code']}</p>
    <p>出错路径：${requestScope['javax.servlet.error.request_uri']}</p>
    <p>错误信息：${requestScope['javax.servlet.error.message']}</p>

    <p><a class="btn" href="${pageContext.request.contextPath}/">返回首页</a></p>
</div>
</body>
</html>

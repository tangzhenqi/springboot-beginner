package com.springmvc.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;

/**
 * HttpServletRequest 请求对象案例
 *
 * request由tomcat创建，封装了本次请求的全部数据（请求行、请求头、请求体），一次请求对应一个request对象
 * 对应springmvc中：@RequestParam、@RequestHeader、@RequestBody 等注解的底层数据来源
 *
 * 访问地址：http://localhost/req?username=张三&hobby=java&hobby=mysql
 */
@WebServlet("/req")
public class RequestDemoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //=============== 中文乱码处理 ===============
        //POST请求参数在请求体中，必须在获取参数之前设置请求体编码，否则中文乱码
        //GET请求参数在url中，由服务器的uriEncoding决定（tomcat8以后默认UTF-8，本模块pom中也已配置）
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        StringBuilder html = new StringBuilder("<h3>一、请求行数据</h3>");

        //=============== 1.请求行 ===============
        //请求方式：GET / POST
        html.append("请求方式 getMethod()：").append(request.getMethod()).append("<br/>");
        //虚拟目录（项目访问路径），本模块部署在根路径下所以为空字符串
        html.append("项目路径 getContextPath()：").append(request.getContextPath()).append("<br/>");
        //请求URI：虚拟目录 + servlet路径，如 /req
        html.append("请求URI getRequestURI()：").append(request.getRequestURI()).append("<br/>");
        //请求URL：完整地址，如 http://localhost/req
        html.append("请求URL getRequestURL()：").append(request.getRequestURL()).append("<br/>");
        //问号后面的查询参数字符串，POST请求为null
        html.append("查询参数 getQueryString()：").append(request.getQueryString()).append("<br/>");
        //客户端ip，做日志、限流时常用
        html.append("客户端IP getRemoteAddr()：").append(request.getRemoteAddr()).append("<br/>");

        //=============== 2.请求头 ===============
        html.append("<h3>二、请求头数据</h3>");
        //User-Agent：浏览器信息，可用于判断客户端类型
        html.append("User-Agent：").append(request.getHeader("User-Agent")).append("<br/>");
        //Referer：从哪个页面跳转过来的，可用于防盗链、统计来源
        html.append("Referer：").append(request.getHeader("Referer")).append("<br/>");
        html.append("<b>全部请求头：</b><br/>");
        //getHeaderNames返回的是Enumeration（早期的迭代器）
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            html.append("&nbsp;&nbsp;").append(name).append(" = ").append(request.getHeader(name)).append("<br/>");
        }

        //=============== 3.请求参数 ===============
        html.append("<h3>三、请求参数（GET与POST获取方式完全一致）</h3>");
        //获取单个参数值，参数不存在返回null（注意判空，避免空指针）
        String username = request.getParameter("username");
        html.append("getParameter(\"username\")：").append(username).append("<br/>");
        //获取同名的多个参数值，如复选框，参数不存在返回null
        String[] hobbies = request.getParameterValues("hobby");
        html.append("getParameterValues(\"hobby\")：").append(Arrays.toString(hobbies)).append("<br/>");
        //获取全部参数，key是参数名，value是值数组（springmvc封装实体对象时用的就是它）
        html.append("<b>getParameterMap()：</b><br/>");
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            html.append("&nbsp;&nbsp;").append(entry.getKey())
                    .append(" = ").append(Arrays.toString(entry.getValue())).append("<br/>");
        }

        //=============== 4.域对象 + 请求转发 ===============
        //request是一个域对象，作用范围：一次请求内（转发的多个资源之间可以共享数据）
        request.setAttribute("msg", "这条数据由 RequestDemoServlet 存入request域，再由转发的目标页面取出");

        //请求转发：服务器内部跳转，浏览器地址栏不变，只发送一次请求，可以访问WEB-INF下的资源
        //与重定向的区别见 ResponseDemoServlet
        if ("true".equals(request.getParameter("forward"))) {
            request.setAttribute("html", html.toString());
            request.getRequestDispatcher("/WEB-INF/pages/request.jsp").forward(request, response);
            return;
        }

        html.append("<h3>四、请求转发</h3>")
                .append("<a href='").append(request.getContextPath()).append("/req?forward=true&username=张三&hobby=java&hobby=mysql'>")
                .append("点击测试转发到 /WEB-INF/pages/request.jsp（地址栏不变）</a><br/>")
                .append("<br/><a href='").append(request.getContextPath()).append("/'>返回首页</a>");
        response.getWriter().write(html.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}

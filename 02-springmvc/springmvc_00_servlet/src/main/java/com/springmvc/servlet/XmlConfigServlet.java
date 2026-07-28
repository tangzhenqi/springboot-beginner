package com.springmvc.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 传统的web.xml配置方式（servlet3.0之前唯一的方式）
 *
 * 本类没有@WebServlet注解，访问路径配置在 webapp/WEB-INF/web.xml 中：
 *   <servlet>
 *       <servlet-name>xmlConfigServlet</servlet-name>
 *       <servlet-class>com.springmvc.servlet.XmlConfigServlet</servlet-class>
 *   </servlet>
 *   <servlet-mapping>
 *       <servlet-name>xmlConfigServlet</servlet-name>
 *       <url-pattern>/xmlServlet</url-pattern>
 *   </servlet-mapping>
 *
 * 注解与xml等价，二选一即可。学习springmvc时要理解：
 * DispatcherServlet本质上也是一个Servlet，只是它的配置从web.xml换成了配置类
 *
 * 访问地址：http://localhost/xmlServlet
 */
public class XmlConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<h3>我是通过 web.xml 配置访问路径的Servlet</h3>"
                //<init-param>配置的初始化参数，读取方式与注解方式完全一样
                + "web.xml中配置的初始化参数 desc：" + getServletConfig().getInitParameter("desc") + "<br/>"
                + "<a href='" + request.getContextPath() + "/'>返回首页</a>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}

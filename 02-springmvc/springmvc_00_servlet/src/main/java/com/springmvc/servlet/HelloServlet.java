package com.springmvc.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 入门案例：最简单的Servlet
 *
 * 1.继承HttpServlet，重写doGet/doPost方法
 * 2.@WebServlet注解设置访问路径（servlet3.0以后支持，等同于web.xml中的<servlet>+<servlet-mapping>）
 * 3.访问地址：http://localhost/hello
 */
//urlPatterns属性只有一个值时，可以简写为 @WebServlet("/hello")
@WebServlet(urlPatterns = "/hello", name = "helloServlet")
public class HelloServlet extends HttpServlet {

    //浏览器地址栏直接输入地址、a标签、img标签等发起的都是get请求
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("HelloServlet 收到了一次get请求");
        //设置响应体的MIME类型与字符集，必须写在获取输出流之前，否则中文乱码
        response.setContentType("text/html;charset=UTF-8");
        //通过响应对象获取字符输出流，把数据写回浏览器
        response.getWriter().write("<h2>Hello Servlet ~ 你好，Servlet</h2>");
    }

    //表单method="post"、ajax的post请求会进入该方法
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //实际开发中get与post的处理逻辑往往一致，直接调用doGet即可
        doGet(request, response);
    }
}

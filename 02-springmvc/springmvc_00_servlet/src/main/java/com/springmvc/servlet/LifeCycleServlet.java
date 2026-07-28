package com.springmvc.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet生命周期案例（重点，面试常问）
 *
 * 一个Servlet在容器中只有一个实例（单例），生命周期由tomcat管理，分为4个阶段：
 * 1.实例化：调用无参构造，创建对象。默认第一次访问时创建，配置loadOnStartup>=0则服务器启动时创建
 * 2.初始化：调用init(ServletConfig)，只执行一次，可以在此加载配置、创建连接池等
 * 3.服务：每次请求都会调用一次service()，service内部根据请求方式分发给doGet/doPost/doPut/doDelete
 * 4.销毁：服务器正常关闭前调用destroy()，只执行一次，可以在此释放资源
 *
 * 结论：多个请求共用同一个Servlet实例（多线程），所以Servlet中不要定义有状态的成员变量，否则线程不安全
 *
 * 访问地址：http://localhost/lifecycle
 */
@WebServlet(
        urlPatterns = "/lifecycle",
        //服务器启动时就实例化并初始化，值越小优先级越高；不配置（默认-1）则第一次访问时才创建
        loadOnStartup = 1,
        //初始化参数，通过ServletConfig获取，等同于web.xml中的<init-param>
        initParams = {
                @WebInitParam(name = "author", value = "springmvc-beginner"),
                @WebInitParam(name = "version", value = "1.0")
        }
)
public class LifeCycleServlet extends HttpServlet {

    //统计访问次数，用来证明"多个请求共用一个Servlet实例"
    private int count = 0;

    //阶段1：实例化。tomcat通过反射调用无参构造创建对象，只执行一次
    public LifeCycleServlet() {
        System.out.println("【1-实例化】LifeCycleServlet 构造方法执行，对象地址：" + super.toString());
    }

    //阶段2：初始化。参数config中封装了当前Servlet的配置信息（初始化参数、Servlet名称等）
    @Override
    public void init(ServletConfig config) throws ServletException {
        //必须调用父类的init，否则getServletConfig()、getServletContext()会返回null
        super.init(config);
        System.out.println("【2-初始化】init方法执行，servlet名称：" + config.getServletName());
        System.out.println("           初始化参数 author = " + config.getInitParameter("author"));
        System.out.println("           初始化参数 version = " + config.getInitParameter("version"));
    }

    //阶段3：服务。每次请求执行一次。父类HttpServlet的service会按请求方式分发到doGet/doPost
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        count++;
        System.out.println("【3-服务】doGet方法执行，第 " + count + " 次访问，对象地址：" + super.toString());

        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<h3>Servlet生命周期演示</h3>"
                + "当前对象：" + super.toString() + "<br/>"
                + "累计访问次数：" + count + " 次（刷新页面会持续累加，说明多次请求共用同一个实例）<br/>"
                + "初始化参数 author：" + getServletConfig().getInitParameter("author") + "<br/>"
                + "<a href='" + request.getContextPath() + "/'>返回首页</a>");
    }

    //阶段4：销毁。服务器正常关闭（如控制台ctrl+c）时执行一次，kill -9强杀不会执行
    @Override
    public void destroy() {
        System.out.println("【4-销毁】destroy方法执行，共处理了 " + count + " 次请求，资源已释放");
    }
}

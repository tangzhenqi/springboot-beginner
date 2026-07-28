package com.springmvc.listener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监听器：监听web应用中对象的创建、销毁与属性变化
 *
 * 本类同时实现了两个监听器接口：
 *   ServletContextListener  监听ServletContext（应用级对象）的创建与销毁 -> 做应用启动初始化
 *   HttpSessionListener     监听session的创建与销毁 -> 统计在线人数
 *
 * spring在web环境下的 ContextLoaderListener 就是一个ServletContextListener，
 * 它在服务器启动时读取配置、创建spring容器并存入ServletContext域
 */
@WebListener
public class AppContextListener implements ServletContextListener, HttpSessionListener {

    //在线人数（会话数），多线程访问所以用原子类
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    /**
     * 服务器启动、应用加载完成后执行一次（早于所有Servlet的初始化）
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        //ServletContext（application域）：整个web应用共享一个，作用范围最大，服务器关闭才销毁
        //三大域对象作用范围：request（一次请求） < session（一次会话） < servletContext（整个应用）
        ServletContext context = sce.getServletContext();
        context.setAttribute("appName", "Servlet详细案例");
        context.setAttribute("startTime", System.currentTimeMillis());

        System.out.println("==================================================");
        System.out.println("【Listener】应用启动完成：" + context.getServletContextName());
        System.out.println("【Listener】服务器信息：" + context.getServerInfo());
        System.out.println("【Listener】真实磁盘路径：" + context.getRealPath("/"));
        System.out.println("【Listener】在此可以做：加载配置、创建spring容器、初始化连接池等");
        System.out.println("==================================================");
    }

    /**
     * 服务器正常关闭前执行一次
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("【Listener】应用关闭，释放全局资源（连接池、定时任务等）");
    }

    /**
     * 每有一个新会话（新浏览器第一次访问并创建session）就执行一次
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        int count = ONLINE_COUNT.incrementAndGet();
        //把在线人数放入application域，任何页面都能取到
        se.getSession().getServletContext().setAttribute("onlineCount", count);
        System.out.println("【Listener】新会话创建 " + se.getSession().getId() + "，当前在线：" + count);
    }

    /**
     * 会话销毁（调用invalidate或超过session-timeout）时执行
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        int count = ONLINE_COUNT.decrementAndGet();
        se.getSession().getServletContext().setAttribute("onlineCount", count);
        System.out.println("【Listener】会话销毁，当前在线：" + count);
    }
}

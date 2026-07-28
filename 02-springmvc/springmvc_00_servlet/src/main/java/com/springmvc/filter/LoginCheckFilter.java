package com.springmvc.filter;

import com.springmvc.servlet.UserServlet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 登录校验过滤器：拦截 /user/* 下的所有请求，未登录一律跳回登录页
 *
 * 注意类名以L开头，字典序在CharacterEncodingFilter(C)之后，所以编码过滤器会先执行
 * 对应springmvc中的拦截器 HandlerInterceptor（见 springmvc_12_interceptor 模块）
 *
 * 过滤器 Filter 与 拦截器 Interceptor 的区别：
 *   Filter属于Servlet规范，可以拦截包括静态资源在内的所有请求
 *   Interceptor属于springmvc框架，只拦截由DispatcherServlet处理的请求，能拿到handler方法信息
 */
@WebFilter(urlPatterns = "/user/*")
public class LoginCheckFilter implements Filter {

    //白名单：不需要登录就能访问的路径
    private static final List<String> WHITE_LIST = Arrays.asList("/user/toLogin", "/user/login");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("【Filter】LoginCheckFilter 初始化完成，放行白名单：" + WHITE_LIST);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        //Filter接口的参数是ServletRequest（协议无关），要用http特有的方法必须先向下转型
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        //去掉项目虚拟目录，得到与白名单可比较的路径，如 /user/login
        String path = request.getRequestURI().substring(request.getContextPath().length());

        //1.白名单直接放行
        if (WHITE_LIST.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        //2.已登录放行
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(UserServlet.SESSION_USER) != null) {
            chain.doFilter(request, response);
            return;
        }

        //3.未登录，不放行
        System.out.println("【Filter】拦截未登录请求：" + path);
        //ajax请求返回json，页面请求做重定向，避免ajax收到一整个登录页的html
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40100,\"msg\":\"未登录\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/user/toLogin");
        }
    }

    @Override
    public void destroy() {
        System.out.println("【Filter】LoginCheckFilter 销毁");
    }
}

package com.springmvc.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 通用Servlet基类（重点：这就是springmvc中DispatcherServlet的简化版）
 *
 * 问题：一个Servlet只能处理一个请求路径，用户的增删改查就要写4个Servlet，类爆炸
 * 解决：一个Servlet映射 /user/*，根据请求路径的最后一段，用反射调用子类中的同名方法
 *
 * 对照springmvc：
 *   本类的 service()        ->  DispatcherServlet.doDispatch()
 *   路径截取 + 反射找方法    ->  HandlerMapping（找到处理器）
 *   method.invoke()        ->  HandlerAdapter（执行处理器）
 *   返回值前缀 forward/redirect -> ViewResolver（视图解析）
 *
 * 子类中的处理方法必须满足：public 修饰、返回String、参数为(HttpServletRequest, HttpServletResponse)
 * 返回值约定：
 *   "/WEB-INF/pages/xx.jsp"  转发到该页面
 *   "redirect:/user/list"    重定向到该地址（会自动补上项目虚拟目录）
 *   null                     方法内部已自己响应（如直接写json），不再处理
 */
public abstract class BaseServlet extends HttpServlet {

    //重定向的返回值前缀，与springmvc保持一致
    private static final String REDIRECT_PREFIX = "redirect:";

    /**
     * 覆盖HttpServlet的service方法，接管所有请求方式的分发（不再区分doGet/doPost）
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //1.从请求路径中截取要执行的方法名，如 /user/list -> list
        String uri = request.getRequestURI();
        String methodName = uri.substring(uri.lastIndexOf('/') + 1);
        if (methodName.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "请求路径不完整：" + uri);
            return;
        }

        try {
            //2.获取当前对象（子类，如UserServlet）中声明的同名方法
            //  用getClass()而不是BaseServlet.class，保证拿到的是运行时的真实子类
            Method method = getClass().getDeclaredMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);

            //3.反射调用该方法，得到视图返回值
            Object result = method.invoke(this, request, response);

            //4.根据返回值决定跳转方式
            if (result instanceof String) {
                String view = (String) result;
                if (view.startsWith(REDIRECT_PREFIX)) {
                    //重定向：路径要带上项目虚拟目录，因为是浏览器再次发起的请求
                    response.sendRedirect(request.getContextPath() + view.substring(REDIRECT_PREFIX.length()));
                } else {
                    //转发：服务器内部跳转，路径不带虚拟目录，可以访问WEB-INF下的页面
                    request.getRequestDispatcher(view).forward(request, response);
                }
            }
            //result为null表示方法内部已经响应过了，这里什么都不做
        } catch (NoSuchMethodException e) {
            //路径写错或方法名不匹配，返回404而不是500，语义更准确
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "找不到处理方法：" + getClass().getSimpleName() + "#" + methodName + "()");
        } catch (IllegalAccessException | InvocationTargetException e) {
            //业务方法内部抛出的异常会被包装成InvocationTargetException，这里统一转成500
            //对应springmvc中的 @ControllerAdvice + @ExceptionHandler 全局异常处理
            throw new ServletException("请求处理失败：" + methodName, e.getCause() == null ? e : e.getCause());
        }
    }
}

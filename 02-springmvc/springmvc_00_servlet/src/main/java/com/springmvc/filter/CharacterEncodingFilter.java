package com.springmvc.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import java.io.IOException;

/**
 * 编码过滤器：统一解决POST请求中文乱码
 *
 * Filter是Servlet规范的三大组件之一（Servlet、Filter、Listener），
 * 作用：在请求到达Servlet之前、响应返回浏览器之前做统一处理，
 * 常用于：编码处理、登录校验、敏感词过滤、日志记录、跨域处理
 *
 * 本类等价于spring提供的 org.springframework.web.filter.CharacterEncodingFilter，
 * 在springmvc模块中直接在web容器配置类中注册即可，不用自己写
 */
//"/*"表示拦截所有请求；多个过滤器的执行顺序按类名的字典序（xml配置则按<filter-mapping>的先后顺序）
@WebFilter(urlPatterns = "/*", initParams = @WebInitParam(name = "encoding", value = "UTF-8"))
public class CharacterEncodingFilter implements Filter {

    private String encoding;

    //初始化：服务器启动时执行一次
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.encoding = filterConfig.getInitParameter("encoding");
        if (this.encoding == null) {
            this.encoding = "UTF-8";
        }
        System.out.println("【Filter】CharacterEncodingFilter 初始化完成，编码：" + encoding);
    }

    //每次请求都会执行
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        //---------- 放行前：对request做增强 ----------
        //设置请求体编码，对POST有效（GET参数在url上，由服务器的uriEncoding决定）
        request.setCharacterEncoding(encoding);
        //设置响应编码，避免每个Servlet都写一遍setContentType
        response.setCharacterEncoding(encoding);

        //放行，把请求交给下一个过滤器或目标Servlet。不调用则请求到此为止
        chain.doFilter(request, response);

        //---------- 放行后：对response做增强 ----------
        //目标资源执行完后会回到这里，可以做统计耗时、清理资源等收尾工作
    }

    //销毁：服务器正常关闭时执行一次
    @Override
    public void destroy() {
        System.out.println("【Filter】CharacterEncodingFilter 销毁");
    }
}

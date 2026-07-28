package com.springmvc.servlet;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletResponse 响应对象案例
 *
 * response由tomcat创建，用来设置响应行（状态码）、响应头、响应体，一次请求对应一个response对象
 * 对应springmvc中：@ResponseBody（写字符流）、return "redirect:/xxx"（重定向）等
 *
 * 访问地址：http://localhost/resp?type=text
 * type取值：text（默认）、json、redirect、download、status、refresh
 */
@WebServlet("/resp")
public class ResponseDemoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String type = request.getParameter("type");
        if (type == null) {
            type = "text";
        }

        switch (type) {
            case "json":
                writeJson(response);
                break;
            case "redirect":
                redirect(request, response);
                break;
            case "download":
                download(response);
                break;
            case "status":
                status(response);
                break;
            case "refresh":
                refresh(request, response);
                break;
            default:
                writeText(request, response);
        }
    }

    /**
     * 一、响应体：字符流输出html
     * 注意：字符流getWriter()与字节流getOutputStream()互斥，同一次响应中只能使用其中一个
     */
    private void writeText(HttpServletRequest request, HttpServletResponse response) throws IOException {
        //Content-Type响应头：告诉浏览器数据格式与字符集。必须在获取流之前调用，否则中文乱码
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        String ctx = request.getContextPath();
        writer.write("<h3>response响应案例</h3>");
        writer.write("当前使用字符流 response.getWriter() 输出html，中文正常显示<br/><br/>");
        writer.write("<a href='" + ctx + "/resp?type=json'>1.响应json数据（@ResponseBody的底层）</a><br/>");
        writer.write("<a href='" + ctx + "/resp?type=redirect'>2.重定向到首页（地址栏改变，两次请求）</a><br/>");
        writer.write("<a href='" + ctx + "/resp?type=download'>3.文件下载（字节流 + Content-Disposition响应头）</a><br/>");
        writer.write("<a href='" + ctx + "/resp?type=status'>4.自定义响应状态码404</a><br/>");
        writer.write("<a href='" + ctx + "/resp?type=refresh'>5.定时刷新跳转（Refresh响应头）</a><br/>");
        writer.write("<br/><a href='" + ctx + "/'>返回首页</a>");
        //writer由tomcat管理，会自动关闭，无需手动close
    }

    /**
     * 二、响应json：只是把Content-Type改成application/json，本质仍是输出字符串
     * springmvc中@ResponseBody + jackson做的就是"对象 -> json字符串 -> 写入响应体"
     */
    private void writeJson(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":20000,\"msg\":\"操作成功\",\"data\":{\"id\":1,\"name\":\"张三\"}}");
    }

    /**
     * 三、重定向：告诉浏览器再访问一个新地址
     *
     * 转发 forward                        重定向 redirect
     * 一次请求，地址栏不变                 两次请求，地址栏改变
     * 服务器内部跳转，只能跳本项目资源      浏览器发起，可以跳外部站点
     * 可以共享request域数据                 request域数据丢失（需用session传递）
     * 可以访问WEB-INF下的资源              不能访问WEB-INF下的资源
     */
    private void redirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        //sendRedirect底层等价于：setStatus(302) + setHeader("Location", url)
        //路径要加上项目虚拟目录，因为这个地址是给浏览器用的
        response.sendRedirect(request.getContextPath() + "/index.jsp");
    }

    /**
     * 四、文件下载：字节流 + Content-Disposition响应头
     * 这里为了演示直接生成内容，实际开发中是读取服务器上的文件再拷贝到输出流
     */
    private void download(HttpServletResponse response) throws IOException {
        String content = "这是一个由Servlet动态生成的文件\r\n演示 response 字节流的用法\r\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        //告诉浏览器这是一个未知类型的二进制流，不要尝试直接打开
        response.setContentType("application/octet-stream");
        //Content-Disposition: attachment 表示以附件形式下载，filename指定下载的文件名
        //中文文件名需要用ISO-8859-1转码，否则部分浏览器会乱码
        String fileName = new String("演示文件.txt".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setContentLength(bytes.length);

        //字节流，可以传输任意类型的数据（图片、视频、压缩包等）
        ServletOutputStream out = response.getOutputStream();
        out.write(bytes);
        out.flush();
    }

    /**
     * 五、响应状态码
     * 200成功  302重定向  304读缓存  400参数错误  403无权限  404资源不存在  405请求方式不支持  500服务器内部异常
     */
    private void status(HttpServletResponse response) throws IOException {
        //setStatus只设置状态码，响应体仍由自己控制
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("<h3>已手动把状态码设置为 404（打开浏览器F12的Network可以看到）</h3>");
        //也可以用 response.sendError(404, "资源不存在")，会直接跳到服务器的错误页面
    }

    /**
     * 六、自定义响应头：Refresh实现"N秒后自动跳转"，常用于操作成功后的提示页
     */
    private void refresh(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        //setHeader同名会覆盖，addHeader同名会追加
        response.setHeader("Refresh", "3;URL=" + request.getContextPath() + "/index.jsp");
        response.getWriter().write("<h3>操作成功，3秒后自动跳转到首页...</h3>");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}

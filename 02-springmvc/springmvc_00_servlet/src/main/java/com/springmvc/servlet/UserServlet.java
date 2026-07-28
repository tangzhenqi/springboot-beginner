package com.springmvc.servlet;

import com.springmvc.domain.User;
import com.springmvc.service.UserService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/**
 * 综合案例：用户登录 + 用户增删改查
 *
 * 一个Servlet处理该模块的所有请求，url-pattern使用目录匹配 /user/*
 * 具体执行哪个方法由父类BaseServlet根据路径反射决定：
 *   /user/toLogin  ->  toLogin()      跳转登录页
 *   /user/login    ->  login()        登录
 *   /user/logout   ->  logout()       退出
 *   /user/list     ->  list()         用户列表
 *   /user/toAdd    ->  toAdd()        跳转新增页
 *   /user/save     ->  save()         保存
 *   /user/toUpdate ->  toUpdate()     跳转修改页（回显数据）
 *   /user/update   ->  update()       修改
 *   /user/delete   ->  delete()       删除
 *
 * 这里的方法名，等价于springmvc中 @RequestMapping("/list") 标注的控制器方法
 */
@WebServlet("/user/*")
public class UserServlet extends BaseServlet {

    //业务层对象。Servlet是单例的，这里的service没有可变状态，因此线程安全
    //springmvc中这行会被 @Autowired 依赖注入替代
    private final UserService userService = new UserService();

    //保存登录用户的session属性名，与过滤器、jsp页面中使用的key保持一致
    public static final String SESSION_USER = "loginUser";

    /**
     * 跳转到登录页面
     * 登录页放在WEB-INF下，只能由服务器转发访问，浏览器无法直接输入地址访问，更安全
     */
    public String toLogin(HttpServletRequest request, HttpServletResponse response) {
        //从cookie中取出上次记住的用户名，实现"记住我"的回显
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("rememberedUsername".equals(cookie.getName())) {
                    //写入时做了url编码，取出时要解码还原
                    request.setAttribute("rememberedUsername", decode(cookie.getValue()));
                    break;
                }
            }
        }
        return "/WEB-INF/pages/login.jsp";
    }

    /**
     * 登录：校验账号密码，成功后把用户存入session
     *
     * session：服务器端的会话技术，一次会话（浏览器不关闭）内共享数据，容量不限，可存对象
     * cookie：客户端的会话技术，数据存在浏览器，只能存字符串，有大小限制（4KB左右）
     * session底层依赖名为JSESSIONID的cookie来识别是哪个浏览器
     */
    public String login(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        User user = userService.login(username, password);
        if (user == null) {
            //登录失败：把错误信息存入request域，转发回登录页（转发才能共享request域数据）
            request.setAttribute("errorMsg", "用户名或密码错误");
            request.setAttribute("username", username);
            return "/WEB-INF/pages/login.jsp";
        }

        //登录成功：用户信息存入session，作为后续判断是否登录的依据
        HttpSession session = request.getSession();
        session.setAttribute(SESSION_USER, user);

        //"记住我"：把用户名写入cookie，保存7天
        boolean remember = "true".equals(request.getParameter("remember"));
        //cookie的值不允许包含中文、空格等特殊字符，必须先做url编码，读取时再解码
        Cookie cookie = new Cookie("rememberedUsername", remember ? encode(username) : "");
        //setMaxAge单位为秒：正数=持久化到硬盘；0=立即删除；负数（默认）=仅存于内存，关闭浏览器即失效
        cookie.setMaxAge(remember ? 7 * 24 * 60 * 60 : 0);
        //设置cookie的携带范围，写成项目根路径，保证整个项目的请求都会携带它
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        response.addCookie(cookie);

        //登录成功后用重定向而不是转发：避免用户刷新页面时重复提交表单
        return "redirect:/user/list";
    }

    /**
     * 退出登录：销毁session
     */
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        //getSession(false)：存在就返回，不存在返回null，不会新建session
        HttpSession session = request.getSession(false);
        if (session != null) {
            //invalidate会销毁整个session及其中所有数据，比remove单个属性更彻底
            session.invalidate();
        }
        return "redirect:/user/toLogin";
    }

    /**
     * 用户列表：查询数据存入request域，转发到列表页展示
     * 等价于springmvc的：public String list(Model model){ model.addAttribute("userList", ...); return "list"; }
     */
    public String list(HttpServletRequest request, HttpServletResponse response) {
        List<User> userList = userService.findAll();
        request.setAttribute("userList", userList);
        return "/WEB-INF/pages/list.jsp";
    }

    /**
     * 跳转到新增页面
     */
    public String toAdd(HttpServletRequest request, HttpServletResponse response) {
        return "/WEB-INF/pages/add.jsp";
    }

    /**
     * 保存用户
     * 这里手动把请求参数一个个封装进实体，springmvc中只需要把User作为方法形参即可自动封装
     */
    public String save(HttpServletRequest request, HttpServletResponse response) {
        User user = buildUserFromRequest(request);

        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            request.setAttribute("errorMsg", "用户名不能为空");
            return "/WEB-INF/pages/add.jsp";
        }
        if (!userService.save(user)) {
            request.setAttribute("errorMsg", "用户名 " + user.getUsername() + " 已存在");
            request.setAttribute("user", user);
            return "/WEB-INF/pages/add.jsp";
        }
        return "redirect:/user/list";
    }

    /**
     * 跳转到修改页面，并把待修改的数据回显到表单
     */
    public String toUpdate(HttpServletRequest request, HttpServletResponse response) {
        Integer id = parseInt(request.getParameter("id"));
        User user = id == null ? null : userService.findById(id);
        if (user == null) {
            request.setAttribute("errorMsg", "要修改的用户不存在");
            return list(request, response);
        }
        request.setAttribute("user", user);
        return "/WEB-INF/pages/update.jsp";
    }

    /**
     * 修改用户
     */
    public String update(HttpServletRequest request, HttpServletResponse response) {
        User user = buildUserFromRequest(request);
        if (user.getId() == null || !userService.update(user)) {
            request.setAttribute("errorMsg", "修改失败，用户不存在");
            return list(request, response);
        }
        return "redirect:/user/list";
    }

    /**
     * 删除用户
     */
    public String delete(HttpServletRequest request, HttpServletResponse response) {
        Integer id = parseInt(request.getParameter("id"));
        if (id == null || !userService.deleteById(id)) {
            request.setAttribute("errorMsg", "删除失败，用户不存在");
            return list(request, response);
        }
        return "redirect:/user/list";
    }

    /**
     * ajax接口：返回json数据，方法内部直接响应，所以返回null不再做视图跳转
     * 对应springmvc的 @ResponseBody
     */
    public String listJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        StringBuilder json = new StringBuilder("{\"code\":20000,\"data\":[");
        List<User> userList = userService.findAll();
        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"id\":").append(user.getId())
                    .append(",\"username\":\"").append(user.getUsername())
                    .append("\",\"gender\":\"").append(user.getGender())
                    .append("\",\"age\":").append(user.getAge())
                    .append(",\"address\":\"").append(user.getAddress()).append("\"}");
        }
        json.append("]}");
        response.getWriter().write(json.toString());
        return null;
    }

    //把请求参数封装成User对象（springmvc中由HandlerMethodArgumentResolver自动完成）
    private User buildUserFromRequest(HttpServletRequest request) {
        User user = new User();
        user.setId(parseInt(request.getParameter("id")));
        user.setUsername(request.getParameter("username"));
        //新增时密码为空则给个默认值，简化演示
        String password = request.getParameter("password");
        user.setPassword(password == null || password.isEmpty() ? "123456" : password);
        user.setGender(request.getParameter("gender"));
        user.setAge(parseInt(request.getParameter("age")));
        user.setAddress(request.getParameter("address"));
        return user;
    }

    //cookie中存中文会抛IllegalArgumentException，统一用url编码处理
    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    //请求参数全部是字符串，转换类型时要考虑空值与格式异常
    private Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

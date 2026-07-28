package com.springmvc.service;

import com.springmvc.dao.UserDao;
import com.springmvc.domain.User;

import java.util.List;

/**
 * 业务层：负责业务逻辑与校验，表现层（Servlet）只负责接收参数、调用业务、响应结果
 * 三层架构 表现层Servlet -> 业务层Service -> 数据层Dao，在springmvc/ssm中依然是这个分层
 */
public class UserService {

    /**
     * 登录校验
     * @return 校验通过返回用户对象，失败返回null
     */
    public User login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        User user = UserDao.findByUsername(username.trim());
        //用户不存在或密码不匹配都视为登录失败，对外不区分提示，避免暴露用户是否存在
        if (user == null || !user.getPassword().equals(password)) {
            return null;
        }
        return user;
    }

    public List<User> findAll() {
        return UserDao.findAll();
    }

    public User findById(Integer id) {
        return UserDao.findById(id);
    }

    /**
     * 新增用户，同一用户名不允许重复
     * @return 保存成功返回true，用户名已存在返回false
     */
    public boolean save(User user) {
        if (UserDao.findByUsername(user.getUsername()) != null) {
            return false;
        }
        UserDao.save(user);
        return true;
    }

    public boolean update(User user) {
        return UserDao.update(user);
    }

    public boolean deleteById(Integer id) {
        return UserDao.deleteById(id);
    }
}

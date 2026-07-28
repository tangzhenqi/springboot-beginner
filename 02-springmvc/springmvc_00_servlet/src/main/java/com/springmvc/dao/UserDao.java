package com.springmvc.dao;

import com.springmvc.domain.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据访问层：用内存Map模拟数据库，避免为了演示Servlet再去搭建数据库环境
 * 后续 springmvc_08_ssm 模块中，这一层会换成 mybatis 的 Mapper 接口
 *
 * ConcurrentHashMap保证多线程（多个请求）下操作数据的安全性
 */
public class UserDao {

    //模拟数据库表，key为主键id
    private static final ConcurrentHashMap<Integer, User> DB = new ConcurrentHashMap<>();

    //模拟数据库主键自增
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    //静态代码块初始化两条测试数据，其中admin用于登录
    static {
        save(new User(null, "admin", "123456", "男", 20, "北京市海淀区"));
        save(new User(null, "tom", "123456", "男", 25, "上海市浦东新区"));
    }

    //新增：id由程序生成
    public static User save(User user) {
        user.setId(ID_GENERATOR.incrementAndGet());
        DB.put(user.getId(), user);
        return user;
    }

    //按id删除
    public static boolean deleteById(Integer id) {
        return DB.remove(id) != null;
    }

    //修改：id不变，覆盖原有数据
    public static boolean update(User user) {
        if (user.getId() == null || !DB.containsKey(user.getId())) {
            return false;
        }
        DB.put(user.getId(), user);
        return true;
    }

    //按id查询
    public static User findById(Integer id) {
        return DB.get(id);
    }

    //按用户名查询，登录时使用
    public static User findByUsername(String username) {
        for (User user : DB.values()) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }

    //查询全部，按id升序返回
    public static List<User> findAll() {
        List<User> users = new ArrayList<>(DB.values());
        users.sort((u1, u2) -> u1.getId() - u2.getId());
        return users;
    }
}

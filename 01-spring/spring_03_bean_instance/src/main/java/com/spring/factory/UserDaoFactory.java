package com.spring.factory;

import com.spring.dao.UserDao;
import com.spring.dao.impl.UserDaoImpl;
//实例工厂创建对象
public class UserDaoFactory {
    public UserDao getUserDao(){
        return new UserDaoImpl();
    }
}

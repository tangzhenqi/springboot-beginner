package com.spring.factory;

import com.spring.dao.OrderDao;
import com.spring.dao.impl.OrderDaoImpl;
//静态工厂创建对象
public class OrderDaoFactory {
    public static OrderDao getOrderDao(){
        System.out.println("factory setup....");
        return new OrderDaoImpl();
    }
}

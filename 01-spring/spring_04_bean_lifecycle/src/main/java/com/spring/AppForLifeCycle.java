package com.spring;

import com.spring.dao.BookDao;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class AppForLifeCycle {
    public static void main( String[] args ) {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");

        BookDao bookDao = (BookDao) ctx.getBean("bookDao");
        bookDao.save();
        //注册关闭钩子函数，在虚拟机退出之前回调此函数，关闭容器
        //ctx.registerShutdownHook();

        //关闭容器
        ctx.close();
    }
}

/**
 * bean生命周期
 * <p>
 * 初始化容器
 *   1. 创建对象（内存分配）
 *   2. 执行构造方法
 *   3. 执行属性注入（set操作）
 *   4. 执行bean初始化方法
 * <p>
 * 使用bean
 *   1. 执行业务操作
 * <p>
 * 关闭/销毁容器
 *   1. 执行bean销毁方法
 */

/**
 * bean销毁时机
 * <p>
 * 容器关闭前触发bean的销毁
 * <p>
 * 关闭容器方式：
 *   1. 手工关闭容器
 *      ConfigurableApplicationContext接口close()操作
 *   2. 注册关闭钩子，在虚拟机退出前先关闭容器再退出虚拟机
 *      ConfigurableApplicationContext接口registerShutdownHook()操作
 */
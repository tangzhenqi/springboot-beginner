package com.spring;

import com.spring.dao.BookDao;
import com.spring.service.BookService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AppForScope {
    public static void main(String[] args) {

        ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");

        // getBean(String name) 的返回类型是 Object。编译器只知道它是 Object，
        // 不知道名为 bookDao 的 Bean 实际类型是 BookDao，因此赋值前需要强制转换。
        // 也可以写成 ctx.getBean("bookDao", BookDao.class)，这样就不需要手动强转。
        BookDao bookDao1 = (BookDao) ctx.getBean("bookDao");
        BookDao bookDao2 = (BookDao) ctx.getBean("bookDao");
        System.out.println(bookDao1);
        System.out.println(bookDao2);

    }
}

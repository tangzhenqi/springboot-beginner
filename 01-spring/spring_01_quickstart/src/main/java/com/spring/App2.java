package com.spring;

import com.spring.dao.BookDao;
import com.spring.service.BookService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class App2 {
    public static void main(String[] args) {
        //3.获取IoC容器
        ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");
        //4.获取bean（根据bean配置id获取）
//        BookDao bookDao = (BookDao) ctx.getBean("bookDao");
//        bookDao.save();

        BookService bookService = (BookService) ctx.getBean("bookService");
        bookService.save();

    }
}

/**
 * 为什么 ClassPathXmlApplicationContext 会"默认"读取 resources 文件夹下的配置文件?
 *
 * 关键在于 "ClassPath"(类路径)这个词,而不是 resources 文件夹本身。
 * ClassPathXmlApplicationContext 是从类路径(classpath)下查找配置文件的,
 * 跟 resources 这个名字没有直接关系。
 *
 * resources 文件夹之所以生效,是 Maven 的约定在起作用,分两步:
 *   1. 编译打包时,Maven 会把 src/main/resources/ 下的所有文件,
 *      原样复制到 target/classes/ 目录。
 *   2. 程序运行时,target/classes/ 就是 classpath 的根目录
 *      (Java 编译后的 .class 文件也放这里)。
 *
 * 所以最终 applicationContext.xml 位于 classpath 根目录,
 * ClassPathXmlApplicationContext("applicationContext.xml") 自然就找到了。
 *
 *   src/main/resources/applicationContext.xml   (你写的位置)
 *             │  Maven 编译时复制
 *             ▼
 *   target/classes/applicationContext.xml       (classpath 根目录 = 运行时真正读取的位置)
 *             ▲
 *             │  ClassPathXmlApplicationContext 在这里找
 *
 * 一句话总结:不是"默认读 resources 文件夹",而是 Maven 把 resources 的内容
 * 放到了 classpath 根目录,而这个类恰好从 classpath 根目录找文件。
 *
 * 补充:
 *   - 若放在子目录 src/main/resources/spring/applicationContext.xml,
 *     则需写成 new ClassPathXmlApplicationContext("spring/applicationContext.xml")。
 *   - 也可显式写 "classpath:applicationContext.xml",效果一样。
 *   - 若想从文件系统绝对路径读取,则改用 FileSystemXmlApplicationContext。
 */
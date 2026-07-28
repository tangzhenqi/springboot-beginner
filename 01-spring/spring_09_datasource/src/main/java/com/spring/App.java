package com.spring;

import com.spring.dao.BookDao;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.sql.DataSource;

public class App {
    public static void main(String[] args) {
        ApplicationContext ctx = new ClassPathXmlApplicationContext("applicationContext.xml");

        // druid数据源
        DataSource dataSource_druid = (DataSource) ctx.getBean("dataSource_druid");
        // 打印的是 DruidDataSource 重写 toString() 后的运行状态统计信息(JSON格式):
        //   CreateTime   - 数据源创建时间
        //   ActiveCount  - 当前正在被使用的连接数
        // 此时全为0,是因为Druid懒加载:只是创建了数据源对象,还没真正getConnection()去连数据库。
        //
        // 【如果配置的数据库不存在会怎样?】
        //   - 只创建对象(即这行代码)通常不报错,因为还没真正去连库,输出照旧。
        //     例外:配置了 init-method="init" 或 initialSize>0 时,初始化就会尝试建连,此时会直接报错。
        //   - 真正 getConnection()/执行SQL 时才会暴露问题,常见异常:
        //       库名不存在        -> Unknown database 'xxx' (MySQL 1049)
        //       服务未启动/端口不通 -> Communications link failure / Connection refused
        //       用户名密码错误      -> Access denied for user 'xxx'
        //     这些底层 SQLException 常被 Spring 包装为 CannotGetJdbcConnectionException 抛出。
        System.out.println(dataSource_druid);

        // c3p0数据源
        DataSource dataSource_c3p0 = (DataSource) ctx.getBean("dataSource_c3p0");
        System.out.println(dataSource_c3p0);

        BookDao bookDao = (BookDao) ctx.getBean("bookDao");
        bookDao.save();

    }
}

package com.spring;

import com.alibaba.druid.pool.DruidDataSource;
import com.spring.config.SpringConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;

public class App {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
        DataSource dataSource = ctx.getBean(DataSource.class);
        System.out.println(dataSource);

        DruidDataSource ds = ctx.getBean(DruidDataSource.class);
        System.out.println(ds.getUsername());
        System.out.println(ds.getUrl());
        System.out.println(ds.getDriverClassName());

    }
}

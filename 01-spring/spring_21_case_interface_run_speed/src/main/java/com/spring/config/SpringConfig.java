package com.spring.config;

import org.springframework.context.annotation.*;

@Configuration
@ComponentScan("com.spring")
@PropertySource("classpath:jdbc.properties")
@Import({JdbcConfig.class,MybatisConfig.class})
@EnableAspectJAutoProxy
public class SpringConfig {
}

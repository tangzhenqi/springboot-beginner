package com.springmvc.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@ComponentScan({"com.springmvc.controller","com.springmvc.config"})
@EnableWebMvc
public class SpringMvcConfig {
}

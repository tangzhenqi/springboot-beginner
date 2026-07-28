package com.maven.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@ComponentScan({"com.maven.controller","com.maven.config"})
@EnableWebMvc
public class SpringMvcConfig {
}

package com.springmvc.service.impl;

import com.springmvc.domain.User;
import com.springmvc.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    public void save(User user) {
        System.out.println("user service ...");
    }
}

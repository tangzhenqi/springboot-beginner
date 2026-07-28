package com.mybatisplus.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mybatisplus.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserDao extends BaseMapper<User> {
}

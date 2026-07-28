package com.spring.dao.impl;

import com.spring.dao.BookDao;

public class BookDaoImpl implements BookDao {
    public BookDaoImpl() {
        System.out.println("constructor");
    }

    public void save() {
        System.out.println("book dao save ..." );
    }
}

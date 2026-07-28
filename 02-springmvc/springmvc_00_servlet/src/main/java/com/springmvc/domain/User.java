package com.springmvc.domain;

/**
 * 用户实体类
 * 属性名要与页面表单的name属性保持一致，方便统一封装（这也是springmvc能自动封装实体的前提）
 */
public class User {

    private Integer id;
    private String username;
    private String password;
    private String gender;
    private Integer age;
    private String address;

    public User() {
    }

    public User(Integer id, String username, String password, String gender, Integer age, String address) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.gender = gender;
        this.age = age;
        this.address = address;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', gender='" + gender
                + "', age=" + age + ", address='" + address + "'}";
    }
}

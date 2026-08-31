# 具体案例：分别使用 JDBC 和 MyBatis 查询账户

> 本文只做一件事：根据账户 id 查询 `tbl_account` 表，并把查询结果转换成一个 `Account` 对象。
>
> 我们会先使用原生 JDBC 完成，再使用当前项目的 MyBatis 写法完成。通过完全相同的需求，可以直观看出 MyBatis 替我们做了哪些工作。

## 1. 案例目标与准备

数据库中有一张账户表：

```text
+----+-------+--------+
| id | name  | money  |
+----+-------+--------+
|  1 | Tom   | 1000.0 |
|  2 | Jerry | 2000.0 |
+----+-------+--------+
```

现在需要编写 Java 代码：

```text
输入：账户 id = 1
处理：执行 SQL 查询 tbl_account
输出：Account{id=1, name='Tom', money=1000.0}
```

要执行的 SQL 是：

```sql
SELECT id, name, money
FROM tbl_account
WHERE id = ?;
```

其中 `?` 不是问号文本，而是待绑定的 SQL 参数。

**准备数据库**

先在 MySQL 中执行：

```sql
CREATE DATABASE IF NOT EXISTS spring_db
    DEFAULT CHARACTER SET utf8;

USE spring_db;

CREATE TABLE IF NOT EXISTS tbl_account (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(35),
    money DOUBLE
);

INSERT INTO tbl_account(id, name, money)
VALUES (1, 'Tom', 1000), (2, 'Jerry', 2000);
```

如果表中已经有相同 id，最后一条 SQL 会报主键重复。此时不需要再次插入，只要执行下面的 SQL 确认数据存在：

```sql
SELECT * FROM tbl_account;
```

**准备 Java 实体类**

数据库的一行账户数据，需要用 Java 对象接收。本项目已经有 `Account`：

```java
public class Account implements Serializable {

    private Integer id;
    private String name;
    private Double money;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getMoney() {
        return money;
    }

    public void setMoney(Double money) {
        this.money = money;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", money=" + money +
                '}';
    }
}
```

对应关系是：

| 数据库列 | Java 属性 | 赋值方法 |
| --- | --- | --- |
| `id` | `id` | `setId(...)` |
| `name` | `name` | `setName(...)` |
| `money` | `money` | `setMoney(...)` |

## 2. 原生 JDBC 实现

**完整代码**

下面是一段完整的 JDBC 查询代码。为了突出 JDBC 本身，这里直接使用 `DriverManager` 获取连接：

```java
import com.spring.domain.Account;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcAccountQueryDemo {

    private static final String URL =
            "jdbc:mysql://localhost:3306/spring_db?useSSL=false";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "填写自己的数据库密码";

    public static Account findById(Integer id) {
        String sql = "select id, name, money " +
                     "from tbl_account where id = ?";

        // try-with-resources 会在代码执行完毕或发生异常时自动关闭资源
        try (Connection connection =
                     DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            // 给 SQL 中的第 1 个问号赋值
            statement.setInt(1, id);

            // 执行查询，得到数据库结果集
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // 手工创建对象
                    Account account = new Account();

                    // 手工读取每一列，再放入对象属性
                    account.setId(resultSet.getInt("id"));
                    account.setName(resultSet.getString("name"));
                    account.setMoney(resultSet.getDouble("money"));

                    return account;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询账户失败，id=" + id, e);
        }

        // 没有查询到数据
        return null;
    }

    public static void main(String[] args) {
        Account account = findById(1);
        System.out.println(account);
    }
}
```

预期输出：

```text
Account{id=1, name='Tom', money=1000.0}
```

**JDBC 的 8 个步骤分别在哪里**

**步骤 1：获取数据库连接**

```java
Connection connection =
        DriverManager.getConnection(URL, USERNAME, PASSWORD);
```

`Connection` 代表 Java 程序和数据库之间的一条连接。

数据库地址、用户名或密码错误时，程序通常会在这里失败。

**步骤 2：编写 SQL**

```java
String sql = "select id, name, money " +
             "from tbl_account where id = ?";
```

这里使用 `?` 给查询 id 预留位置。

不要直接拼接参数：

```java
// 不推荐
String sql = "select * from tbl_account where id = " + id;
```

字符串拼接不利于参数类型处理，在参数来自用户输入时还可能产生 SQL 注入风险。

**步骤 3：创建 `PreparedStatement`**

```java
PreparedStatement statement = connection.prepareStatement(sql);
```

`PreparedStatement` 可以理解为“准备执行的 SQL 对象”。它知道 SQL 的结构，但此时 `?` 还没有具体值。

**步骤 4：给占位符赋值**

```java
statement.setInt(1, id);
```

这里的 `1` 表示 SQL 中第一个 `?`，JDBC 参数位置从 1 开始。

当 `id = 1` 时，可以把最终效果理解为：

```sql
SELECT id, name, money
FROM tbl_account
WHERE id = 1;
```

但 JDBC 实际使用的是参数绑定，并不是简单地把字符串拼在一起。

**步骤 5：执行 SQL**

```java
ResultSet resultSet = statement.executeQuery();
```

不同类型的 SQL 通常使用不同方法：

| SQL | JDBC 方法 | 返回内容 |
| --- | --- | --- |
| `SELECT` | `executeQuery()` | `ResultSet` |
| `INSERT/UPDATE/DELETE` | `executeUpdate()` | 受影响行数 |

**步骤 6：遍历 `ResultSet`**

```java
if (resultSet.next()) {
    // 读取当前这一行
}
```

刚拿到 `ResultSet` 时，游标还没有指向第一行。必须先调用 `next()`。

- 返回 `true`：存在下一行，可以读取；
- 返回 `false`：没有下一行。

本案例按主键 id 查询，最多只有一行，所以使用 `if`。查询全部账户时会改用 `while`：

```java
while (resultSet.next()) {
    // 每循环一次，处理一行数据
}
```

**步骤 7：手工转换为 Java 对象**

```java
Account account = new Account();
account.setId(resultSet.getInt("id"));
account.setName(resultSet.getString("name"));
account.setMoney(resultSet.getDouble("money"));
```

这是 JDBC 代码中最典型的重复劳动：

1. 手工创建对象；
2. 按列名读取结果；
3. 选择正确的 JDBC 类型读取方法；
4. 调用 setter 给属性赋值。

如果实体有 20 个属性，就可能需要写 20 行类似代码。

**步骤 8：关闭资源**

本例通过 try-with-resources 自动关闭：

```java
try (Connection connection = ...;
     PreparedStatement statement = ...) {

    try (ResultSet resultSet = ...) {
        // 使用资源
    }
}
```

关闭顺序大致为：

```text
ResultSet → PreparedStatement → Connection
```

如果没有 try-with-resources，就要在 `finally` 中手工关闭。忘记关闭数据库连接会导致连接逐渐耗尽。

## 3. MyBatis 实现

完成相同需求时，本项目的 MyBatis 核心代码只有一个接口方法：

```java
public interface AccountDao {

    @Select("select id, name, money " +
            "from tbl_account where id = #{id}")
    Account findById(Integer id);
}
```

调用代码是：

```java
Account account = accountDao.findById(1);
System.out.println(account);
```

在当前 Spring 整合案例中，实际调用入口是：

```java
ApplicationContext ctx =
        new AnnotationConfigApplicationContext(SpringConfig.class);

AccountService accountService = ctx.getBean(AccountService.class);

Account account = accountService.findById(1);
System.out.println(account);
```

预期输出仍然是：

```text
Account{id=1, name='Tom', money=1000.0}
```

## 4. MyBatis 替我们做了什么

我们只写了一行：

```java
Account account = accountDao.findById(1);
```

MyBatis 在背后大致完成了下面的工作。

**第 1 步：代理对象拦截方法调用**

`accountDao` 不是我们编写的实现类，而是 MyBatis 创建的动态代理对象。

代理对象发现当前调用的方法是：

```text
com.spring.dao.AccountDao.findById
```

**第 2 步：读取方法上的 SQL**

MyBatis 找到：

```java
@Select("select id, name, money " +
        "from tbl_account where id = #{id}")
```

于是知道应该执行一条查询 SQL。

**第 3 步：处理 `#{id}`**

传入的方法参数是：

```java
findById(1)
```

MyBatis 会把 `#{id}` 处理成 JDBC 的 `?`，再绑定参数：

```text
MyBatis 写法：where id = #{id}
JDBC 形式：  where id = ?
绑定参数：   第 1 个 ? = 1
```

所以 `#{}` 不是普通字符串替换，它的底层仍然使用 `PreparedStatement` 参数绑定。

**第 4 步：获取连接并执行查询**

MyBatis 根据配置的数据源获取连接，创建 `PreparedStatement`，绑定参数并调用 `executeQuery()`。

这些动作仍然存在，只是不再需要我们逐行编写。

**第 5 步：把结果映射成 `Account`**

MyBatis 看到方法返回类型是：

```java
Account findById(Integer id);
```

因此会创建 `Account` 对象，并根据同名关系完成映射：

```text
ResultSet 的 id 列    → Account.id
ResultSet 的 name 列  → Account.name
ResultSet 的 money 列 → Account.money
```

它所做的事情类似于 JDBC 示例中的：

```java
Account account = new Account();
account.setId(resultSet.getInt("id"));
account.setName(resultSet.getString("name"));
account.setMoney(resultSet.getDouble("money"));
```

**第 6 步：返回对象并释放本次调用的资源**

查询到数据时返回 `Account`，没有查询到时返回 `null`。

在 Spring 整合方式中，mybatis-spring 会通过 `SqlSessionTemplate` 协调 `SqlSession` 和连接的使用，不需要业务代码手工打开、关闭会话。

**JDBC 的 8 步，MyBatis 分别怎样处理**

| JDBC 工作 | JDBC 中由谁写 | MyBatis 案例中怎样完成 |
| --- | --- | --- |
| 1. 获取连接 | 开发者调用 `getConnection()` | 框架通过 `DataSource` 获取 |
| 2. 编写 SQL | 开发者 | 开发者仍然编写在 `@Select` 中 |
| 3. 创建 `PreparedStatement` | 开发者 | MyBatis 内部创建 |
| 4. 给占位符赋值 | 开发者调用 `setInt()` | MyBatis 根据 `#{id}` 自动绑定 |
| 5. 执行 SQL | 开发者调用 `executeQuery()` | MyBatis 根据 `@Select` 自动执行 |
| 6. 遍历 `ResultSet` | 开发者写 `next()` | MyBatis 内部处理 |
| 7. 转换 Java 对象 | 开发者写 getter/setter 映射 | MyBatis 根据列名和属性名映射 |
| 8. 关闭资源 | 开发者显式或 try-with-resources 关闭 | MyBatis-Spring 管理本次调用资源 |

MyBatis 没有替我们决定业务和 SQL。它主要接管的是重复、机械、容易写错的 JDBC 模板代码。

**两份代码的核心对比**

**JDBC 版本**

```java
String sql = "select id, name, money " +
             "from tbl_account where id = ?";

try (Connection connection = DriverManager.getConnection(...);
     PreparedStatement statement = connection.prepareStatement(sql)) {

    statement.setInt(1, id);

    try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
            Account account = new Account();
            account.setId(resultSet.getInt("id"));
            account.setName(resultSet.getString("name"));
            account.setMoney(resultSet.getDouble("money"));
            return account;
        }
    }
}
```

**MyBatis 版本**

```java
@Select("select id, name, money " +
        "from tbl_account where id = #{id}")
Account findById(Integer id);
```

两者最终都会使用 JDBC 与 MySQL 通信。MyBatis 是建立在 JDBC 之上的封装，不是绕过 JDBC 的另一种数据库协议。

**为什么 `#{id}` 比 `${id}` 更合适**

正确写法：

```java
@Select("select * from tbl_account where id = #{id}")
```

MyBatis 会得到：

```sql
SELECT * FROM tbl_account WHERE id = ?;
```

再把 id 安全地绑定给 `?`。

不推荐的写法：

```java
@Select("select * from tbl_account where id = ${id}")
```

`${}` 是文本拼接。对于来自用户的字符串参数，它可能改变原 SQL 的结构，产生 SQL 注入风险。

初学阶段记住：

```text
普通数据参数使用 #{}
不要把 #{} 随意改成 ${}
```

**扩展：查询多条数据**

需求变为“查询所有账户”时，JDBC 要创建集合并循环处理结果：

```java
List<Account> accounts = new ArrayList<>();

while (resultSet.next()) {
    Account account = new Account();
    account.setId(resultSet.getInt("id"));
    account.setName(resultSet.getString("name"));
    account.setMoney(resultSet.getDouble("money"));
    accounts.add(account);
}
```

MyBatis 只需要把方法返回值声明为集合：

```java
@Select("select id, name, money from tbl_account")
List<Account> findAll();
```

MyBatis 根据 `List<Account>` 可以知道：

- 需要处理多行结果；
- 每一行转换为一个 `Account`；
- 最终把所有对象放入 `List`。

## 5. 运行与排错

**检查连接配置**

修改：

```text
src/main/resources/jdbc.properties
```

确认数据库地址、用户名和密码属于你自己的 MySQL 环境。

**检查测试数据**

```sql
USE spring_db;
SELECT * FROM tbl_account WHERE id = 1;
```

确保能查询到一条记录。

**运行 `App2`**

`App2` 已经包含：

```java
Account ac = accountService.findById(1);
System.out.println(ac);
```

直接运行它即可验证 MyBatis + Spring 版本。

**运行 `App`**

`App` 是没有交给 Spring 管理的原生 MyBatis 版本。它查询的是 id=2：

```java
Account ac = accountDao.findById(2);
```

运行后预期输出：

```text
Account{id=2, name='Jerry', money=2000.0}
```

**常见问题**

**返回 `null`**

先在数据库直接执行：

```sql
SELECT * FROM tbl_account WHERE id = 1;
```

如果 SQL 本身查不到数据，MyBatis 返回 `null` 是正常结果。

**数据库连接失败**

重点检查：

- MySQL 是否启动；
- `spring_db` 是否已经创建；
- 端口是否为 3306；
- `jdbc.properties` 的用户名和密码是否正确。

**`Account` 的部分属性为 `null`**

检查 SQL 查询列名与 Java 属性名是否一致。例如数据库列叫 `account_name`，Java 属性叫 `name`，可以使用别名：

```sql
SELECT id, account_name AS name, money
FROM tbl_account
WHERE id = ?;
```

**MyBatis 找不到 Mapper 方法对应的 SQL**

注解写法中，检查 `findById` 上是否存在 `@Select`。如果异常中出现 `Invalid bound statement`，通常表示接口方法已经找到，但 SQL 映射没有找到。

## 6. 总结

这个“按 id 查询账户”的案例中，JDBC 与 MyBatis 的关系可以概括为：

```text
JDBC：
开发者亲自获取连接、创建语句、绑定参数、执行查询、遍历结果、转换对象、关闭资源

MyBatis：
开发者声明方法、SQL、参数和返回类型
框架在内部完成其余 JDBC 模板工作
```

MyBatis 最有价值的地方不是让 SQL 消失，而是让代码重点回到：

```text
我要执行什么 SQL？
传入什么参数？
希望得到什么 Java 对象？
```

本案例最终只需要记住这组对照：

```java
// JDBC 的参数占位符
where id = ?
statement.setInt(1, id);

// MyBatis 的参数占位符
where id = #{id}
```

`#{id}` 背后仍然是 JDBC 的 `PreparedStatement` 和参数绑定，只是 MyBatis 替我们完成了它。

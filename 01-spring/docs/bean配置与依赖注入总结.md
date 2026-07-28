# Spring bean 配置与依赖注入总结

## 一、bean 相关配置（`<bean>` 标签属性）

```xml
<bean
    id="bookDao"
    name="dao bookDaoImpl daoImpl"
    class="com.itheima.dao.impl.BookDaoImpl"
    scope="singleton"
    init-method="init"
    destroy-method="destory"
    autowire="byType"
    factory-method="getInstance"
    factory-bean="com.itheima.factory.BookDaoFactory"
    lazy-init="true"
/>
```

| 属性 | 说明 |
|------|------|
| `id` | bean 的 Id（容器中唯一） |
| `name` | bean 别名，可配置多个，用空格/逗号/分号分隔 |
| `class` | bean 类型：具体类、静态工厂类、FactoryBean 类 |
| `scope` | 控制 bean 的实例数量（`singleton` 单例 / `prototype` 多例） |
| `init-method` | 生命周期初始化方法 |
| `destroy-method` | 生命周期销毁方法 |
| `autowire` | 自动装配类型（`byType` 按类型 / `byName` 按名称） |
| `factory-method` | bean 工厂方法，应用于静态工厂或实例工厂 |
| `factory-bean` | 实例工厂 bean |
| `lazy-init` | 控制 bean 延迟加载（`true` 用到时才创建） |

---

## 二、依赖注入相关

```xml
<bean id="bookService" class="com.itheima.service.impl.BookServiceImpl">
    <!-- 构造器注入 -->
    <constructor-arg name="bookDao" ref="bookDao"/>                       <!-- 构造器注入引用类型 -->
    <constructor-arg name="userDao" ref="userDao"/>
    <constructor-arg name="msg" value="WARN"/>                            <!-- 构造器注入简单类型 -->
    <constructor-arg type="java.lang.String" index="3" value="WARN"/>    <!-- 类型匹配与索引匹配 -->

    <!-- setter 注入 -->
    <property name="bookDao" ref="bookDao"/>                              <!-- setter 注入引用类型 -->
    <property name="userDao" ref="userDao"/>
    <property name="msg" value="WARN"/>                                   <!-- setter 注入简单类型 -->

    <!-- setter 注入集合类型 -->
    <property name="names">
        <list>                                                           <!-- list 集合 -->
            <value>itcast</value>                                        <!-- 集合注入简单类型 -->
            <ref bean="dataSource"/>                                     <!-- 集合注入引用类型 -->
        </list>
    </property>
</bean>
```

### 两种注入方式

| 方式 | 标签 | 引用类型 | 简单类型 |
|------|------|----------|----------|
| **构造器注入** | `<constructor-arg>` | `ref="bean的id"` | `value="值"` |
| **setter 注入** | `<property>` | `ref="bean的id"` | `value="值"` |

### 构造器参数匹配方式
- `name`：按构造方法参数名匹配
- `type`：按参数类型匹配（`type="java.lang.String"`）
- `index`：按参数位置索引匹配（从 0 开始，`index="3"`）

### 集合类型注入
- `<list>`：list 集合
- 集合元素中：`<value>` 注入简单类型，`<ref bean="..."/>` 注入引用类型
- 其他集合标签同理：`<set>`、`<map>`、`<props>`、`<array>`

---

## 记忆要点
- **配 bean** 看第一部分：`id / class` 必备，其余按需。
- **注值** 看第二部分:`constructor-arg`（构造器）和 `property`（setter）二选一；
  引用类型用 `ref`，简单类型用 `value`，集合类型用嵌套标签。

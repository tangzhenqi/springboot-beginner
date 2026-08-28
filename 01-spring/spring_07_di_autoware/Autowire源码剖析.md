# `autowire="byType"` 源码剖析

> 本文是 [`README.md`](./README.md) 第三节的深入补充，回答一个问题：
>
> ```xml
> <bean id="bookService" class="com.spring.service.impl.BookServiceImpl" autowire="byType"/>
> ```
>
> 这一个属性，Spring 内部具体做了什么？
>
> 文中源码摘自 `spring-beans-5.2.10.RELEASE-sources.jar`，与本模块 `pom.xml` 中的版本一致。核心类是 `org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory`。

**一句话结论：`autowire="byType"` 干的事，是在创建 Bean 的过程中"替你动态生成 `<property>` 标签"，最后仍然走调用 setter 那条老路。**

## 目录

- [第 0 步：XML 解析成一个 int](#第-0-步xml-解析成一个-int)
- [第 1 步：先把对象 new 出来](#第-1-步先把对象-new-出来)
- [第 2 步：populateBean 分发](#第-2-步populatebean-分发)
- [第 3 步：挑出"待自动装配"的属性](#第-3-步挑出待自动装配的属性)
- [第 4 步：按类型去容器里找](#第-4-步按类型去容器里找)
- [第 5 步：统一赋值](#第-5-步统一赋值)
- [全流程串联](#全流程串联)
- [由源码反推的四个结论](#由源码反推的四个结论)

## 第 0 步：XML 解析成一个 int

`autowire="byType"` 被解析成 BeanDefinition 上的一个 int 字段：

```java
AUTOWIRE_NO          = 0    // 默认
AUTOWIRE_BY_NAME     = 1
AUTOWIRE_BY_TYPE     = 2    // ← 本案例
AUTOWIRE_CONSTRUCTOR = 3
```

此刻**什么都还没发生**，只是记了个标记，真正的动作全在创建 Bean 时。

## 第 1 步：先把对象 new 出来

`createBean` → `doCreateBean` → `createBeanInstance`，调用 `BookServiceImpl` 的**无参构造**造出一个空壳，此时 `bookDao` 字段是 `null`。

这里已经能看出它和构造器注入的本质区别：`byType` 模式下对象是**先造出来再填**的，中间存在一段"对象已存在但依赖为 null"的窗口期。

## 第 2 步：populateBean 分发

`AbstractAutowireCapableBeanFactory.java:1395`：

```java
int resolvedAutowireMode = mbd.getResolvedAutowireMode();
if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
    MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
    // Add property values based on autowire by name if applicable.
    if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
        autowireByName(beanName, mbd, bw, newPvs);
    }
    // Add property values based on autowire by type if applicable.
    if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
        autowireByType(beanName, mbd, bw, newPvs);   // ← 进这里
    }
    pvs = newPvs;
}
```

关键信号：传进去的是 `newPvs`——一个**属性值集合**。注意源码自带的注释写的是 "**Add property values** based on autowire by type"，即 `autowireByType` 的职责是往这个集合里**加东西**，而不是直接给对象赋值。

## 第 3 步：挑出"待自动装配"的属性

```java
protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
    Set<String> result = new TreeSet<>();
    PropertyValues pvs = mbd.getPropertyValues();
    PropertyDescriptor[] pds = bw.getPropertyDescriptors();
    for (PropertyDescriptor pd : pds) {
        if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
                !BeanUtils.isSimpleProperty(pd.getPropertyType())) {
            result.add(pd.getName());
        }
    }
    return StringUtils.toStringArray(result);
}
```

方法名直译就是"未被满足的、非简单类型的属性"。**这四个 `&&` 条件，正好就是 `applicationContext.xml` 注释里那几条特征的源头**：

| 源码条件 | 对应的现象 |
| --- | --- |
| `pd.getWriteMethod() != null` | **必须有 setter**，没有 setter 的属性压根进不了名单 |
| `!pvs.contains(pd.getName())` | **显式 `<property>` 优先**，已经配了的不再自动装配 |
| `!BeanUtils.isSimpleProperty(...)` | **简单类型被排除**（String / int / 包装类 / 枚举 / Date 等）|
| 取的是 `PropertyDescriptor` | 属性名由 **setter 推导**，不是字段名 |

对本案例的 `BookServiceImpl`，遍历结果就是 `["bookDao"]`——它有 `setBookDao(BookDao)`，`BookDao` 不是简单类型，XML 里也没写 `<property>`。

> ### ⚠️ "删掉 setter 就静默失败"的根因
>
> 没有 writeMethod → `bookDao` 进不了名单 → 这个属性从头到尾没有任何代码碰过它 → 自然不会有异常。
>
> 所以它**不是"注入失败了"，而是根本没尝试过**。这就是为什么容器启动一切正常，直到运行时才 NPE：
>
> ```
> book service save ...
> Exception in thread "main" java.lang.NullPointerException:
>     Cannot invoke "com.spring.dao.BookDao.save()" because "this.bookDao" is null
> ```

## 第 4 步：按类型去容器里找

```java
protected void autowireByType(
        String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

    TypeConverter converter = getCustomTypeConverter();
    if (converter == null) {
        converter = bw;
    }

    Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
    String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);   // ← 第 3 步
    for (String propertyName : propertyNames) {                          // 本案例只有 "bookDao"
        try {
            PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
            // Don't try autowiring by type for type Object: never makes sense,
            // even if it technically is a unsatisfied, non-simple property.
            if (Object.class != pd.getPropertyType()) {
                MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
                // Do not allow eager init for type matching in case of a prioritized post-processor.
                boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
                DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
                Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
                if (autowiredArgument != null) {
                    pvs.add(propertyName, autowiredArgument);            // ★ 题眼
                }
                for (String autowiredBeanName : autowiredBeanNames) {
                    registerDependentBean(autowiredBeanName, beanName);
                }
                autowiredBeanNames.clear();
            }
        }
        catch (BeansException ex) {
            throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
        }
    }
}
```

`resolveDependency` 内部按 `BookDao` 类型在容器中查找候选，三种结果：

| 候选数量 | 行为 |
| --- | --- |
| **1 个** | 返回它。本案例就是那个没写 id 的 `BookDaoImpl`，自动生成的名字是 `com.spring.dao.impl.BookDaoImpl#0`——byType 全程用不到名字，所以不写 id 完全没影响 |
| **0 个** | `AutowireByTypeDependencyDescriptor` 的 `required` 是 **false**，返回 `null`，`if (autowiredArgument != null)` 不成立，**静默跳过** |
| **多个** | 先用 `@Primary`、`@Priority` 尝试决胜，仍不唯一则抛 `NoUniqueBeanDefinitionException` |

`registerDependentBean` 是登记依赖关系，用于保证销毁时 `bookService` 先于 `bookDao` 被销毁。

★ 标记的那行 `pvs.add(propertyName, autowiredArgument)` 是整个机制的题眼：**它只是往属性值集合里塞了一条记录**，效果完全等同于你在 XML 里手写：

```xml
<property name="bookDao" ref="bookDao"/>
```

## 第 5 步：统一赋值

回到 `populateBean` 末尾，`applyPropertyValues(beanName, mbd, bw, pvs)` 遍历 `pvs`，通过 `BeanWrapper` **反射调用 `setBookDao(...)`**。

到这一步，自动装配来的属性和手写 `<property>` 的属性已经躺在同一个集合里，走的是完全相同的代码路径——**Spring 自己都不再区分它们了**。

## 全流程串联

```
autowire="byType"
   ↓ 解析
BeanDefinition.autowireMode = 2
   ↓ createBeanInstance
new BookServiceImpl()                    bookDao = null
   ↓ populateBean → autowireByType
unsatisfiedNonSimpleProperties() → ["bookDao"]
                                         ← 有 setter、非简单类型、未显式配置
   ↓ resolveDependency（按 BookDao 类型找）
找到唯一的 BookDaoImpl 实例
   ↓
pvs.add("bookDao", 实例)                  ★ 相当于动态生成 <property name="bookDao" ref="..."/>
   ↓ applyPropertyValues
bw 反射调用 setBookDao(实例)               ← 和手写 <property> 走同一条路
```

## 由源码反推的四个结论

1. **"自动装配是帮你自动填 `<property>` 标签"这句话，在源码层面是字面意义上准确的**，不是打比方——它真的就是往 `MutablePropertyValues` 里 `add` 一条。

2. **`autowire` 省掉的只是配置文件里的一行字，没有改变注入机制**。setter 该有还得有，注入时机、半初始化窗口期都和 setter 注入完全一致。

3. **它的错误暴露得太晚**。缺依赖时 `required = false` 导致静默跳过，而不是启动报错。这是它相比 `@Autowired` 最大的劣势——后者找不到依赖会在容器启动阶段就抛 `NoSuchBeanDefinitionException`。

4. **`@Autowired` 不是它的注解版翻译**。`@Autowired` 由 `AutowiredAnnotationBeanPostProcessor` 处理，走的是反射直接写字段（`Field.setAccessible(true)`），和本文这条 `PropertyValues` 链路完全无关。详见 [案例 13](../spring_13_annotation_di)。

## 延伸阅读

- 三种注入方式的横向对比：[`README.md` 第三节](./README.md)
- `@Autowired` / `@Qualifier` 的用法：[`spring_13_annotation_di`](../spring_13_annotation_di)
- 源码入口类：`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory`
  的 `populateBean()` / `autowireByType()` / `unsatisfiedNonSimpleProperties()` / `applyPropertyValues()`

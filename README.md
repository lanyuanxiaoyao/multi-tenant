# 多租户
> **多租户（Multi Tenancy/Tenant）** 是一种软件架构，其定义是：**在一台服务器上运行单个应用实例，它为多个租户提供服务**。

概念是抽象的，但是理解起来并不困难，简单来说就是分组，举个例子：我们管理学校学生的时候，可以按照不同的范围来进行分组，比如我们可以按照**学生个人**为单位进行分组，也可以按照**班级**为单位进行分组，然后班级下面有很多的学生，也可以按照**年级**为单位进行分组，以**学校**为单位……这样的每一个分组的单位，都可以是我们概念里面说的一个**租户**。  
但是这样不就和我们以前说的按照面向对象来分类是一样的吗？其实是差不多的，但是有着一些细节上的差别，首先多租户架构的概念是**针对数据存储**的，我们是一个**数据服务提供商**，假设我们给所有的学校提供服务，对于我们来说，分组是按照**学校**为单位的，而且学校与学校之间互相没有任何关系，也就说学校与学校之间是**隔离**的，对于不同学校的数据我们需要将它们隔离开来。这种数据的分组就是多租户架构要研究的问题。  
当然这只是概念上的区别，在实际使用上和我们传统的分组并无太大差异。

## 多租户的三种模式
多租户的架构分为以下三种：
1. 独立数据库
2. 共享数据库，独立Schema
3. 共享数据库，独立Schema，共享数据表

*注：在这个架构的概念里面，数据库指的是物理机器数据库，也就是我们的一部运行着数据库软件的计算机是一个物理数据库，Schema就是我们在数据库软件里面创建的“数据库”，实际上都是在同一个物理机器里面的，表就是表，一个简单的表*

独立数据库是一个租户独享一个数据库实例，它提供了**最强的分离度**，租户的数据彼此**物理不可见**，备份与恢复都很**灵活**；  
共享数据库、独立 Schema 将每个租户关联到同一个数据库的不同 Schema，租户间数据彼此逻辑不可见，上层应用程序的实现和独立数据库一样简单，但备份恢复稍显复杂；   
最后一种模式则是租户数据在数据表级别实现共享，它提供了最低的成本，但引入了额外的编程复杂性（程序的数据访问需要用 tenantId 来区分不同租户），备份与恢复也更复杂。  
这三种模式的特点可以用一张图来概括：

![三种部署模式的异同](https://www.ibm.com/developerworks/cn/java/j-lo-dataMultitenant/image001.jpg)

## 多租户模式选择
从上面的图我们可以看到，在成本上，独立数据库是最高的，毕竟我们一个租户就是一个物理机器，而且数据共享起来会麻烦，涉及到跨物理机器的通信，但这种模式的优势体现在单个租户数据量庞大，而且有非常大的扩展需求，那么单个机器内的调整就非常容易，而且不会影响到其他的租户，因为它的隔离程度是最高的。  
事实上，多租户模式的选择，主要是成本原因，对于多数场景而言，**共享度越高**，软硬件**资源利用效率更好**，**成本更低**。但同时也要解决好租户资源共享和隔离带来的安全与性能、扩展性等问题。毕竟，也有客户无法满意于将数据与其他租户放在共享资源中。

# Hibernate 多租户的使用
## Mybatis 多租户的使用
一开始我也是使用Mybatis进行多租户的设计，但是事实上Mybatis本身是没有对多租户提供支持的，也就说我们如果使用Mybatis设计多租户的架构的话，那么我们就需要手动实现sql语句的拦截然后在执行具体sql语句之前执行`use tenant_id`的操作，拦截sql语句的一个比较简单的方式是通过spring aop在service层的操作里进行切入实现拦截。  
实际上Hibernate也是这么干的，不过Hibernate在框架层面帮我们进行了sql语句拦截，不需要自己设计。  
虽然最后我选择了Hibernate进行多租户的设计，但是这里也记录下Mybatis的设计思路，实现起来就简单了。

## 项目结构
可能与Github（地址在文章末尾）实际编码有点出入，因为我可能会修改，但大体相同。

![][1]

### 主要目录及文件说明
- config  
一些设置文件，一开始我有一些设置文件的，但是后来去掉了，所以你可以忽略这个设置文件夹
	- ConstId  
用来暂存租户ID`TenantId`的一个文件，没有特别的作用，通常情况下，这个租户ID是登陆的时候存在session里面的，然后读取也是从session里面读取，这里显然是我为了方便就随便用一个文件来存了
- controller  
顾名思义……
	- HelloController  
- dao
这个也不解释了，dao层
	- StudentDao  
	- TenantInfoDao  
- entity  
实体类……
	- Student
	- TenantInfo  
这个是租户信息的实体类
- service  
Service层，只有一个StudentService是因为我嫌麻烦就不多创建一个TenantInfoService了
	- StudentService  
- tenant  
多租户相关的文件都在这里了，这个文件夹下的文件是**重点**！这些类的作用会在下面详细分析，这里就先不赘述了
	- MultiTenantConnectionProviderImpl
	- MultiTenantIdentifierResolver
	- TenantDataSourceProvider
- util  
一些辅助的工具，方便操作用的（各个web项目都可以通用，大家可以参考）
	- JsonUtil  
给Gson整了一个单例，不同到处new Gson()
	- Result  
统一的返回结果格式，满足REST架构
	- ResultCode  
统一的返回码，参照HTTP响应码
	- ResultGenerator  
构造返回Result结果的工具类
- CloudApplication.java

### 数据库结构和说明
首先在数据库里有三个Schema，其中`cloud_config`是存储租户信息的，`class_1`和`class_2`分别为我们预设的两个租户

![][2]

#### `cloud_config`的`tenant_info`表结构
![][3]

![][4]

- 字段说明
	- id  
主键
	- tenant_type  
数据库类型，用于识别连接不同的数据库的时候设置驱动的字段，在我这个小Demo中没有用上
	- url  
数据库连接URL
	- username  
数据库连接用户名
	- password  
数据库连接密码
	- tenant_id  
租户ID

#### `class_1`和`class_2`的`student`表结构
![][5]

![][6]

![][7]

## 代码
实际上需要设置的代码非常简单，但是网上的资料极其稀少，很多Demo项目都没有注释和说明，让我走了很多弯路，也是促使我写一个博客来说明这个多租户配置和使用的主要动力

### application.properties
怎么配置开启Hibernate的多租户功能，网上各种配置形式都有，有两种形式，一种是写配置类，一种就是在`application.properties`文件直接配置，显然直接配置要比配置类简单太多了
```
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/cloud_config
spring.datasource.username=lanyuanxiaoyao
spring.datasource.password=
spring.datasource.driver-class-name=org.postgresql.Driver

# Hibernate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.multiTenancy=SCHEMA
spring.jpa.properties.hibernate.tenant_identifier_resolver=cloud.tenant.MultiTenantIdentifierResolver
spring.jpa.properties.hibernate.multi_tenant_connection_provider=cloud.tenant.MultiTenantConnectionProviderImpl
```
这就是所需要的所有相关配置（如果你有别的配置就另外加上就是了），其中Database配置一定要有，就是一定要有一个默认的配置才能启动Spring boot，这个不能省……这是一个坑。  
- 关于Hibernate的几个配置项的说明
	- **show-sql**  
这个也无关多租户的设置，只是在控制台显示Hibernate执行的sql语句，方便调试
	- **hibernate.multiTenancy**  
选择多租户的模式，有四个参数：`NONE`，`DATABASE`，`SCHEMA`，`DISCRIMINATOR`，其中`NONE`就是默认没有模式，`DISCRIMINATOR`会在Hibernate5支持，所以我们根据模式选择是独立数据库还是不独立数据库就可以了，我这里选择SCHEMA，因为只有一台物理机器
	- **hibernate.tenant_identifier_resolver**  
租户ID解析器，简单来说就是这个设置指定的类负责每次执行sql语句的时候获取租户ID
	- **hibernate.multi_tenant_connection_provider**  
这个设置指定的类负责按照租户ID来提供相应的数据源

**配置后三个设置项的时候会没有自动提示，直接复制就行了，只要名字没错就ok，因为没有自动提示搞到我以为设置在这里是不行的**

### tenant包
这里的三个类是全部和多租户相关的类，这里我连同导包的信息也一并贴上了，希望大家不要导错包，同名的包有不少
#### TenantDataSourceProvider
```java
import cloud.entity.TenantInfo;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lanyuanxiaoyao
 */
public class TenantDataSourceProvider {

    // 使用一个map来存储我们租户和对应的数据源，租户和数据源的信息就是从我们的tenant_info表中读出来
    private static Map<String, DataSource> dataSourceMap = new HashMap<>();

    /**
     * 静态建立一个数据源，也就是我们的默认数据源，假如我们的访问信息里面没有指定tenantId，就使用默认数据源。
     * 在我这里默认数据源是cloud_config，实际上你可以指向你们的公共信息的库，或者拦截这个操作返回错误。
     */
    static {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url("jdbc:postgresql://localhost:5432/cloud_config");
        dataSourceBuilder.username("lanyuanxiaoyao");
        dataSourceBuilder.password("");
        dataSourceBuilder.driverClassName("org.postgresql.Driver");
        dataSourceMap.put("Default", dataSourceBuilder.build());
    }

    // 根据传进来的tenantId决定返回的数据源
    public static DataSource getTenantDataSource(String tenantId) {
        if (dataSourceMap.containsKey(tenantId)) {
            System.out.println("GetDataSource:" + tenantId);
            return dataSourceMap.get(tenantId);
        } else {
            System.out.println("GetDataSource:" + "Default");
            return dataSourceMap.get("Default");
        }
    }

    // 初始化的时候用于添加数据源的方法
    public static void addDataSource(TenantInfo tenantInfo) {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(tenantInfo.getUrl());
        dataSourceBuilder.username(tenantInfo.getUsername());
        dataSourceBuilder.password(tenantInfo.getPassword());
        dataSourceBuilder.driverClassName("org.postgresql.Driver");
        dataSourceMap.put(tenantInfo.getTenantId(), dataSourceBuilder.build());
    }

}
```
#### MultiTenantConnectionProviderImpl
```java
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import javax.sql.DataSource;

/**
 * 这个类是Hibernate框架拦截sql语句并在执行sql语句之前更换数据源提供的类
 * @author lanyuanxiaoyao
 * @version 1.0
 */
public class MultiTenantConnectionProviderImpl extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl {

    // 在没有提供tenantId的情况下返回默认数据源
    @Override
    protected DataSource selectAnyDataSource() {
        return TenantDataSourceProvider.getTenantDataSource("Default");
    }

    // 提供了tenantId的话就根据ID来返回数据源
    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        return TenantDataSourceProvider.getTenantDataSource(tenantIdentifier);
    }
}
```
#### MultiTenantIdentifierResolver
```java
package cloud.tenant;

import cloud.config.ConstId;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * 这个类是由Hibernate提供的用于识别tenantId的类，当每次执行sql语句被拦截就会调用这个类中的方法来获取tenantId
 * @author lanyuanxiaoyao
 * @version 1.0
 */
public class MultiTenantIdentifierResolver implements CurrentTenantIdentifierResolver{

    // 获取tenantId的逻辑在这个方法里面写
    @Override
    public String resolveCurrentTenantIdentifier() {
        if (!"".equals(ConstId.Id)){
            return ConstId.Id;
        }
        return "Default";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
```
## Hibernate 多租户实现原理
真如前面所说，Hibernate实现多租户的原理实际上就是在调用具体sql语句之前先调用一句`user database`来切换数据库，实现切换租户空间的功能，所以Hibernate提供了两个类来帮助我们在框架层面拦截我们要执行的sql语句，并注入切换数据库的操作，操作流程见下图：

![][8]

## 测试
因为Demo实在是简单，所以有一些细节没有处理，包括从session中取tenantId也没有写进去，所以测试流程就先写下来，免得无法测试实际项目效果

### 初始化`datasourceMap`
访问`http://localhost:8080/`  

![][9]

可以看到我们从`cloud_config`schema的`tenant_info`获取到所有租户的信息

### 登陆
访问`http://localhost:8080/login?t=class_1`

![][10]

看到返回成功，即后台已经设置好了`tenantId`为`class_1`

### 查询
访问`http://localhost:8080/select?t=class_1`

![][11]

可以看到我们在不改动实际数据库连接的情况下获取到了`class_1`schema的`student`的数据，到这里我们已经可以访问租户的信息了

### 切换租户
我们重复登录和查询的步骤  
访问`http://localhost:8080/login?t=class_2`和`http://localhost:8080/select?t=class_2`

![][12]

我们成功获取到了另一个租户的信息，到这里我们多租户的实现已经成功了。

# 总结
多租户架构这个看起来好像还挺新的，也许是应用范围不够广泛，网上的资料相当少，也让我走了很多的弯路，在此总结这篇文档，希望能够帮助到大家。  
Demo的GIthub地址：[https://github.com/lanyuanxiaoyao/multi-tenant](https://github.com/lanyuanxiaoyao/multi-tenant)


  [1]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_12h27m50s_001_.png "目录结构"
  [2]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h40m46s_002_.png "数据库结构"
  [3]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h41m52s_005_.png "cloud_config"
  [4]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h46m34s_006_.png "cloud_config表结构"
  [5]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h41m27s_003_.png "class_1"
  [6]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h41m39s_004_.png "class_2"
  [7]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_13h58m44s_007_.png "student表结构"
  [8]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/multi-tenant.png "multi-tenant"
  [9]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_18h40m27s_001_.png "初始化datasourceMap"
  [10]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_18h43m21s_002_.png "登陆"
  [11]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_18h45m28s_003_.png "查询"
  [12]: https://www.github.com/lanyuanxiaoyao/GitGallery/raw/master/2017/7/14/Spring%20Boot%EF%BC%88%E4%B8%89%EF%BC%89%20Spring%20boot%20+%20Hibernate%20%E5%A4%9A%E7%A7%9F%E6%88%B7%E7%9A%84%E4%BD%BF%E7%94%A8/Ashampoo_Snap_2017%E5%B9%B47%E6%9C%8814%E6%97%A5_18h51m40s_004_.png "查询"

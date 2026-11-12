package com.mujinyu.shopping_manager_api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

// shopping_common 依赖了 mybatis-plus，会传递引入 spring-boot-starter-jdbc，
// 而本服务只做远程调用、没有自己的数据库，故排除数据源自动配置，避免启动时报
// "Failed to configure a DataSource: 'url' attribute is not specified"
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient // 向注册中心注册服务
@RefreshScope // 动态刷新配置
@Slf4j
public class ShoppingManagerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingManagerApiApplication.class, args);
        log.info("************* 管理员管理API启动成功 *************");
    }

}

package com.mujinyu.shopping_user_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@SpringBootApplication
@MapperScan("com.mujinyu.shopping_user_service.mapper")
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@Slf4j
public class ShoppingUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingUserServiceApplication.class, args);
        log.info("************* 用户微服务启动成功 *************");
    }

}

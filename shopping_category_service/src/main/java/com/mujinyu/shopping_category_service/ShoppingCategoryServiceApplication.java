package com.mujinyu.shopping_category_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@MapperScan ("com.mujinyu.shopping_category_service.mapper")
@SpringBootApplication
@Slf4j
public class ShoppingCategoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCategoryServiceApplication.class, args);
        log.info("**************** 广告微服务启动成功 ****************");
    }

}

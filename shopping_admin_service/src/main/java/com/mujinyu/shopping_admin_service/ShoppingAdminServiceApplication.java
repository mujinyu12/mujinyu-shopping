package com.mujinyu.shopping_admin_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@SpringBootApplication
@EnableDiscoveryClient //开启服务发现
@EnableDubbo //开启dubbo注解支持
@RefreshScope //开启配置文件动态刷新
@MapperScan ("com.mujinyu.shopping_admin_service.mapper")
@Slf4j
public class ShoppingAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingAdminServiceApplication.class, args);
        log.info("**************** 管理员管理微服务启动成功 ****************");
    }

}

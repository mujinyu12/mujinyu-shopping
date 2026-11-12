package com.mujinyu.shopping_pay_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 支付服务启动类
 * <p>
 * 只做微信支付下单、回调验签、写支付记录，不对外提供 HTTP 接口（HTTP 由 BFF 暴露）。
 */
@Slf4j
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@MapperScan("com.mujinyu.shopping_pay_service.mapper")
@SpringBootApplication
public class ShoppingPayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingPayServiceApplication.class, args);
        log.info("********** 支付微服务启动成功 **********");
    }
}
package com.mujinyu.shopping_seckill_customer_api;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@Slf4j
public class ShoppingSeckillCustomerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingSeckillCustomerApiApplication.class, args);
        log.info("**************** 秒杀服务客户端启动成功 ****************");
    }

}

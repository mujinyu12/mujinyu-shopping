package com.mujinyu.shopping_category_customer_api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@EnableDiscoveryClient
@RefreshScope
@Slf4j
@SpringBootApplication (exclude = {DataSourceAutoConfiguration.class})
public class ShoppingCategoryCustomerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCategoryCustomerApiApplication.class, args);
        log.info("**************** 广告用户API微服务启动成功 ****************");
    }

}

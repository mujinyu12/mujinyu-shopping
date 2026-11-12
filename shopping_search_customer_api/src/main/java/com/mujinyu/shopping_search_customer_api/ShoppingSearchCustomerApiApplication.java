package com.mujinyu.shopping_search_customer_api;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@EnableDiscoveryClient
@EnableDubbo
@RefreshScope
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class ShoppingSearchCustomerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingSearchCustomerApiApplication.class, args);
		log.info("**************** 用户搜索API微服务启动成功 ****************");
	}
}
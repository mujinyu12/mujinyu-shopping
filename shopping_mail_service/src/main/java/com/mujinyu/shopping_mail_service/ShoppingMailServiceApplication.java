package com.mujinyu.shopping_mail_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
@EnableDubbo
@RefreshScope
@Slf4j
public class ShoppingMailServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingMailServiceApplication.class, args);
		log.info("*************** 邮箱服务启动成功 *******************");
	}

}

package com.mujinyu.shopping_sms_service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 短信服务启动类
 * <p>
 * shopping_common 里引入了 mybatis-plus，它会尝试配置数据源；
 * 这个服务不连数据库，必须排除 DataSourceAutoConfiguration，否则启动直接报
 * "Failed to configure a DataSource"。
 */
@Slf4j
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ShoppingSmsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingSmsServiceApplication.class, args);
        log.info("========== 短信微服务启动成功 ==========");
    }
}

package com.mujinyu.shopping_custcare_customer_api;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * AI 客服对外接口层（BFF）。
 * 只做 HTTP -> Dubbo 的转发，不直连数据库、不直连大模型：
 * 大模型调用全部发生在 shopping_custcare_service(9013) 里。
 * exclude DataSourceAutoConfiguration：shopping_common 会带进 mybatis-plus，
 * 本模块没有数据源，不排除会报 Failed to configure a DataSource。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@Slf4j
public class ShoppingCustcareCustomerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCustcareCustomerApiApplication.class, args);
        log.info("**************** AI 客服接口层启动成功 ****************");
    }

}
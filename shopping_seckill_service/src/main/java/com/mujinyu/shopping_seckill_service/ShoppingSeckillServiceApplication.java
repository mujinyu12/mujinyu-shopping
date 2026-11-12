package com.mujinyu.shopping_seckill_service;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableDubbo
@RefreshScope
@Slf4j
@MapperScan("com.mujinyu.shopping_seckill_service.mapper")
@EnableScheduling
public class ShoppingSeckillServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingSeckillServiceApplication.class, args);
		log.info("******************* 商品秒杀微服务启动成功 ***********************");
	}

	// 布隆过滤器的容器
	@Bean
	public BitMapBloomFilter bitMapBloomFilter() {
		// 注意：该参数不是“元素个数”，而是位图大小的基数：
		// 每个过滤器的位图位数 = (m / 5) * 1024 * 1024 * 8，且内部会创建 5 个过滤器
		// 即 m=5 约占用 5MB 堆内存，可支撑百万级元素；m=10000 则需要约 10GB，会直接内存溢出
		return new BitMapBloomFilter(5);
	}
}
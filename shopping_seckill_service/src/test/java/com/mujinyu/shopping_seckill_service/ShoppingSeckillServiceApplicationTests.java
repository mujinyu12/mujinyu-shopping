package com.mujinyu.shopping_seckill_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class ShoppingSeckillServiceApplicationTests {

	@Autowired
	private RedisTemplate template;
    @Qualifier ("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;

	@Test
	void contextLoads() {
		redisTemplate.boundHashOps("seckillGoods").delete(149187842867997L);
	}

}

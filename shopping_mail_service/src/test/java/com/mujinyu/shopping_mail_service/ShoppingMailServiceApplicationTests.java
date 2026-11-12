package com.mujinyu.shopping_mail_service;

import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ShoppingMailServiceApplicationTests {

	@Autowired
	private MailService mailService;

	@Test
	void contextLoads() {
		BaseResult baseResult = mailService.sendMail("mujinyu1@qq.com", "这是一封离别信，写下我该离开的原因", "离别信");
		System.out.println(baseResult);
	}

}

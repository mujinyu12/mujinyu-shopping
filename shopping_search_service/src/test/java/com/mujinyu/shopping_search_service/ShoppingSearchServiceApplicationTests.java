package com.mujinyu.shopping_search_service;

import com.mujinyu.shopping_common.pojo.GoodsDesc;
import com.mujinyu.shopping_common.service.GoodsService;
import com.mujinyu.shopping_search_service.service.SearchServiceImpl;
import org.apache.dubbo.config.annotation.DubboReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(properties = {
		// 测试会启动完整的 Dubbo 服务提供者，而 dubbo.protocol.port=-1（或 0）都会落到默认的 20880，
		// 与本机已启动的 shopping_goods_service 冲突，这里显式换一个不常用的端口
		"dubbo.protocol.port=20899",
		// 测试进程不需要把提供者写进 Nacos 注册中心
		"dubbo.provider.register=false",
		// QoS 默认监听 22222；Nacos 配置拉取失败时配置里的 qos-enable:false 会缺失，
		// 就会和本机已启动的服务抢端口，这里直接关掉
		"dubbo.application.qos-enable=false"
})
class ShoppingSearchServiceApplicationTests {
	@Autowired
	private SearchServiceImpl searchService;

	// check=false：分词测试不依赖 goods_service；同步用例已禁用，需要提供者时再启用
	@DubboReference(check = false)
	private GoodsService goodsService;

	@Test
	void contextLoads() {
		List<String> analyze = searchService.analyze("我爱百战程序员", "ik_pinyin");
		System.out.println(analyze);
	}

	// 该用例需要 shopping_goods_service 的 GoodsService 提供者注册到 Nacos，
	// 本地/CI 默认不启动该服务，禁用后 mvn test 不会因 No provider available 失败，联调时再手动启用
	@Test
	@Disabled("依赖 shopping_goods_service 提供者，需手动联调")
	void testSyncGoodsToEs() {
		List<GoodsDesc> all = goodsService.findAll();
		for (GoodsDesc goodsDesc : all) {
			// 如果商品是上架状态
			if (goodsDesc.getIsMarketable()) {
				searchService.syncGoodsToES(goodsDesc);
			}
		}
	}
}

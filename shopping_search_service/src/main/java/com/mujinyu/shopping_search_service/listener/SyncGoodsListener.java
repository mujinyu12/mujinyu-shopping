package com.mujinyu.shopping_search_service.listener;

import com.mujinyu.shopping_common.pojo.GoodsDesc;
import com.mujinyu.shopping_common.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

// 监听同步商品消息
@RocketMQMessageListener(topic = "sync_goods_queue", consumerGroup = "sync_goods_group")
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncGoodsListener implements RocketMQListener<GoodsDesc> {

    private final SearchService searchService;

    @Override
    public void onMessage(GoodsDesc goodsDesc) {
        log.info("同步商品数据到ES");
        searchService.syncGoodsToES(goodsDesc);
    }
}

package com.mujinyu.shopping_search_service.listener;

import com.mujinyu.shopping_common.pojo.GoodsDesc;
import com.mujinyu.shopping_common.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

// 监听删除商品消息
@RocketMQMessageListener(topic = "del_goods_queue", consumerGroup = "del_goods_group")
@Service
@RequiredArgsConstructor
@Slf4j
public class DelGoodsListener implements RocketMQListener<Long> {

    private final SearchService searchService;

    @Override
    public void onMessage(Long aLong) {
        log.info("删除ES中的商品数据{}", aLong);
        searchService.delete(aLong);
    }
}

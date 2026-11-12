package com.mujinyu.shopping_cart_service.listener;

import com.mujinyu.shopping_cart_service.service.CartServiceImpl;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * 商品下架消息。
 * 不再遍历所有用户的购物车去删除该商品，只让缓存失效；
 * 读购物车时按 isMarketable 过滤，商品重新上架后会自动回到购物车。
 */
@Service
@RocketMQMessageListener(topic = "del_cart_queue", consumerGroup = "del_cart_group")
public class DelCartListener implements RocketMQListener<Long> {
    @Override
    public void onMessage(Long goodId) {
        CartServiceImpl.evictGoodsCache(goodId);
    }
}
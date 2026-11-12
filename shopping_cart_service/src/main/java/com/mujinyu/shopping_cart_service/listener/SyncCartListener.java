package com.mujinyu.shopping_cart_service.listener;

import com.mujinyu.shopping_cart_service.service.CartServiceImpl;
import com.mujinyu.shopping_common.pojo.CartGoods;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * 商品变更消息（改价/改图）。
 * 这里不再刷新所有用户的购物车，只让该商品的本地快照缓存失效，
 * 下一次读购物车时会自动拉取最新数据。
 */
@Service
@RocketMQMessageListener (topic = "sync_cart_queue", consumerGroup = "sync_cart_group")
public class SyncCartListener implements RocketMQListener<CartGoods> {
    @Override
    public void onMessage(CartGoods cartGoods) {
        if (cartGoods != null && cartGoods.getGoodId() != null) {
            CartServiceImpl.evictGoodsCache(cartGoods.getGoodId());
        }
    }
}
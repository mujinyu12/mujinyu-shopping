package com.mujinyu.shopping_seckill_service.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import com.mujinyu.shopping_seckill_service.mapper.SeckillGoodsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mujinyu.shopping_seckill_service.constant.SeckillRedisConstants.STOCK_KEY_PREFIX;

/**
 * redis 监听类继承KeyExpirationEventMessageListener类，用于监听Redis键的过期事件
 */
@Slf4j
@Component
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    /**
     * 订单过期后，交易关闭，回退商品库存
     *
     * @param message 过期的键
     * @param pattern 过期的模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 获取失效key，即订单id
        String expiredKey = message.toString();
        // 副本自身过期时，Redis已经把value删掉了，拿不到订单详情，无法（也不需要）再回补：
        // 真正干活的是“订单主键”过期事件，那时副本还活着1分钟，可以读到订单详情
        if (expiredKey.endsWith("_copy")) return;
        String orderId = expiredKey;
        // 副本不存在说明这个key不是秒杀订单（可能是库存key、"seckill:rollback:"幂等标记等），直接忽略
        String copyKey = orderId + "_copy";
        Orders orders = (Orders) redisTemplate.opsForValue().get(copyKey);
        if (orders == null) return;

        // 1、幂等闸门：同一订单只回补一次（SETNX，保留24小时足够覆盖重投窗口）
        // 使用Redis的SETNX命令设置一个标志，防止重复回补库存
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent("seckill:rollback:" + orderId, "1", 24, TimeUnit.HOURS);
        // 如果标志已存在，说明该订单已经处理过，直接返回
        if (!Boolean.TRUE.equals(first)) {
            log.info("订单【{}】库存已回补过，忽略重复事件", orderId);
            return;
        }
        // 如果订单状态为2（已支付），则不回补库存
        if (Integer.valueOf(2).equals(orders.getStatus())) { // 已支付订单不回补
            redisTemplate.delete(copyKey);
            return;
        }
        // 获取商品id和购买数量
        List<CartGoods> cartGoods = orders.getCartGoods();
        if (cartGoods == null || cartGoods.isEmpty()) {
            log.warn("订单副本【{}】没有商品信息，无法回补库存", expiredKey);
            return;
        }
        Long goodsId = cartGoods.get(0).getGoodId();
        Integer num = cartGoods.get(0).getNum();
        if (goodsId == null || num == null || num <= 0) {
            log.warn("订单副本【{}】商品信息异常，goodsId={}，num={}", expiredKey, goodsId, num);
            return;
        }

        // 增加数据库中的商品库存：addStock 的SQL带 stockCount + #{num} <= num 上限保护，
        // 即使幂等闸门失效（如Redis被清空），库存也不会被加到超过总库存。
        // 注意：bz_seckill_goods.goodsId 允许重复（同一商品多场次），而 addStock 按主键 id 更新，
        // 所以这里先按 goodsId 取最近一场（id 最大）的场次 id，不能把 goodsId 直接当 id 用
        Long seckillId = seckillGoodsMapper.selectList(
                        new QueryWrapper<SeckillGoods>().eq("goodsId", goodsId).orderByDesc("id").last("LIMIT 1"))
                .stream().findFirst().map(SeckillGoods::getId).orElse(null);
        if (seckillId == null) {
            log.warn("订单副本【{}】对应的秒杀场次不存在，goodsId={}，跳过数据库回补", expiredKey, goodsId);
        } else {
            seckillGoodsMapper.addStock(seckillId, num);
        }
        // 增加Redis中的商品库存
        stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + goodsId, num);
        // 回补完成后清理副本，避免残留；清理失败不影响库存正确性
        try {
            redisTemplate.delete(copyKey);
        } catch (Exception e) {
            log.warn("订单副本【{}】清理失败，等待TTL自然过期", copyKey, e);
        }
    }
}

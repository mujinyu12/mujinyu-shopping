package com.mujinyu.shopping_seckill_service.constant;

/**
 * 秒杀模块Redis键常量
 */
public final class SeckillRedisConstants {

    /**
     * 秒杀商品详情缓存key（Hash），field为商品id
     */
    public static final String SECKILL_GOODS_KEY = "seckillGoods";

    /**
     * 秒杀商品库存缓存key前缀，完整key格式：seckill:stock:{goodsId}
     * 该key的值为纯字符串整数，由Lua脚本原子扣减、订单过期时原子回补
     */
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private SeckillRedisConstants() {
    }
}
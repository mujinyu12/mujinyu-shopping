package com.mujinyu.shopping_common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.SeckillGoods;

public interface SeckillService {
    /**
     * 网站用户从Redis查询秒杀商品列表
     *
     * @param page 页码
     * @param size 每页条数
     * @return 秒杀商品列表
     */
    Page<SeckillGoods> findPageByRedis(int page, int size);

    /**
     * 查询秒杀商品详情
     *
     * @param goodsId 秒杀商品ID
     * @return 秒杀商品详情
     */
    SeckillGoods findSeckillGoodsByRedis(Long goodsId);

    /**
     * 生成秒杀订单
     *
     * @param orders 订单数据
     * @return 生成的订单
     */
    Orders createOrder(Orders orders);

    /**
     * 查询秒杀订单详情
     *
     * @param id 订单id
     * @return 订单详情
     */
    Orders findOrder(String id);

    /**
     * 支付秒杀订单
     *
     * @param orderId 订单id
     * @return 订单对象
     */
    Orders pay(String orderId, Long userId);

    /**
     * 确认数据库缓存成功后才能清理Redis
     * @param orderId
     */
    void confirmPaid(String orderId);

    /**
     * 将一个秒杀商品保存到Redis中
     *
     * @param seckillGoods 秒杀商品对象
     */
    void addRedisSeckillGoods(SeckillGoods seckillGoods);

    /**
     * 从数据库查询秒杀商品详情
     *
     * @param goodsId 商品ID
     * @return 秒杀商品详情
     */
    SeckillGoods findSeckillGoodsByMySql(Long goodsId);

    /**
     * 退款成功后回补秒杀库存（DB + Redis + 商品场次缓存）
     * @param seckillId 下单时记录的秒杀场次ID，可为null，为null时按goodsId取最近一场
     * @param goodsId 商品ID
     * @param num 购买数量
     * @return 是否回补成功
     */
    boolean restoreStockForRefund(Long seckillId, Long goodsId, Integer num);
}

package com.mujinyu.shopping_seckill_customer_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_common.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品控制器
 */
@RestController
@Slf4j
@RequestMapping ("/user/seckillGoods")
public class SeckillGoodsController {
    @DubboReference
    private SeckillService seckillService;
    @DubboReference
    private OrdersService ordersService;
    /**
     * 用户分页查询秒杀商品
     *
     * @param page 页码
     * @param size 每页条数
     * @return 秒杀商品列表
     */
    @GetMapping ("/findPage")
    public BaseResult<Page<SeckillGoods>> findPage(int page, int size) {
        return BaseResult.ok(seckillService.findPageByRedis(page, size));
    }

    /**
     * 用户根据id查询秒杀商品详情
     *
     * @param id 商品id
     * @return 秒杀商品详情
     */
    @GetMapping ("/findById")
    public BaseResult<SeckillGoods> findById(@RequestParam ("id") Long id) {
        // 从Redis中查询商品详情
        SeckillGoods seckillGoods = seckillService.findSeckillGoodsByRedis(id);
        if (seckillGoods != null) {
            return BaseResult.ok(seckillService.findSeckillGoodsByRedis(id));
        } else {
            // 如果redis中查找不到，再从数据库查询秒杀商品详情
            SeckillGoods seckillGoodsByMySql = seckillService.findSeckillGoodsByMySql(id);
            return BaseResult.ok(seckillGoodsByMySql);
        }
    }

    /**
     * 创建秒杀订单
     * @param orders 订单对象
     * @param userId 用户id
     * @return 创建的订单对象
     */
    @PostMapping ("/add")
    public BaseResult<Orders> add(@RequestBody Orders orders, @LoginUser Long userId) {
        orders.setUserId(userId);
        Orders order = seckillService.createOrder(orders);
        return BaseResult.ok(order);
    }

    /**
     * 根据id查询秒杀订单（只能查自己的订单）
     * @param id 订单id
     * @param userId 用户id
     * @return 查询结果
     */
    @GetMapping ("/findOrder")
    public BaseResult<Orders> findOrder(@RequestParam ("id") String id, @LoginUser Long userId) {
        Orders order = seckillService.findOrder(id);
        if (order == null) {
            throw new BusException(CodeEnum.ORDER_EXPIRED_ERROR); // 订单不存在或已过期
        }
        // 横向越权校验：不能查看别人的秒杀订单
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusException(CodeEnum.NO_PERMISSION);
        }
        return BaseResult.ok(order);
    }

    /**
     * 先落库，再删缓存；失败则保留副本，由过期监听回补库存
     * @param id 订单id
     * @param userId 用户id
     * @return 执行结果
     */
    @PostMapping("/pay")
    public BaseResult<Orders> pay(@RequestParam String id, @LoginUser Long userId) {
        // 支付秒杀订单
        Orders orders = seckillService.pay(id, userId);

        try {
            // 将订单数据存入数据库
            ordersService.add(orders);
        } catch (DuplicateKeyException e) {
            log.info("秒杀订单【{}】已落库，按幂等处理", id);
        }
        catch (Exception e) {
            // 落库失败：缓存与副本都还没删，过期监听仍能回补库存，用户也可以重试
            log.error("秒杀订单【{}】落库失败，保留Redis副本等待补偿", id, e);
            throw e;
        }
        try {
            seckillService.confirmPaid(id);
        } catch (Exception e) {
            log.warn("秒杀订单【{}】已落库，但缓存清理失败，等待TTL自然过期", id, e);
        }
        return BaseResult.ok(orders);
    }
}
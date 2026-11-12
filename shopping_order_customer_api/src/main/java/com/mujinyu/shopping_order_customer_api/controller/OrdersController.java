package com.mujinyu.shopping_order_customer_api.controller;

import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Goods;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.result.PageResult;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.CartService;
import com.mujinyu.shopping_common.service.GoodsService;
import com.mujinyu.shopping_common.service.OrdersService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping ("/user/orders")
public class OrdersController {
    @DubboReference
    private OrdersService ordersService;
    @DubboReference
    private CartService cartService;
    @DubboReference(timeout = 2000, retries = 0, check = false)
    private GoodsService goodsService;


    /**
     * 生成订单
     *
     * @param orders 订单对象
     * @param userId 用户ID
     * @return 生成的订单
     */
    @PostMapping ("/add")
    public BaseResult<Orders> add(@RequestBody Orders orders, @LoginUser Long userId) {
        // 保存订单
        orders.setUserId(userId); // 设置用户ID
        orders.setOrderType(1); // 1、普通订单 2、秒杀订单
        orders.setSeckillId(null); // 设置秒杀订单ID为空
        // 价格以服务端购物车为准，忽略客户端传来的 price/title/headerPic，避免伪造金额
        bindServerPrice(orders);
        Orders orders1 = ordersService.add(orders);
        // 将Redis中的购物车商品删除
        List<CartGoods> cartGoods = orders.getCartGoods();
        for (CartGoods cartGood : cartGoods) {
            cartService.deleteCartOption(userId, cartGood.getGoodId());
        }
        return BaseResult.ok(orders1);
    }

    /**
     * 结算时以商品服务为准：一次性批量取价，覆盖客户端提交的明细。
     * 客户端只允许提交 goodId 和 num；商品不存在或已下架时直接拒绝下单。
     */
    private void bindServerPrice(Orders orders) {
        List<CartGoods> requested = orders.getCartGoods();
        if (requested == null || requested.isEmpty()) {
            throw new BusException(CodeEnum.PAYMENT_ERROR);
        }
        // 1、收集去重后的商品 ID，一次批量查询，避免 N 次 RPC
        Set<Long> goodIds = new LinkedHashSet<>();
        for (CartGoods item : requested) {
            if (item == null || item.getGoodId() == null) {
                throw new BusException(CodeEnum.PARAMETER_ERROR);
            }
            goodIds.add(item.getGoodId());
        }
        List<Goods> goodsList = goodsService.findByIds(new ArrayList<>(goodIds));
        Map<Long, Goods> goodsMap = new HashMap<>();
        if (goodsList != null) {
            for (Goods goods : goodsList) {
                goodsMap.put(goods.getId(), goods);
            }
        }
        // 2、用商品服务的最新价格覆盖；商品不存在或已下架不允许下单
        for (CartGoods item : requested) {
            Goods goods = goodsMap.get(item.getGoodId());
            if (goods == null || !Boolean.TRUE.equals(goods.getIsMarketable())) {
                throw new BusException(CodeEnum.PARAMETER_ERROR);
            }
            item.setPrice(goods.getPrice());
            item.setGoodsName(goods.getGoodsName());
            item.setHeaderPic(goods.getHeaderPic());
        }
    }

    /**
     * 根据id查询订单详情（只能查自己的订单，归属校验在 orders_service 里做）
     *
     * @param id     订单id
     * @param userId 用户ID
     * @return 查询结果
     */
    @GetMapping ("/findById")
    public BaseResult<Orders> findById(@RequestParam ("id") String id, @LoginUser Long userId) {
        return BaseResult.ok(ordersService.findOwnedOrder(id, userId));
    }

    /**
     * 查询用户的订单
     *
     * @param userId 用户ID
     * @param status 订单状态：1.未付款 2.已付款 3.未发货 4.已发货 5.交易成功 6.交易关闭 7.待评价；不传表示查询全部订单
     * @param page   页码
     * @param size   每页条数
     * @return 订单列表
     */
    @GetMapping ("/findUserOrders")
    public BaseResult<PageResult<Orders>> findUserOrders(@LoginUser Long userId,
                                                         @RequestParam (value = "status", required = false) Integer status,
                                                         @RequestParam (defaultValue = "1") int page,
                                                         @RequestParam (defaultValue = "10") int size) {
        return BaseResult.ok(ordersService.findUserOrders(userId, status, page, size));
    }
}

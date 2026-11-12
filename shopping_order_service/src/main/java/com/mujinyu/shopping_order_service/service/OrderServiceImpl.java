package com.mujinyu.shopping_order_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.result.PageResult;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_order_service.mapper.CartGoodsMapper;
import com.mujinyu.shopping_order_service.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DubboService
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrdersService {
    private final OrdersMapper ordersMapper;
    private final CartGoodsMapper cartGoodsMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final String CHECK_ORDERS_QUEUE = "check_orders_queue";
    private static final int MAX_PAGE_SIZE = 50;

    @Override
    @Transactional (rollbackFor = Exception.class)
    public Orders add(Orders orders) {
        // 订单状态未付款
        if (orders.getStatus() == null) {
            orders.setStatus(1);
        }
        // 订单创建时间
        orders.setCreateTime(new Date());
        // 计算订单总价，遍历订单所有商品
        fillAndValidateAmount(orders);

        ordersMapper.insert(orders);
        List<CartGoods> cartGoods = orders.getCartGoods();
        for (CartGoods cartGood : cartGoods) {
            cartGood.setOrderId(orders.getId());
            cartGoodsMapper.insert(cartGood); // 数据量小时逐条即可，量大该insertBatchSomeColumn
        }

        // MQ必须等事务提交后再发；否则事务回滚了消息却已投出，消费端查不到订单
        String orderId = orders.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rocketMQTemplate.syncSend(CHECK_ORDERS_QUEUE, MessageBuilder.withPayload(orderId).build(), 3000, 16);
                } catch (Exception e) {
                    log.error("订单[{}]延时检查消息发送失败，需人工补偿", orderId, e);
                }
            }
        });
        return orders;
    }

    /**
     * 修改订单
     * 关单（status=6）必须走条件更新：只有「未付款(1)」的订单允许关闭，
     * 避免一次全字段 updateById 把已支付订单覆盖回关闭状态
     */
    @Override
    public void update(Orders orders) {
        if (orders == null || orders.getId() == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        if (Integer.valueOf(6).equals(orders.getStatus())) {
            if (!closeIfUnpaid(orders.getId())) {
                log.info("订单[{}]已不是未付款状态，忽略关闭", orders.getId());
            }
            return;
        }
        ordersMapper.updateById(orders);
    }

    @Override
    public Orders findById(String id) {
        return ordersMapper.findById(id);
    }

    @Override
    public Orders findOwnedOrder(String id, Long userId) {
        Orders orders = ordersMapper.findById(id);
        if (orders == null) {
            // 秒杀订单在支付前只存在于 Redis，查不到时不能说"接口不存在"
            throw new BusException(CodeEnum.ORDER_EXPIRED_ERROR);
        }
        if (orders.getUserId() == null || !String.valueOf(orders.getUserId()).equals(String.valueOf(userId))) {
            throw new BusException(CodeEnum.PERMISSION_DENIED);
        }
        return orders;
    }

    @Override
    public PageResult<Orders> findUserOrders(Long userId, Integer status, int page, int size) {
        int safePage = Math.max(page, 1); // 页码最小为1
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE); // 每页大小在1到50之间
        long total = ordersMapper.countUserOrders(userId, status); // 统计符合条件的订单数量
        if (total == 0) { // 如果没有符合条件的订单，就返回空结果的PageResult
            return new PageResult<>(0, safePage, safeSize, Collections.emptyList());
        }
        List<Orders> orders = ordersMapper.findOrdersPage(userId, status, (safePage - 1) * safeSize, safeSize); // 分页查询符合条件的订单
        if (!orders.isEmpty()) {
            List<String> orderIds = orders.stream() // 获取订单ID列表
                    .map(Orders::getId) // 提取订单ID
                    .collect(Collectors.toList()); // 收集为列表
            List<CartGoods> goods = cartGoodsMapper.selectList(new LambdaQueryWrapper<CartGoods>().in(CartGoods::getOrderId, orderIds)); // 根据订单ID列表查询订单商品
            Map<String, List<CartGoods>> grouped = goods.stream()
                    .collect(Collectors.groupingBy(CartGoods::getOrderId)); // 按订单ID分组
            orders.forEach(o -> o.setCartGoods(grouped.getOrDefault(o.getId(), Collections.emptyList()))); // 将订单商品列表设置到订单对象中
        }
        return new PageResult<>(total, safePage, safeSize, orders);
    }

    @Override
    public boolean markPaidIfUnpaid(String id) {
        return ordersMapper.markPaidIfUnpaid(id, new Date()) > 0;
    }

    @Override
    public boolean closeIfUnpaid(String id) {
        return ordersMapper.closeIfUnpaid(id) > 0;
    }

    @Override
    public boolean markRefundingIfPaid(String id) {
        return ordersMapper.markRefundingIfPaid(id) > 0;
    }

    @Override
    public boolean markRefundedIfRefunding(String id) {
        return ordersMapper.markRefundedIfRefunding(id) > 0;
    }

    @Override
    public boolean restoreStatus(String id, Integer status) {
        if (status == null) {
            // 没有记录原状态时，退回到“已支付”状态，避免订单卡在8
            status = 2;
        }
        return ordersMapper.restoreStatus(id, status) > 0;
    }

    /**
     * 填充并验证订单金额信息
     * 计算订单商品总金额和运费，并设置订单支付金额
     *
     * @param orders 订单对象，包含商品列表和运费信息
     * @throws BusException 当商品列表为空、商品数量或价格异常、运费为负数时抛出业务异常
     */
    private void fillAndValidateAmount(Orders orders) {
        // 获取订单中的商品列表
        List<CartGoods> cartGoods = orders.getCartGoods();
        // 检查商品列表是否为空
        if (cartGoods == null || cartGoods.isEmpty()) {
            throw new BusException(CodeEnum.PAYMENT_ERROR);
        }
        // 初始化总金额为0
        BigDecimal sum = BigDecimal.ZERO;
        // 遍历商品列表，计算总金额
        for (CartGoods cartGood : cartGoods) {
            // 获取商品数量
            Integer num = cartGood.getNum();
            // 验证商品数量是否有效（必须大于0）
            if (num == null || num <= 0) {
                throw new BusException(CodeEnum.PARAMETER_ERROR);
            }
            // 获取商品单价
            BigDecimal price = cartGood.getPrice();
            // 验证商品价格是否有效（不能为负数）
            if (price == null || price.signum() < 0) {
                throw new BusException(CodeEnum.PARAMETER_ERROR);
            }
            // 累加商品总价（单价×数量）
            sum = sum.add(price.multiply(BigDecimal.valueOf(num)));
        }
        // 运费：null视为包邮；负数/异常值直接拒绝
        BigDecimal postFee = orders.getPostFee();
        if (postFee == null) {
            postFee = BigDecimal.ZERO;
        }
        if (postFee.signum() < 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        orders.setPostFee(postFee);
        orders.setPayment(sum.add(postFee));
    }
}

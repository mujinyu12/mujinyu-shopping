package com.mujinyu.shopping_order_service.listener;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_common.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

/**
 * 延迟队列消费者，检查订单状态
 */
@Service
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "check_orders_queue", consumerGroup = "check_orders_queue")
public class CheckOrderListener implements RocketMQListener<String> {
    private final OrdersService ordersService;

    // 支付服务未启动时不阻塞订单服务启动：关单失败会由重试/补偿兜底
    @DubboReference(check = false, retries = 0, timeout = 3000)
    private WxPayService wxPayService;

    @Override
    public void onMessage(String orderId) {
        // 查询订单
        Orders orders = ordersService.findById(orderId);
        if (orders == null) {
            log.warn("关单检查：订单不存在，忽略。orderId={}", orderId);
            return;
        }
        if (!Integer.valueOf(1).equals(orders.getStatus())) {
            return; // 已支付/已关闭，幂等返回
        }
        // 1、先关微信侧订单（已支付会返回 ORDERPAID，重新检查状态）
        try {
            wxPayService.closeOrder(orderId);
        } catch (Exception e) {
            log.warn("关单检查：微信关单失败，orderId={}，message={}", orderId, e.getMessage());
        }
        // 2、条件更新：只有仍是1才改成6；影响0行说明已被支付回调抢先处理
        boolean closed = ordersService.closeIfUnpaid(orderId);
        log.info("关单检查：订单{}，是否成功关闭={}", orderId, closed);
    }
}

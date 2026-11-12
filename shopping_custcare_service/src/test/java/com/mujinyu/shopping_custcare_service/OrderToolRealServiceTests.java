package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.service.AICustCareService;
import com.mujinyu.shopping_common.service.OrdersService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实链路的验证（不 Mock 任何东西）：AI → 工具 → Dubbo → shopping_order_service → MySQL。
 * <p>
 * 前置条件：shopping_order_service 已经跑起来（它依赖 RocketMQ 的 name-server，
 * 只跑 mqnamesrv 就能启动，findUserOrders 这条查询本身不走 MQ）。
 * 会真实调用大模型，计费，所以不进日常回归。
 */
@SpringBootTest
class OrderToolRealServiceTests {

    @Autowired
    private AICustCareService aiCustCareService;

    @Autowired
    private OrdersService ordersService;

    @Test
    @DisplayName("Dubbo 直连：能查到 userId=1 的订单")
    void dubboCanReachOrderService() {
        assertNotNull(ordersService, "OrdersService 没注入，检查 @DubboReference 和注册中心");
        List<Orders> orders = ordersService.findUserOrders(1L, 1);
        System.out.println("[Dubbo直连] 待支付订单数=" + orders.size());
        assertFalse(orders.isEmpty(), "userId=1 在 bz_orders 里有 6 条 status=1 的订单，应该能查到");
    }

    @Test
    @DisplayName("整条链路：AI 调工具查真实订单，回答里出现真实订单号")
    void aiQueriesRealOrders() {
        String answer = aiCustCareService.generateResponse("帮我查一下我的待支付订单", 1L);
        System.out.println("[AI回答-真实链路] " + answer);
        assertNotNull(answer);
        assertTrue(answer.contains("2115796694468780034") || answer.contains("2115813341422198786"),
                "回答里应该出现数据库里真实存在的订单号，实际：" + answer);
    }
}
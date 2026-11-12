package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.service.AICustCareService;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_custcare_service.functioncall.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单查询工具（Function Calling）的端到端测试。
 * <p>
 * 会真实调用大模型（计费）。订单数据用 Mock 的 {@link OrdersService} 顶替，
 * 因为 shopping_order_service 依赖 RocketMQ，本机没跑 broker 时它起不来；
 * 真正打通 Dubbo 的那一段单独用 curl / 前端验证。
 */
@SpringBootTest
class OrderToolCallingTests {

    @Autowired
    private AICustCareService aiCustCareService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private OrderQueryService orderQueryService;

    @BeforeEach
    void stubOrderService() {
        OrdersService ordersService = mock(OrdersService.class);
        when(ordersService.findUserOrders(eq(1L), any()))
                .thenReturn(List.of(order("2115796694468780034", "31999.00"),
                        order("2115813341422198786", "4000.00")));
        ReflectionTestUtils.setField(orderQueryService, "ordersService", ordersService);
    }

    private static Orders order(String id, String payment) {
        Orders o = new Orders();
        o.setId(id);
        o.setUserId(1L);
        o.setStatus(1);
        o.setPayment(new BigDecimal(payment));
        o.setCreateTime(new Date());
        return o;
    }

    @Test
    @DisplayName("工具 Bean 必须就是 Spring 管理的那个实例，否则 @DubboReference 永远是 null")
    void toolBeanIsTheSpringManagedInstance() {
        Function<?, ?> tool = context.getBean("getOrderQueryService", Function.class);
        assertSame(orderQueryService, tool,
                "FunctionCallConfig 里 new OrderQueryService() 会绕过 Spring，"
                        + "导致 ordersService 注入不进来（运行时 NPE）");
        assertNotNull(ReflectionTestUtils.getField(tool, "ordersService"), "ordersService 没有被注入");
    }

    @Test
    @DisplayName("端到端：用户问「待支付订单」，模型应调用工具并返回订单号")
    void aiQueriesUserOrders() {
        String answer = aiCustCareService.generateResponse("帮我查一下我的待支付订单", 1L);
        System.out.println("[AI回答] " + answer);
        assertNotNull(answer);
        assertTrue(answer.contains("2115796694468780034") || answer.contains("2115813341422198786"),
                "回答里应该出现真实订单号，说明工具真的被调用并回传了数据，实际：" + answer);
    }

    @Test
    @DisplayName("连续两次调用后，system prompt 不应该被反复拼接变长")
    void promptDoesNotGrow() {
        int first = promptLength();
        aiCustCareService.generateResponse("你好", 1L);
        int second = promptLength();
        System.out.println("[prompt 长度] 首次=" + first + " 调用后=" + second);
        assertEquals(first, second, "generateResponse 里曾写成 prompt += ...，每问一次提示词就长一截");
    }

    private int promptLength() {
        Object prompt = ReflectionTestUtils.getField(aiCustCareService, "prompt");
        return prompt == null ? 0 : prompt.toString().length();
    }
}
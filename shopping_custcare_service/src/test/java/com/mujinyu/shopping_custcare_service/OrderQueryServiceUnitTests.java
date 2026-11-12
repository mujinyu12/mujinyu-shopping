package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_custcare_service.context.UserContext;
import com.mujinyu.shopping_custcare_service.functioncall.OrderQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单查询工具的四项加固（来自上一轮的 4 条建议）单元测试。
 * <p>
 * 不启动 Spring、不连 Dubbo、不调大模型：直接 mock {@link OrdersService}。
 */
class OrderQueryServiceUnitTests {

    private OrdersService ordersService;

    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {
        ordersService = mock(OrdersService.class);
        orderQueryService = new OrderQueryService();
        ReflectionTestUtils.setField(orderQueryService, "ordersService", ordersService);
        UserContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 建议 1：用户 ID 只认服务端上下文，模型的参数里没有 userId 这个字段 */
    @Test
    @DisplayName("用户ID来自上下文，而不是模型参数")
    void userIdComesFromContext() {
        when(ordersService.findUserOrders(eq(1L), eq(1))).thenReturn(List.of(buildOrder("A1")));
        UserContext.setUserId(2L); // 上下文被 BFF 设置成 2 号用户
        when(ordersService.findUserOrders(eq(2L), eq(1))).thenReturn(List.of(buildOrder("B1")));

        OrderQueryService.Response response =
                orderQueryService.apply(new OrderQueryService.Request("待支付", 1));

        assertEquals("B1", response.orders().get(0).getId());
        verify(ordersService).findUserOrders(eq(2L), eq(1));
    }

    /** 建议 1 的反面：没登录就直接拒绝，绝不拿 null 去查库 */
    @Test
    @DisplayName("未登录时拒绝查询并给出可转述的错误")
    void rejectsAnonymousCaller() {
        UserContext.clear();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderQueryService.apply(new OrderQueryService.Request("待支付", 1)));
        assertTrue(ex.getMessage().contains("登录"));
    }

    /** 建议 2：条数上限 10，模型传 1000 也只返回 10 条 */
    @Test
    @DisplayName("条数被限制在 1~10 之间")
    void capsPageSize() {
        List<Orders> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(buildOrder("ORD-" + i));
        }
        when(ordersService.findUserOrders(eq(1L), any())).thenReturn(many);

        OrderQueryService.Response huge = orderQueryService.apply(new OrderQueryService.Request("待支付", 1000));
        assertEquals(10, huge.orders().size());

        OrderQueryService.Response zero = orderQueryService.apply(new OrderQueryService.Request("待支付", 0));
        assertEquals(1, zero.orders().size(), "0 或负数要兜底成 1，不能返回空");

        OrderQueryService.Response noSize = orderQueryService.apply(new OrderQueryService.Request("待支付", null));
        assertEquals(1, noSize.orders().size(), "不传条数默认 1");
    }

    /** 建议 3：不传状态就查全部（status = null 交给 XML 的 <if> 处理） */
    @Test
    @DisplayName("status 为空时查询该用户全部订单")
    void nullStatusQueriesAll() {
        when(ordersService.findUserOrders(eq(1L), isNull())).thenReturn(List.of(buildOrder("ALL-1")));
        OrderQueryService.Response response =
                orderQueryService.apply(new OrderQueryService.Request(null, 1));
        assertEquals("ALL-1", response.orders().get(0).getId());
        verify(ordersService).findUserOrders(eq(1L), isNull());
    }

    /** 建议 3 的补充：模型传中文状态也能翻译，解析不了就当没传 */
    @Test
    @DisplayName("中文状态词被翻译成数字，无法识别时按全部处理")
    void translatesChineseStatus() {
        when(ordersService.findUserOrders(eq(1L), any())).thenReturn(List.of());

        orderQueryService.apply(new OrderQueryService.Request("已发货", 1));
        verify(ordersService).findUserOrders(1L, 4);

        orderQueryService.apply(new OrderQueryService.Request("随便什么状态", 1));
        verify(ordersService).findUserOrders(1L, null);
    }

    /** 建议 4：隐私脱敏，手机号中间四位、收货人姓名只留姓 */
    @Test
    @DisplayName("返回前对手机号和收货人脱敏")
    void masksPrivacy() {
        Orders order = buildOrder("SECRET-1");
        order.setReceiver("欧阳娜娜");
        order.setReceiverMobile("19123456789");
        when(ordersService.findUserOrders(eq(1L), any())).thenReturn(List.of(order));

        Orders result = orderQueryService.apply(new OrderQueryService.Request("待支付", 1)).orders().get(0);

        assertEquals("191****6789", result.getReceiverMobile());
        assertEquals("欧***", result.getReceiver());
    }

    /** 空结果要返回空列表，让模型如实告知，而不是抛异常 */
    @Test
    @DisplayName("查不到订单时返回空列表")
    void emptyResultReturnsEmptyList() {
        when(ordersService.findUserOrders(eq(1L), any())).thenReturn(List.of());
        assertTrue(orderQueryService.apply(new OrderQueryService.Request("待支付", 1)).orders().isEmpty());
    }

    private static Orders buildOrder(String id) {
        Orders order = new Orders();
        order.setId(id);
        order.setPayment(new BigDecimal("100.00"));
        order.setStatus(1);
        order.setUserId(1L);
        order.setReceiver("张三");
        order.setReceiverMobile("13800138000");
        return order;
    }
}
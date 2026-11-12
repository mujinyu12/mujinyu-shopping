package com.mujinyu.shopping_custcare_service.config;

import com.mujinyu.shopping_custcare_service.functioncall.GoodsQueryService;
import com.mujinyu.shopping_custcare_service.functioncall.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

/**
 * Function Calling 配置。
 */
@Configuration
@RequiredArgsConstructor
public class FunctionCallConfig {

    private final OrderQueryService orderQueryService;

    private final GoodsQueryService goodsQueryService;

    @Bean
    @Description("查询当前用户的订单信息，输入订单状态（1=待支付，2=已完成）和查询条数（默认为1），用户ID由系统自动注入，不需要模型提供")
    public Function<OrderQueryService.Request, OrderQueryService.Response> getOrderQueryService() {
        return orderQueryService;
    }

    @Bean
    @Description("查询商品信息，输入商品关键字和查询条数（默认为1）")
    public Function<GoodsQueryService.Request, GoodsQueryService.Response> getGoodsQueryService() {
        return goodsQueryService;
    }
}
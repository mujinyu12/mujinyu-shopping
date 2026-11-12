package com.mujinyu.shopping_custcare_service.functioncall;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_custcare_service.context.UserContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Function;

/**
 * AI 客服的订单查询工具（Function Calling 的 function）。
 * 安全约束：
 *     用户 ID 不从模型参数里取，而是从 {@link UserContext} 拿 → 模型无法越权查别人的订单；
 *     条数上限 {@value #MAX_PAGE_SIZE}，防止模型传 1000 把 token 撑爆；
 *     状态支持「待支付」「已发货」这类中文，也支持 null（查该用户全部订单）；
 *     返回前对收货人、手机号做脱敏，原始隐私不会进入大模型的上下文。
 */
@Component
public class OrderQueryService implements Function<OrderQueryService.Request, OrderQueryService.Response> {

    /** 一次最多返回多少条订单（模型有时会瞎填一个大数字） */
    private static final int MAX_PAGE_SIZE = 10;

    /** 模型没给条数时默认返回几条 */
    private static final int DEFAULT_PAGE_SIZE = 1;

    // check = false：订单服务没启动时不要让整个 AI 客服跟着起不来，
    // 真正调用时连不上会抛异常，由下面的 catch 转成一句人话交给模型。
    @DubboReference(check = false, timeout = 5000, retries = 0)
    private OrdersService ordersService;

    /**
     * 应用
     *
     * @param request the function argument
     * @return the function result
     */
    @Override
    public Response apply(Request request) {
        // 用户身份只认服务端上下文，忽略模型可能编造的任何用户信息
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("未获取到当前登录用户，无法查询订单，请用户先登录后再试");
        }
        int size = resolveSize(request == null ? null : request.size()); // 查询条数
        Integer status = resolveStatus(request == null ? null : request.status()); // 订单状态
        List<Orders> userOrders;
        try {
            userOrders = ordersService.findUserOrders(userId, status);
        } catch (Exception e) {
            // 订单服务不可用时给模型一句能转述的话，而不是把堆栈丢给用户
            throw new IllegalStateException("订单服务暂时不可用，请稍后再试", e);
        }
        if (userOrders == null || userOrders.isEmpty()) {
            return new Response(List.of());
        }
        List<Orders> result = userOrders.stream()
                .limit(size)
                .map(OrderQueryService::desensitize)
                .toList();
        return new Response(result);
    }

    /**
     * 条数兜底：默认 1 条，范围 [1, 10]
     */
    private static int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    /**
     * 状态兜底：模型可能传 "待支付"、也可能传 "1"，还可能是 null（不指定状态 → 查全部）。
     * 解析失败时返回 null 而不是抛异常，这样至少能查到该用户的全部订单，而不是让用户看到报错。
     */
    private static Integer resolveStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String text = status.trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            // 不是数字，按中文状态词翻译
        }
        return switch (text) {
            case "待支付", "未付款", "未支付", "未付款订单" -> 1;
            case "已付款", "已支付", "待发货" -> 2;
            case "已发货", "发货" -> 4;
            case "交易成功", "已完成", "完成" -> 5;
            case "交易关闭", "已关闭", "已取消", "取消" -> 6;
            case "待评价" -> 7;
            default -> null;
        };
    }

    /**
     * 脱敏：手机号中间四位打码、收货人只留姓，避免把隐私原样喂给大模型
     */
    private static Orders desensitize(Orders order) {
        if (order == null) {
            return null;
        }
        order.setReceiverMobile(maskPhone(order.getReceiverMobile()));
        order.setReceiver(maskName(order.getReceiver()));
        return order;
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String text = phone.trim(); // 去除首尾空格
        if (text.length() >= 11) {
            return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
        }
        if (text.length() > 4) {
            return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
        }
        return "****";
    }

    private static String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String text = name.trim(); // 去除首尾空格
        if (text.length() <= 1) {
            return text;
        }
        return text.charAt(0) + "*".repeat(text.length() - 1);
    }

    /**
     * 服务的输入
     * <p>
     * 故意不包含 userId：用户身份由服务端上下文注入，模型不允许指定。
     *
     * @param status 订单状态，可传 1/2/4/5/6/7，也可传「待支付」「已发货」等中文，不传则查全部
     * @param size   查询条数，上限 {@value #MAX_PAGE_SIZE}
     */
    public record Request(String status, Integer size) {

    }

    /**
     * 服务的输出
     *
     * @param orders 订单信息列表（收货人、手机号已脱敏）
     */
    public record Response(List<Orders> orders) {

    }
}
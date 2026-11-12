package com.mujinyu.shopping_order_customer_api.controller;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.Payment;
import com.mujinyu.shopping_common.pojo.WxRefundNotify;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.OrdersService;
import com.mujinyu.shopping_common.service.RefundService;
import com.mujinyu.shopping_common.service.WxPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/payment")
public class PaymentController {

    @DubboReference
    private WxPayService wxPayService;
    @DubboReference
    private OrdersService ordersService;
    @DubboReference
    private RefundService refundService;

    /**
     * 生成支付二维码内容
     *
     * @param orderId 订单 id
     * @return code_url，前端用它生成二维码
     */
    @PostMapping("/pcPay")
    public BaseResult<String> pcPay(@RequestParam("orderId") String orderId, @LoginUser Long userId) {
        // 只能给自己的订单发起支付（网关不校验支付接口，归属校验在 orders_service 里做）
        Orders orders = ordersService.findOwnedOrder(orderId, userId);
        // 只有未付款的订单才能生成二维码（pay_service 内也会校验，这里提前拦截，省一次远程调用）
        if (orders.getStatus() == null || orders.getStatus() != 1) {
            throw new BusException(CodeEnum.ORDER_STATUS_ERROR);
        }
        return BaseResult.ok(wxPayService.nativePay(orders));
    }

    /**
     * 微信支付结果通知
     * 微信要求：处理成功返回 HTTP 200 且 body 为 {"code":"SUCCESS","message":"成功"}，
     * 否则会按策略重复通知（8 次，间隔逐渐变长）。
     */
    @PostMapping("/native/notify")
    @GlobalTransactional // 分布式事务管理
    public ResponseEntity<Map<String, String>> nativeNotify(
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestBody String body) {

        Payment payment;
        try {
            // 验签 + 解密在 pay_service 里做
            payment = wxPayService.notify(body, serial, nonce, signature, timestamp);
        } catch (Exception e) {
            log.error("微信支付回调处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "处理失败"));
        }

        // 非成功状态的通知，直接应答成功，不处理订单
        if (payment == null) {
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        }

        // 订单必须存在，否则没法更新状态（判空必须放在访问任何订单字段之前）
        Orders orders = ordersService.findById(payment.getOrderId());
        if (orders == null) {
            log.error("支付通知对应的订单不存在：{}", payment.getOrderId());
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        }
        // 校验实付金额：应付=orders.payment（下单时服务端算的），实付=payerTotal（微信回调里带过来的）
        BigDecimal paid = payment.getPayerTotal();
        BigDecimal expected = orders.getPayment();
        if (paid == null || expected == null || paid.compareTo(expected) != 0) {
            // 金额不符：不改变订单状态，记录异常并告警，人工介入
            log.error("[支付金额异常]订单{} 应付{} 元，实付{}元， 微信单号 {}", payment.getOrderId(), expected, paid, payment.getTransactionId());
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        }

        // 1. 先落支付记录，幂等用「库里有没有这条记录」判断，
        //    而不是用订单状态判断：否则第一次写库失败后，微信重试会被当成已处理直接应答成功，记录就永久丢了
        wxPayService.addPayment(payment);

        // 2. 再修改订单状态：2 = 已付款，paymentType = 1 表示微信支付
        boolean paid2 = ordersService.markPaidIfUnpaid(payment.getOrderId());
        if (paid2) {
            log.info("订单{}状态已更新为已付款", payment.getOrderId());
        } else {
            // 已经不是未付款状态：可能并发回调，也可能已关闭（超时关单后用户又付了钱）
            Orders latest = ordersService.findById(payment.getOrderId());
            if (latest != null && Integer.valueOf(6).equals(latest.getStatus())) {
                log.error("[超时关单后又支付]订单{}已关闭但收到支付成功，微信单号{}，需退款", payment.getOrderId(), payment.getTransactionId());
            }
        }

        return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
    }

    /**
     * 主动对账补单（回调丢失时的兜底）
     * 前端支付后轮询这个接口即可，不依赖微信能不能回调进来。
     */
    @GetMapping("/syncOrder")
    public BaseResult<String> syncOrder(@RequestParam("orderId") String orderId, @LoginUser Long userId) {
        Orders orders = ordersService.findOwnedOrder(orderId, userId);
        Payment payment = wxPayService.syncOrder(orderId);
        if (payment == null) {
            return BaseResult.ok("NOTPAY");
        }
        BigDecimal paid = payment.getPayerTotal();
        if (paid == null || orders.getPayment() == null || paid.compareTo(orders.getPayment()) != 0) {
            log.error("[对账金额异常] 订单 {} 应付 {} 元，实付 {} 元", orderId, orders.getPayment(), paid);
            return BaseResult.ok("金额不符");
        }
        // 条件更新：只有仍是未付款(1)才能置为已付款(2)，并发回调只会成功一次
        boolean synced = ordersService.markPaidIfUnpaid(orderId);
        if (synced) {
            log.info("订单{}已通过主动对账补单", orderId);
        } else {
            log.info("订单{}已不是未付款状态，无需补单", orderId);
        }
        return BaseResult.ok("SUCCESS");
    }

    /**
     * 主动查询微信侧订单状态（对账 / 补偿丢失的回调）
     */
    @GetMapping("/queryOrder")
    public BaseResult<String> queryOrder(@RequestParam("orderId") String orderId, @LoginUser Long userId) {
        // 微信侧订单信息不能跨用户查询
        ordersService.findOwnedOrder(orderId, userId);
        return BaseResult.ok(wxPayService.queryOrder(orderId));
    }

    /**
     * 关闭微信侧订单（用户取消订单时调用）
     */
    @PostMapping("/closeOrder")
    public BaseResult<String> closeOrder(@RequestParam("orderId") String orderId, @LoginUser Long userId) {
        // 不能关别人的订单
        Orders orders = ordersService.findOwnedOrder(orderId, userId);
        // 已关闭的订单直接返回，保证前端重复点击取消是幂等的
        if (Integer.valueOf(6).equals(orders.getStatus())) {
            return BaseResult.ok("订单已关闭");
        }
        // 只有未付款的订单需要关单，已付款的订单关单会让微信返回 ORDERPAID
        if (orders.getStatus() == null || orders.getStatus() != 1) {
            throw new BusException(CodeEnum.ORDER_STATUS_ERROR);
        }
        wxPayService.closeOrder(orderId);
        // 微信侧关单成功后，本地订单也必须落库为「交易关闭」，否则订单会一直停留在待支付状态
        if (!ordersService.closeIfUnpaid(orderId)) {
            throw new BusException(CodeEnum.ORDER_STATUS_ERROR);
        }
        return BaseResult.ok("订单已关闭");
    }

    /**
     * 微信退款结果通知。
     *
     * @param body 退款结果通知的 JSON 数据
     * @param serial 微信支付平台证书序列号
     * @param nonce 随机字符串
     * @param signature 签名
     * @param timestamp 时间戳
     * @return 微信要求返回 200 + {"code":"SUCCESS}，否则会重试
     */
    @PostMapping("/refund/notify")
    public ResponseEntity<Map<String, String>> refundNotify(@RequestHeader("Wechatpay-Serial") String serial,
                                                            @RequestHeader("Wechatpay-Nonce") String nonce,
                                                            @RequestHeader("Wechatpay-Signature") String signature,
                                                            @RequestHeader("Wechatpay-Timestamp") String timestamp,
                                                            @RequestBody String body) {
        try {
            WxRefundNotify wxRefundNotify = wxPayService.notifyRefund(body, serial, nonce, signature, timestamp);
            refundService.handleNotify(wxRefundNotify);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("微信退款回调处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "处理失败"));
        }
    }

}

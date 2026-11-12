package com.mujinyu.shopping_common.service;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.Payment;
import com.mujinyu.shopping_common.pojo.WxRefundNotify;

import java.math.BigDecimal;

/**
 * 微信支付服务接口
 */
public interface WxPayService {

    /**
     * Native 扫码下单，返回二维码内容（code_url）
     * 前端拿这个字符串生成二维码，用户用微信扫一扫即可付款。
     *
     * @param orders 订单对象，必须已存在且状态为未付款
     * @return code_url，例如 weixin://wxpay/bizpayurl?pr=xxxx
     */
    String nativePay(Orders orders);

    /**
     * 处理微信支付结果通知
     * 微信会用平台证书签名并加密回调报文，这里负责验签、解密，把结果转成支付记录对象。
     * 调用方（BFF）拿到 Payment 后负责修改订单状态并落库。
     *
     * @param body      回调报文原文（必须是未经任何处理的原始 JSON 字符串）
     * @param serial    请求头 Wechatpay-Serial，平台证书序列号
     * @param nonce     请求头 Wechatpay-Nonce
     * @param signature 请求头 Wechatpay-Signature
     * @param timestamp 请求头 Wechatpay-Timestamp
     * @return 支付成功的支付记录；如果通知的交易状态不是成功，返回 null
     */
    Payment notify(String body, String serial, String nonce, String signature, String timestamp);

    /**
     * 保存支付记录
     *
     * @param payment 支付记录
     */
    void addPayment(Payment payment);

    /**
     * 按订单号查支付记录，用于回调幂等判断
     *
     * @param orderId 订单 id
     * @return 支付记录，没有则返回 null
     */
    Payment findPaymentByOrderId(String orderId);

    /**
     * 主动对账补单：查微信侧交易，支付成功且库里没有记录时补一条
     *
     * @param orderId 订单 id
     * @return 补录（或已存在）的支付记录；微信侧未支付返回 null
     */
    Payment syncOrder(String orderId);

    /**
     * 主动查询微信侧订单状态，用于对账或补偿丢失的回调
     *
     * @param orderId 商户订单号（即我们的订单 id）
     * @return SUCCESS / NOTPAY / CLOSED 等；查询失败返回 ERROR
     */
    String queryOrder(String orderId);

    /**
     * 关闭微信侧订单（订单超时或用户取消时调用）
     *
     * @param orderId 商户订单号
     */
    void closeOrder(String orderId);


    /**
     * 发起退款（整单退）。金额以微信实收为准做上限校验，调用方负责业务侧金额校验
     *
     * @param orderId     商户订单号，即 bz_orders.id
     * @param outRefundNo 商户退款单号，必须全局唯一且重试时保持不变（幂等）
     * @param amount      退款金额（元）
     * @param reason      退款原因，会展示给用户
     * @return 微信退款单号refundId：同步失败会抛BusException(REFUND_ERROR)
     */
    String refund(String orderId, String outRefundNo, BigDecimal amount, String reason);

    /**
     * 解析微信退款结果通知（验签 + 解密），转成本项目的DTO
     * 报文非法会抛出BusException(CHECK_SIGN_ERROR)
     *
     * @param body      回调报文原文（必须是未经任何处理的原始 JSON 字符串）
     * @param serial    请求头 Wechatpay-Serial，平台证书序列号
     * @param nonce     请求头 Wechatpay-Nonce
     * @param signature 请求头 Wechatpay-Signature
     * @param timestamp 请求头 Wechatpay-Timestamp
     * @return 退款通知DTO
     */
    WxRefundNotify notifyRefund(String body, String serial, String nonce, String signature, String timestamp);
}

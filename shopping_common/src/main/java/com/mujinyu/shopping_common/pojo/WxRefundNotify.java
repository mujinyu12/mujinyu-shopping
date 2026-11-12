package com.mujinyu.shopping_common.pojo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 微信退款结果通知（从微信 SDK 的 RefundNotification 转出来的精简结构）
 */
@Data
public class WxRefundNotify implements Serializable {

    /** 商户退款单号，对应 bz_refund.refund_no */
    private String outRefundNo;

    /** 微信退款单号 */
    private String refundId;

    /** 商户订单号 */
    private String outTradeNo;

    /** SUCCESS / CLOSED / PROCESSING / ABNORMAL */
    private String status;

    /** 退款金额（元） */
    private BigDecimal refundAmount;
}
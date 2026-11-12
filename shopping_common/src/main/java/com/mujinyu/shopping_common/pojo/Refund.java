package com.mujinyu.shopping_common.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 退款单
 * 一个订单最多一张退款单（uk_order_id），状态流转：
 * 0 待审核 -> 1 退款中 -> 2 已退款
 *          -> 3 已驳回
 *          -> 4 退款失败
 */
@Data
public class Refund implements Serializable {
    @TableId (type = IdType.ASSIGN_ID)
    private String id;

    /** 订单 id */
    @TableField ("order_id")
    private String orderId;

    /** 申请用户 id */
    @TableField ("user_id")
    private Long userId;

    /** 商户退款单号（out_refund_no），全局唯一 */
    @TableField ("refund_no")
    private String refundNo;

    /** 微信支付订单号 */
    @TableField ("transaction_id")
    private String transactionId;

    /** 微信退款单号 */
    @TableField ("wx_refund_id")
    private String wxRefundId;

    /** 退款金额（元） */
    private BigDecimal amount;

    /** 用户填写的退款原因 */
    private String reason;

    /** 0 待审核 1 退款中 2 已退款 3 已驳回 4 退款失败 */
    private Integer status;

    /** 申请前的订单状态（2 或 3），驳回/失败时用于还原 */
    @TableField ("order_status_before")
    private Integer orderStatusBefore;

    /** 审批人 */
    @TableField ("audit_by")
    private String auditBy;

    /** 审批时间 */
    @TableField ("audit_time")
    private Date auditTime;

    /** 审批备注 / 失败原因 */
    @TableField ("audit_remark")
    private String auditRemark;

    @TableField ("create_time")
    private Date createTime;

    /** 退款成功时间 */
    @TableField ("success_time")
    private Date successTime;
}
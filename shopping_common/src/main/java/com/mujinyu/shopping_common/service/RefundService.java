package com.mujinyu.shopping_common.service;

import com.mujinyu.shopping_common.pojo.Refund;
import com.mujinyu.shopping_common.pojo.WxRefundNotify;
import com.mujinyu.shopping_common.result.PageResult;

/**
 * 退款服务（Dubbo，实现在 shopping_order_service）
 */
public interface RefundService {

    /**
     * 用户申请整单退款：订单 2/3 → 8，并生成待审核退款单
     */
    Refund apply(Long userId, String orderId, String reason);

    /** 按退款单 id 查询 */
    Refund findById(String id);

    /** 按订单 id 查询退款单，没有返回 null */
    Refund findByOrderId(String orderId);

    /** 后台分页查询，status 为 null 表示全部 */
    PageResult<Refund> findPage(Integer status, int page, int size);

    /**
     * 审批：approved=true 发起微信退款；false 驳回并还原订单状态
     */
    void audit(String refundId, boolean approved, String auditor, String remark);

    /**
     * 处理微信退款结果通知（幂等）
     */
    void handleNotify(WxRefundNotify notify);
}
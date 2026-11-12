package com.mujinyu.shopping_order_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.Refund;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

// 退款Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    /**
     * 根据订单ID查询退款记录
     *
     * @param orderId 订单ID，用于查询对应的退款信息
     * @return 返回与指定订单ID匹配的退款记录对象
     */
    Refund findByOrderId(@Param ("orderId") String orderId);

    /**
     * 分页查询退款记录的方法
     *
     * @param status 退款状态，用于筛选特定状态的退款记录
     * @param offset 分页偏移量，用于指定查询的起始位置
     * @param size   每页记录数，用于指定每页显示的记录数量
     * @return 返回一个Refund对象的列表，表示查询到的退款记录
     */
    List<Refund> findPage(@Param ("status") Integer status,
                          @Param ("offset") int offset,
                          @Param ("size") int size);

    /**
     * 根据状态统计页面的数量
     *
     * @param status 页面状态，用于筛选特定状态的页面
     * @return 符合指定状态的页面总数，返回类型为long
     */
    long countPage(@Param ("status") Integer status);

    /**
     * 驳回/退款失败后重新申请：复用同一行（uk_order_id 唯一），重置为待审核
     */
    int reopen(@Param ("id") String id,
               @Param ("refundNo") String refundNo,
               @Param ("amount") java.math.BigDecimal amount,
               @Param ("reason") String reason,
               @Param ("orderStatusBefore") Integer orderStatusBefore);

    /**
     * 标记处理状态的接口方法
     *
     * @param id        业务ID，用于唯一标识一条业务记录
     * @param auditBy   审核人，记录是谁进行的审核操作
     * @param auditTime 审核时间，记录审核操作发生的时间戳
     */
    int markProcessing(@Param ("id") String id,
                       @Param ("auditBy") String auditBy,
                       @Param ("auditTime") Date auditTime);

    /**
     * 0 待审核 -> 3 已驳回
     */
    int markRejected(@Param ("id") String id,
                     @Param ("auditBy") String auditBy,
                     @Param ("remark") String remark,
                     @Param ("auditTime") Date auditTime);

    /**
     * 1 退款中 -> 1（补写微信退款单号，状态不变）
     */
    int fillWxRefundId(@Param ("id") String id,
                       @Param ("wxRefundId") String wxRefundId,
                       @Param ("transactionId") String transactionId);

    /**
     * -> 4 退款失败
     */
    int markFailed(@Param ("id") String id, @Param ("remark") String remark);

    /**
     * 1 退款中 -> 2 已退款
     */
    int markSuccess(@Param ("id") String id, @Param ("successTime") Date successTime);
}

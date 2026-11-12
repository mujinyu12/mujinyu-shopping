package com.mujinyu.shopping_order_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.Orders;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

// 订单Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
    // 查询订单详情
    Orders findById(String id);

    // 查询用户订单
    List<Orders> findOrdersByUserIdAndStatus(@Param ("userId") Long userId, @Param ("status") Integer status);

    // 只把未付款改成已付款
    int markPaidIfUnpaid(@Param ("id") String id, @Param ("paymentTime") Date paymentTime);

    // 只把未付款改成已关闭
    int closeIfUnpaid(@Param ("id") String id);

    /**
     * 分页查询订单信息
     *
     * @param userId 用户ID，用于筛选特定用户的订单
     * @param status 订单状态，用于筛选特定状态的订单
     * @param offset 分页偏移量，用于指定查询的起始位置
     * @param size   每页显示的记录数，用于控制返回结果的数量
     * @return 返回符合条件的订单列表
     */
    List<Orders> findOrdersPage(@Param ("userId") Long userId,
                                @Param ("status") Integer status,
                                @Param ("offset") int offset,
                                @Param ("size") int size);

    /**
     * 统计用户订单数量
     *
     * @param userId 用户ID，用于指定要查询的用户
     * @param status 订单状态，用于筛选特定状态的订单
     * @return 符合条件的订单数量，返回类型为long
     */
    long countUserOrders(@Param ("userId") Long userId, @Param ("status") Integer status);

    // 2、已付款 / 3、未发货 -> 8、退款中
    int markRefundingIfPaid(@Param("id") String id);

    // 8、退款中 -> 9、已退款
    int markRefundedIfRefunding(@Param("id") String id);

    // 8、退款中 -> 指定状态（驳回/失败还原）
    int restoreStatus(@Param("id") String id, @Param("status") Integer status);
}

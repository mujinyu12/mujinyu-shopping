package com.mujinyu.shopping_common.service;

import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.result.PageResult;

import java.util.List;

// 订单服务
public interface OrdersService {
    // 生成订单
    Orders add(Orders orders);
    // 修改订单
    void update(Orders orders);
    // 根据id查询订单
    Orders findById(String id);
    // 根据id查询订单并校验归属：订单不存在抛 404，不属于该用户抛 403
    Orders findOwnedOrder(String id, Long userId);
    // 查询用户订单
    PageResult<Orders> findUserOrders(Long userId, Integer status, int page, int size);
    // 兼容旧调用方（AI客服的订单查询）：只按用户+状态取第一页，内部转调分页方法
    default List<Orders> findUserOrders(Long userId, Integer status) {
        return findUserOrders(userId, status, 1, 100).getList();
    }
    // 条件更新为已支付，返回是否更新成功
    boolean markPaidIfUnpaid(String id);
    // 条件更新为已关闭，返回是否更新成功
    boolean closeIfUnpaid(String id);
    // 条件更新为退款中（2 已付款 / 3 未发货 → 8），返回是否更新成功
    boolean markRefundingIfPaid(String id);
    // 条件更新为已退款（8 → 9），返回是否更新成功
    boolean markRefundedIfRefunding(String id);
    // 退款驳回/失败时还原订单状态（8 → 指定状态），返回是否更新成功
    boolean restoreStatus(String id, Integer status);
}

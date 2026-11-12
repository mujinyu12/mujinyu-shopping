package com.mujinyu.shopping_order_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.Refund;
import com.mujinyu.shopping_common.pojo.WxRefundNotify;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.result.PageResult;
import com.mujinyu.shopping_common.service.RefundService;
import com.mujinyu.shopping_common.service.SeckillService;
import com.mujinyu.shopping_common.service.WxPayService;
import com.mujinyu.shopping_order_service.mapper.OrdersMapper;
import com.mujinyu.shopping_order_service.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@DubboService
@Slf4j
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundMapper refundMapper;

    private final OrdersMapper ordersMapper;

    private static final int MAX_PAGE_SIZE = 50; // 最大页大小

    // 退款要走网络，超时时间要给足；retries=0 避免重复退单
    @DubboReference(timeout = 10000, retries = 0, check = false)
    private WxPayService wxPayService;

    // 秒杀单退款成功后回补库存；check=false 保证秒杀服务没起时订单服务也能启动
    @DubboReference(timeout = 50000, retries = 0, check = false)
    private SeckillService seckillService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Refund apply(Long userId, String orderId, String reason) {
        Orders orders = ordersMapper.findById(orderId); // 根据订单id查询订单
        if (orders == null) { // 如果订单为空，则直接抛出异常
            throw new BusException(CodeEnum.NOT_FOUND);
        }
        if (!Objects.equals(orders.getUserId(), userId)) { // 如果订单的用户id和传入的用户id不一致，则直接抛出异常
            throw new BusException(CodeEnum.PERMISSION_DENIED);
        }
        // 仅支持已付款或者未发货的订单退款
        Integer status = orders.getStatus();
        if (status == null || (status != 2 && status != 3)) {
            throw new BusException(CodeEnum.REFUND_STATUS_ERROR); // 如果订单状态不支持退款，则抛出异常
        }
        // 已有退款单：待审核/退款中/已退款都不允许重复申请；已驳回(3)/退款失败(4)允许重新申请
        Refund existing = refundMapper.findByOrderId(orderId);
        boolean canReopen = existing != null && existing.getStatus() != null
                && (existing.getStatus() == 3 || existing.getStatus() == 4);
        if (existing != null && !canReopen) {
            throw new BusException(CodeEnum.REFUND_STATUS_ERROR); // 如果订单已申请过退款，则抛出异常
        }
        // 条件更新：并发下只有一个请求能把 2/3 改成8
        if (ordersMapper.markRefundingIfPaid(orderId) == 0) {
            throw new BusException(CodeEnum.REFUND_STATUS_ERROR); // 如果订单状态不支持退款，则抛出异常
        }

        String refundNo = "RF" + IdWorker.getIdStr(); // 每次申请都用新的商户退款单号
        Refund refund;
        if (existing == null) {
            refund = new Refund();
            refund.setOrderId(orderId); // 设置订单id
            refund.setUserId(userId); // 设置用户id
            refund.setRefundNo(refundNo); // 设置退款单号
            refund.setAmount(orders.getPayment()); // 设置退款金额
            refund.setReason(reason); // 设置退款原因
            refund.setStatus(0); // 设置退款状态为待审核
            refund.setOrderStatusBefore(status); // 记录申请前的订单状态
            refund.setCreateTime(new Date()); // 设置退款申请时间
            refundMapper.insert(refund); // 插入退款记录
        } else {
            // 驳回/失败后重新申请：复用同一行（uk_order_id），清空上一次的审批与微信退款信息
            refundMapper.reopen(existing.getId(), refundNo, orders.getPayment(), reason, status);
            refund = refundMapper.selectById(existing.getId());
        }
        log.info("用户申请退款 orderId={} refundNo={} amount={}", orderId, refund.getRefundNo(), refund.getAmount());
        return refund;
    }

    @Override
    public Refund findById(String id) {
        return refundMapper.selectById(id); // 根据id查询退款记录
    }

    @Override
    public Refund findByOrderId(String orderId) {
        return refundMapper.findByOrderId(orderId); // 根据订单id查询退款记录
    }

    @Override
    public PageResult<Refund> findPage(Integer status, int page, int size) {
        int p = Math.max(page, 1); // 如果页码小于1，则设置为1
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE); // 如果页大小小于1，则设置为1；如果页大小大于最大页大小，则设置为最大页大小
        long total = refundMapper.countPage(status); // 根据状态查询退款记录总数
        return new PageResult<>(total, p, s, refundMapper.findPage(status, (p - 1) * s, s)); // 返回分页结果
    }

    @Override
    public void audit(String refundId, boolean approved, String auditor, String remark) {
        Refund refund = refundMapper.selectById(refundId); // 根据id查询退款记录
        if (refund == null) {
            throw new BusException(CodeEnum.NOT_FOUND); // 如果退款记录为空，则直接抛出异常
        }
        Integer current = refund.getStatus();
        // 只有待审核可以审批；状态 1 且没有微信退款单号说明上一次发起退款时进程中断，允许重试
        boolean interrupted = current != null && current == 1 && refund.getWxRefundId() == null;
        if (current == null || (current != 0 && !interrupted)) {
            throw new BusException(CodeEnum.REFUND_STATUS_ERROR); // 如果退款状态不是待审核，则抛出异常
        }

        if (!approved) {
            if (refundMapper.markRejected(refundId, auditor, remark, new Date()) == 0) {
                throw new BusException(CodeEnum.REFUND_STATUS_ERROR); // 如果不批准退款，修改退款状态失败，则抛出异常
            }
            ordersMapper.restoreStatus(refund.getOrderId(), refund.getOrderStatusBefore()); // 恢复订单状态
            log.info("退款驳回 refundNo={} auditor={} remark={}", refund.getRefundNo(), auditor, remark);
            return;
        }

        // 抢占：0 -> 1，防止两个管理员同时点通过
        if (refundMapper.markProcessing(refundId, auditor, new Date()) == 0) {
            throw new BusException(CodeEnum.REFUND_STATUS_ERROR);
        }
        try {
            String wxRefundId = wxPayService.refund(refund.getOrderId(), refund.getRefundNo(), refund.getAmount(), refund.getReason());
            refundMapper.fillWxRefundId(refundId, wxRefundId, null);
            log.info("微信退款已受理 refundNo={} wxRefundId={}", refund.getRefundNo(), wxRefundId);
        } catch (Throwable e) {
            // 微信退款调用必须保证退款单状态可以落库：这里连 Error 一起兜住，
            // 否则接口版本不匹配（AbstractMethodError）这类错误会让退款单永远停在“退款中”
            log.error("发起微信退款失败 refundNo={}", refund.getRefundNo(), e);
            String msg = e.getMessage() == null ? "退款失败" : e.getMessage();
            refundMapper.markFailed(refundId, msg.length() > 180 ? msg.substring(0, 180) : msg);
            ordersMapper.restoreStatus(refund.getOrderId(), refund.getOrderStatusBefore());
            throw new BusException(CodeEnum.REFUND_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleNotify(WxRefundNotify notify) {
        if (notify == null || notify.getOutRefundNo() == null) {
            return;
        }
        LambdaQueryWrapper<Refund> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Refund::getRefundNo, notify.getOutRefundNo()).last("limit 1");
        Refund refund = refundMapper.selectOne(queryWrapper);
        if (refund == null) {
            log.warn("退款回调找不到退款单 refundNo={}", notify.getOutRefundNo());
            return;
        }
        // 金额校验：回调里的退款金额必须和退款单一致，防止伪造/串单回调把订单直接改成已退款
        if (notify.getRefundAmount() != null && refund.getAmount() != null
                && notify.getRefundAmount().compareTo(refund.getAmount()) != 0) {
            log.error("退款回调金额与退款单不一致，拒绝处理 refundNo={} 回调金额={} 退款单金额={}",
                    refund.getRefundNo(), notify.getRefundAmount(), refund.getAmount());
            return;
        }
        String status = notify.getStatus();
        if ("SUCCESS".equals(status)) {
            // markSuccess 是条件更新（只有PROCESSING 才能改成 SUCCESS），返回true说明这一笔钱退款第一次成功，库存回补挂在它后面，天然保证重复回调不会补两次
            if (refundMapper.markSuccess(refund.getId(), new Date()) > 0) {
                ordersMapper.markRefundedIfRefunding(refund.getOrderId());
                restoreStockIfSeckill(refund.getOrderId()); // 秒杀单退款成功后回补库存
                log.info("退款成功 orderId={} refundNo={} amount={}", refund.getOrderId(), refund.getRefundNo(), refund.getAmount());
            }
        } else if ("CLOSED".equals(status) || "ABNORMAL".equals(status)) {
            log.error("退款失败/异常 refundNo={} status={}", notify.getOutRefundNo(), status);
            refundMapper.markFailed(refund.getId(), "微信退款状态：" + status);
            ordersMapper.restoreStatus(refund.getOrderId(), refund.getOrderStatusBefore());
        }
    }

    /**
     * 秒杀单退款成功后回补库存
     * 普通订单（order_type=1） 什么都不做-普通下单没有扣过库存
     */
    private void restoreStockIfSeckill(String orderId) {
        Orders orders = ordersMapper.findById(orderId);
        // 订单不存在，或者不是秒杀单（order_type != 2），都不需要回补库存
        if (orders == null || !Integer.valueOf(2).equals(orders.getOrderType())) {
            return;
        }
        List<CartGoods> cartGoods = orders.getCartGoods();
        if (cartGoods == null || cartGoods.isEmpty()) {
            log.warn("秒杀订单{}没有商品明细，跳过库存回补", orderId);
            return;
        }
        for (CartGoods item : cartGoods) {
            try {
                seckillService.restoreStockForRefund(orders.getSeckillId(), item.getGoodId(), item.getNum());
            } catch (Throwable e) {
                // 钱已经退给用户了，库存回补失败绝不能把退款单回滚：记日志由后台按“退款成功但库存未回补”人工兜底。
                // 这里连 Error 一起兜住（例如秒杀服务版本旧导致的 AbstractMethodError），否则会连退款成功状态一起回滚
                log.error("秒杀订单{}库存回补失败 goodsId={} num={}", orderId, item.getGoodId(), item.getNum(), e);
            }
        }
    }
}

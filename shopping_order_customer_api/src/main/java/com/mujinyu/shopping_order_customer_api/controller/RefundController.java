package com.mujinyu.shopping_order_customer_api.controller;

import com.mujinyu.shopping_common.pojo.Refund;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 用户端退款接口
 */
@RestController
@RequestMapping ("/user/refund")
@RequiredArgsConstructor
public class RefundController {

    @DubboReference (timeout = 5000, retries = 0, check = false)
    private RefundService refundService;

    /**
     * 申请退款
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @param reason  退款原因
     * @return 退款信息
     */
    @PostMapping ("/apply")
    public BaseResult<Refund> apply(@LoginUser Long userId,
                                    @RequestParam ("orderId") String orderId,
                                    @RequestParam (value = "reason", required = false) String reason) {
        return BaseResult.ok(refundService.apply(userId, orderId, reason));
    }

    /**
     * 根据ID查询退款单
     *
     * @param userId 用户ID
     * @param id     退款单ID
     * @return 退款单
     */
    @GetMapping ("/findById")
    public BaseResult<Refund> findById(@LoginUser Long userId, @RequestParam ("id") String id) {
        Refund refund = refundService.findById(id); // 根据ID查询订单信息
        if (refund == null) {
            throw new BusException(CodeEnum.NOT_FOUND);  // 如果退款单不存在，抛出异常
        }
        if (!Objects.equals(refund.getUserId(), userId)) {
            throw new BusException(CodeEnum.PERMISSION_DENIED); // 如果退款单不属于当前用户，抛出异常
        }
        return BaseResult.ok(refund);
    }

    /**
     * 根据订单ID查询退款单
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 退款单
     */
    @GetMapping ("/findByOrderId")
    public BaseResult<Refund> findByOrderId(@LoginUser Long userId, @RequestParam ("orderId") String orderId) {
        Refund refund = refundService.findByOrderId(orderId); // 根据订单ID查询退款单
        if (refund != null && !Objects.equals(refund.getUserId(), userId)) {
            throw new BusException(CodeEnum.PERMISSION_DENIED); // 如果退款单不属于当前用户，抛出异常
        }
        return BaseResult.ok(refund);
    }
}

package com.mujinyu.shopping_manager_api.controller;

import com.mujinyu.shopping_common.pojo.Refund;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.PageResult;
import com.mujinyu.shopping_common.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 后台退款审批
 */
@RestController
@RequestMapping("/admin/refund")
@RequiredArgsConstructor
public class RefundAdminController {

    @DubboReference(timeout = 15000, retries = 0, check = false)
    private RefundService refundService;

    @GetMapping("/findPage")
    @PreAuthorize("hasAnyAuthority('/admin/refund')")
    public BaseResult<PageResult<Refund>> findPage(@RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return BaseResult.ok(refundService.findPage(status, page, size));
    }

    @PostMapping("/audit")
    @PreAuthorize("hasAnyAuthority('/admin/refund')")
    public BaseResult<String> audit(@RequestParam("id") String id,
                                    @RequestParam("approved") boolean approved,
                                    @RequestParam(value = "remark", required = false) String remark,
                                    Authentication authentication) {
        refundService.audit(id, approved, authentication.getName(), remark);
        return BaseResult.ok(approved ? "退款已受理" : "已驳回");
    }
}

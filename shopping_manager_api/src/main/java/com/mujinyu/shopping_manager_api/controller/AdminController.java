package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Admin;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台管理员
 */
@RestController
@RequiredArgsConstructor
@RequestMapping ("/admin")
public class AdminController {
    @DubboReference
    private AdminService adminService;

    private final PasswordEncoder encoder;

    /**
     * 新增管理员
     *
     * @param admin 管理员对象
     * @return 结果
     */
    @PostMapping ("/add")
    @PreAuthorize("hasAnyAuthority(\"/admin/add\")")
    public BaseResult add(@RequestBody Admin admin) {
        String password = admin.getPassword();
        password = encoder.encode(password);
        admin.setPassword(password);
        adminService.add(admin);
        return BaseResult.ok("新增管理员对象成功");
    }

    /**
     * 更新管理员
     *
     * @param admin 管理员对象
     * @return 结果
     */
    @PutMapping ("/update")
    @PreAuthorize("hasAnyAuthority(\"/admin/update\")")
    public BaseResult update(@RequestBody Admin admin) {
        String password = admin.getPassword();
        if (password != null && !password.isEmpty()) { // 如果密码不为空，则进行加密
            password = encoder.encode(password);
            admin.setPassword(password);
        }
        adminService.update(admin);
        return BaseResult.ok("更新管理员对象成功");
    }

    /**
     * 删除管理员
     *
     * @param aid 管理员id
     * @return 结果
     */
    @DeleteMapping ("/delete")
    @PreAuthorize("hasAnyAuthority(\"/admin/delete\")")
    public BaseResult delete(@RequestParam ("aid") Long aid) {
        adminService.delete(aid);
        return BaseResult.ok("删除管理员对象成功");
    }

    /**
     * 根据id查询管理员
     *
     * @param aid 管理员id
     * @return 管理员对象
     */
    @GetMapping ("/findById")
    @PreAuthorize("hasAnyAuthority(\"/admin/findById\")")
    public BaseResult<Admin> findById(@RequestParam ("aid") Long aid) {
        return BaseResult.ok(adminService.findById(aid));
    }

    /**
     * 分页查询管理员
     *
     * @param page 页码
     * @param size 每页条数
     * @return 查询结果
     */
    @GetMapping ("/search")
    @PreAuthorize("hasAnyAuthority(\"/admin/search\")")
    public BaseResult<Page<Admin>> search(@RequestParam ("page") int page, @RequestParam ("size") int size) {
        return BaseResult.ok(adminService.search(page, size));
    }

    /**
     * 修改管理员角色
     *
     * @param aid  管理员id
     * @param rids 角色ids
     * @return 结果
     */
    @PutMapping ("/updateRoleToAdmin")
    @PreAuthorize("hasAnyAuthority(\"/admin/updateRoleToAdmin\")")
    public BaseResult updateRoleToAdmin(@RequestParam ("aid") Long aid, @RequestParam ("rids") Long[] rids) {
        if (rids == null || rids.length == 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(), "至少为管理员分配一个角色");
        }
        adminService.updateRoleToAdmin(aid, rids);
        return BaseResult.ok("修改管理员角色成功");
    }

    /**
     * 获取登录的管理员名
     * @return 管理员名
     */
    @GetMapping("/getUsername")
    public BaseResult<String> getUsername() {
        // 1、获取会话对象
        SecurityContext context = SecurityContextHolder.getContext();
        // 2、获取认证对象
        Authentication authentication = context.getAuthentication();
        // 3、获取登录用户信息
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        return BaseResult.ok(username);
    }

    /**
     * 当前登录管理员的权限点（URL列表）
     * @param authentication 当前登录管理员的认证权限列表
     * @return 当前登录管理员的权限点（URL列表）
     */
    @GetMapping("/permissions")
    public BaseResult<List<String>> permissions(Authentication authentication) {
        List<String> urls = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return BaseResult.ok(urls);
    }
}
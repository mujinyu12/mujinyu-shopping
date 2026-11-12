package com.mujinyu.shopping_manager_api.security;

import com.mujinyu.shopping_common.pojo.Admin;
import com.mujinyu.shopping_common.pojo.Permission;
import com.mujinyu.shopping_common.service.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MyUserDetailService implements UserDetailsService {

    @DubboReference
    private AdminService adminService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1、认证
        Admin admin = adminService.findByAdminName(username);
        if (admin == null) {
            throw new UsernameNotFoundException("用户名不存在");
        }

        // 2、授权
        List<Permission> permissions = adminService.findAllPermission(username);
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 权限集合可能为 null，也可能为空（管理员未分配角色），不能直接 get(0)
        if (permissions != null && !permissions.isEmpty()) {
            for (Permission permission : permissions) {
                // 跳过空权限和没有资源路径的脏数据，避免把 null 变成 authority
                if (permission == null || !StringUtils.hasText(permission.getUrl())) {
                    continue;
                }
                // 去掉库里可能存在的首尾空格，否则与 @PreAuthorize 里的权限串对不上
                authorities.add(new SimpleGrantedAuthority(permission.getUrl().trim()));
            }
        }
        if (authorities.isEmpty()) {
            // 无任何权限点时仍允许登录（否则登录接口会 500），但访问受权限点保护的接口都会 403
            log.warn("管理员 {} 未分配任何有效权限点，访问受权限保护的接口将返回 403", username);
        }

        // 3、封装为UserDetails对象
        // 4、返回封装好的UserDetails对象
        return User.withUsername(admin.getUsername())
                .password(admin.getPassword())
                .authorities(authorities)
                .build();
    }
}

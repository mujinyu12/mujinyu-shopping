package com.mujinyu.shopping_admin_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_admin_service.mapper.AdminMapper;
import com.mujinyu.shopping_common.pojo.Admin;
import com.mujinyu.shopping_common.pojo.Permission;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@DubboService
@Transactional
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;

    @Override
    public void add(Admin admin) {
        adminMapper.insert(admin);
    }

    @Override
    public void update(Admin admin) {
        // 参数校验：id 为空时无法定位记录
        if (admin == null || admin.getAid() == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 如果前端传来的是空密码，则还使用原来的密码
        if (!StringUtils.hasText(admin.getPassword())) {
            // 查询原来的密码；记录不存在时直接抛业务异常，避免 selectById 返回 null 后 NPE 变成 500
            Admin existAdmin = adminMapper.selectById(admin.getAid());
            if (existAdmin == null) {
                throw new BusException(CodeEnum.NOT_FOUND);
            }
            admin.setPassword(existAdmin.getPassword());
        }
        adminMapper.updateById(admin);
    }

    @Override
    public void delete(Long id) {
        // 1、删除用户的所有角色
        adminMapper.deleteAdminAllRole(id);
        // 2、删除用户
        adminMapper.deleteById(id);
    }

    @Override
    public Admin findById(Long id) {
        return adminMapper.findById(id);
    }

    @Override
    public Page<Admin> search(int page, int size) {
        Page<Admin> result = adminMapper.selectPage(new Page<>(page, size), null);
        // 分页查询只查了 bz_admin 本身，roles 是 @TableField(exist = false) 不会自动带出来，
        // 这里按页内记录逐条补一次角色，否则前端列表的“角色”列永远是空的，
        // 分配完角色看起来像没生效。每页最多 size 条（前端固定 10），开销可接受。
        result.getRecords().forEach(admin -> {
            Admin detail = adminMapper.findById(admin.getAid());
            if (detail != null) {
                admin.setRoles(detail.getRoles());
            }
        });
        return result;
    }

    @Override
    public void updateRoleToAdmin(Long adminId, Long[] roleIds) {
        // 1、删除用户所有角色
        adminMapper.deleteAdminAllRole(adminId);
        // 2、重新添加管理员角色
        for (Long roleId : roleIds) {
            adminMapper.addRoleToAdmin(adminId, roleId);
        }
    }

    @Override
    public Admin findByAdminName(String username) {
        QueryWrapper<Admin> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return adminMapper.selectOne(wrapper);
    }

    @Override
    public List<Permission> findAllPermission(String username) {
        return adminMapper.findAllPermission(username);
    }
}

package com.mujinyu.shopping_admin_service.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_admin_service.mapper.RoleMapper;
import com.mujinyu.shopping_common.pojo.Role;
import com.mujinyu.shopping_common.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@DubboService
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    @Override
    public void add(Role role) {
        roleMapper.insert(role);
    }

    @Override
    public void update(Role role) {
        roleMapper.updateById(role);
    }

    @Override
    public void delete(Long id) {
        // 1、删除角色
        roleMapper.deleteById(id);
        // 2、删除角色所有权限
        roleMapper.deleteRoleAllPermission(id);
        // 3、删除用户_角色表中的相关数据
        roleMapper.deleteRoleAllAdmin(id);
    }

    @Override
    public Role findById(Long id) {
        return roleMapper.findById(id);
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(null);
    }

    @Override
    public Page<Role> search(int page, int size) {
        return roleMapper.selectPage(new Page<>(page, size), null);
    }

    @Override
    public void updatePermissionToRole(Long rid, Long[] pids) {
        // 1、删除角色所有权限
        roleMapper.deleteRoleAllPermission(rid);
        // 2、添加角色权限
        for (Long pid : pids) {
            roleMapper.addPermissionToRole(rid, pid);
        }
    }
}

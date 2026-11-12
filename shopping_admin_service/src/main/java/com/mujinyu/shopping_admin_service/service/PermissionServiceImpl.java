package com.mujinyu.shopping_admin_service.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_admin_service.mapper.PermissionMapper;
import com.mujinyu.shopping_common.pojo.Permission;
import com.mujinyu.shopping_common.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@DubboService
@Transactional
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public void add(Permission permission) {
        permissionMapper.insert(permission);
    }

    @Override
    public void update(Permission permission) {
        permissionMapper.updateById(permission);

    }

    @Override
    public void delete(Long id) {
        // 1、删除权限
        permissionMapper.deleteById(id);
        // 2、删除角色权限表中相关数据
        permissionMapper.deletePermissionAllRole(id);
    }

    @Override
    public Permission findById(Long id) {
        return permissionMapper.selectById(id);
    }

    @Override
    public Page<Permission> search(int page, int size) {
        return permissionMapper.selectPage(new Page<>(page, size), null);
    }

    @Override
    public List<Permission> findAll() {
        return permissionMapper.selectList(null);
    }
}

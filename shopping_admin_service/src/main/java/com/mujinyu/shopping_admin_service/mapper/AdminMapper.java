package com.mujinyu.shopping_admin_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.Admin;
// 必须是 MyBatis 的 @Param，Spring Data 的 @Param 在 MyBatis 里不认，XML 的 #{aid} 会找不到参数
import com.mujinyu.shopping_common.pojo.Permission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AdminMapper extends BaseMapper<Admin> {
    // 删除管理员的所有角色
    void deleteAdminAllRole(Long aid);

    // 根据id查询管理员，包括角色和权限
    Admin findById(Long id);

    /**
     * 给管理员添加角色的方法
     *
     * @param aid 管理员ID，用于标识需要添加角色的管理员
     * @param rid 角色ID，用于标识要添加给管理员的角色
     */
    // 给管理员添加角色
    void addRoleToAdmin(@Param ("aid") Long aid, @Param ("rid") Long rid);

    // 根据管理员查询权限
    List<Permission> findAllPermission(String username);
}

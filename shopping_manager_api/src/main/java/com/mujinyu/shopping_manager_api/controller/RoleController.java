package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Role;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.RoleService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台角色管理
 */
@RestController
@RequestMapping ("/role")
public class RoleController {
    @DubboReference
    private RoleService roleService;

    /**
     * 新增角色
     *
     * @param role 角色对象
     * @return 结果
     */
    @PostMapping ("/add")
    public BaseResult add(@RequestBody Role role) {
        roleService.add(role);
        return BaseResult.ok("新增角色成功");
    }

    /**
     * 修改角色
     *
     * @param role 角色对象
     * @return 结果
     */
    @PutMapping ("/update")
    public BaseResult update(@RequestBody Role role) {
        roleService.update(role);
        return BaseResult.ok("更新角色成功");
    }

    /**
     * 删除角色
     *
     * @param rid 角色ID
     * @return 结果
     */
    @DeleteMapping ("/delete")
    public BaseResult delete(@RequestParam ("rid") Long rid) {
        roleService.delete(rid);
        return BaseResult.ok("删除角色成功");
    }

    /**
     * 根据ID查询角色
     *
     * @param rid 角色ID
     * @return 角色对象
     */
    @GetMapping ("/findById")
    public BaseResult<Role> findById(@RequestParam ("rid") Long rid) {
        return BaseResult.ok(roleService.findById(rid));
    }

    /**
     * 分页查询角色
     *
     * @param page 页码
     * @param size 每页大小
     * @return 角色对象集合
     */
    @GetMapping ("/search")
    @PreAuthorize ("hasAnyAuthority(\"/role/search\")")
    public BaseResult<Page<Role>> search(@RequestParam ("page") int page, @RequestParam ("size") int size) {
        return BaseResult.ok(roleService.search(page, size));
    }

    /**
     * 查询所有角色
     *
     * @return 角色对象集合
     */
    @GetMapping ("/findAll")
    public BaseResult<List<Role>> findAll() {
        return BaseResult.ok(roleService.findAll());
    }

    /**
     * 修改角色权限
     *
     * @param rid  角色ID
     * @param pids 权限ID数组
     * @return 结果
     */
    @PutMapping ("/updatePermissionToRole")
    public BaseResult updatePermissionToRole(@RequestParam ("rid") Long rid, @RequestParam ("pids") Long[] pids) {
        roleService.updatePermissionToRole(rid, pids);
        return BaseResult.ok("更新角色权限成功");
    }
}

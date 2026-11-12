package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Permission;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.PermissionService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permission")
public class PermissionController {
    @DubboReference
    private PermissionService permissionService;

    /**
     * 新增权限
     * @param permission 权限对象
     * @return 执行结果
     */
    @PostMapping("/add")
    public BaseResult add(@RequestBody Permission permission) {
        permissionService.add(permission);
        return BaseResult.ok("新增权限对象成功");
    }

    /**
     * 更新权限
     * @param permission 权限对象
     * @return 执行结果
     */
    @PutMapping("/update")
    public BaseResult update(@RequestBody Permission permission) {
        permissionService.update(permission);
        return BaseResult.ok("更新权限对象成功");
    }

    /**
     * 删除权限
      * @param pid 权限id
      * @return 执行结果
     */
    @DeleteMapping("/delete")
    public BaseResult delete(@RequestParam("pid") Long pid) {
        permissionService.delete(pid);
        return BaseResult.ok("删除权限对象成功");
    }

    /**
     * 根据id查询权限
     * @param pid 权限id
     * @return 权限对象
     */
    @GetMapping("/findById")
    public BaseResult<Permission> findById(@RequestParam("pid") Long pid) {
        return BaseResult.ok(permissionService.findById(pid));
    }

    /**
     * 分页查询权限
     * @param page 页码
     * @param size 每页大小
     * @return 权限对象列表
     */
    @GetMapping("/search")
    public BaseResult<Page<Permission>> search(int page, int size) {
        return BaseResult.ok(permissionService.search(page, size));
    }

    /**
     * 查询所有权限
     * @return 权限对象列表
     */
    @GetMapping("/findAll")
    public BaseResult<List<Permission>> findAll() {
        return BaseResult.ok(permissionService.findAll());
    }
}

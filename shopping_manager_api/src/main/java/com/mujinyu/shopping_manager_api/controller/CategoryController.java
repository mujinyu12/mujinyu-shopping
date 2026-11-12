package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Category;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.CategoryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping ("/category")
public class CategoryController {
    @DubboReference
    private CategoryService categoryService;

    /**
     * 分页查询广告
     *
     * @param page 页码
     * @param size 每页大小
     * @return 广告分页列表
     */
    @GetMapping ("/search")
    public BaseResult<Page<Category>> search(@RequestParam ("page") int page, @RequestParam ("size") int size) {
        return BaseResult.ok(categoryService.search(page, size));
    }

    /**
     * 新增广告
     *
     * @param category 广告对象
     * @return 新增结果
     */
    @PostMapping ("/add")
    public BaseResult add(@RequestBody Category category) {
        categoryService.add(category);
        return BaseResult.ok("新增广告成功");
    }

    /**
     * 修改广告
     *
     * @param category 广告对象
     * @return 修改结果
     */
    @PutMapping ("/update")
    public BaseResult update(@RequestBody Category category) {
        categoryService.update(category);
        return BaseResult.ok("修改广告成功");
    }

    /**
     * 修改广告状态
     *
     * @param id     广告ID
     * @param status 状态 0代表未启用，1代表启用
     * @return 修改结果
     */
    @PutMapping ("/updateStatus")
    public BaseResult updateStatus(@RequestParam ("id") Long id, @RequestParam ("status") Integer status) {
        categoryService.updateStatus(id, status);
        return BaseResult.ok("修改广告状态成功");
    }

    /**
     * 根据ID查询广告
     *
     * @param id 广告ID
     * @return 广告对象
     */
    @GetMapping ("/findById")
    public BaseResult<Category> findById(@RequestParam ("id") Long id) {
        return BaseResult.ok(categoryService.findById(id));
    }

    /**
     * 根据ID删除广告
     *
     * @param ids 广告ID数组
     * @return 删除结果
     */
    @DeleteMapping ("/delete")
    public BaseResult delete(@RequestParam ("ids") Long[] ids) {
        categoryService.delete(ids);
        return BaseResult.ok("删除广告成功");
    }
}
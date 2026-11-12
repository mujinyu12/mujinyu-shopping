package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Brand;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.BrandService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping ("/brand")
public class BrandController {

    // 远程注入
    @DubboReference
    private BrandService brandService;

    /**
     * 根据id查询品牌
     *
     * @param id 品牌id
     * @return 品牌对象
     */
    @GetMapping ("/findById")
    public BaseResult<Brand> findById(@RequestParam ("id") Long id) {
        return BaseResult.ok(brandService.findById(id));
    }

    /**
     * 查询所有品牌
     *
     * @return 品牌对象列表
     */
    @GetMapping ("/all")
    public BaseResult<List<Brand>> findAll() {
        return BaseResult.ok(brandService.findAll());
    }

    /**
     * 新增品牌
     *
     * @param brand 品牌对象
     * @return 执行结果
     */
    @PostMapping ("/add")
    public BaseResult add(@RequestBody Brand brand) {
        brandService.add(brand);
        return BaseResult.ok("新增品牌成功");
    }

    /**
     * 更新品牌
     *
     * @param brand 品牌对象
     * @return 执行结果
     */
    @PutMapping ("/update")
    public BaseResult update(@RequestBody Brand brand) {
        brandService.update(brand);
        return BaseResult.ok("更新品牌成功");
    }

    /**
     * 删除品牌
     *
     * @param id 品牌id
     * @return 执行结果
     */
    @DeleteMapping ("/delete")
    public BaseResult delete(@RequestParam ("id") Long id) {
        brandService.delete(id);
        return BaseResult.ok("删除品牌成功");
    }

    /**
     * 根据条件分页查询品牌
     *
     * @param brand 品牌对象
     * @param page  页码
     * @param size  每页大小
     * @return 品牌对象列表
     */
    @GetMapping ("/search")
    // GET 没有请求体，条件对象不能加 @RequestBody，否则会抛 Required request body is missing
    // 不加注解时 Spring MVC 按 @ModelAttribute 语义，把同名查询参数（如 name）绑定到 brand 上
    public BaseResult<Page<Brand>> search(Brand brand, @RequestParam ("page") Integer page, @RequestParam ("size") Integer size) {
        return BaseResult.ok(brandService.search(brand, page, size));
    }
}

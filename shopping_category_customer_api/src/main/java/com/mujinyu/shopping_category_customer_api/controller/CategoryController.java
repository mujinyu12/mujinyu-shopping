package com.mujinyu.shopping_category_customer_api.controller;

import com.mujinyu.shopping_common.pojo.Category;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.CategoryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 广告
 */
@RestController
@RequestMapping("/user/category")
public class CategoryController {
    @DubboReference
    private CategoryService categoryService;

    /**
     * 查询所有广告
     * @return 广告列表
     */
    @GetMapping ("/all")
    public BaseResult<List<Category>> findAll(){
        return BaseResult.ok(categoryService.findAll());
    }
}

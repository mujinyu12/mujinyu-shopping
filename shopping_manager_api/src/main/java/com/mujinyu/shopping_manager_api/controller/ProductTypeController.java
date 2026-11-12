package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.ProductType;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.ProductTypeService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品类型控制器
 */
@RestController
@RequestMapping ("/productType")
public class ProductTypeController {
    @DubboReference
    private ProductTypeService productTypeService;

    /**
     * 添加商品类型
     *
     * @param productType 商品类型对象
     * @return 执行结果
     */
    @PostMapping ("/add")
    public BaseResult add(@RequestBody ProductType productType) {
        productTypeService.add(productType);
        return BaseResult.ok("添加商品类型成功");
    }

    /**
     * 修改商品类型
     *
     * @param productType 商品类型对象
     * @return 执行结果
     */
    @PutMapping ("/update")
    public BaseResult update(@RequestBody ProductType productType) {
        productTypeService.update(productType);
        return BaseResult.ok("修改商品类型成功");
    }

    /**
     * 删除商品类型
     *
     * @param id 商品类型id
     * @return 执行结果
     */
    @DeleteMapping ("/delete")
    public BaseResult delete(@RequestParam ("id") Long id) {
        productTypeService.delete(id);
        return BaseResult.ok("删除商品类型成功");
    }

    /**
     * 根据id查询商品类型
     *
     * @param id 商品类型id
     * @return 商品类型对象
     */
    @GetMapping ("/findById")
    public BaseResult<ProductType> findById(@RequestParam ("id") Long id) {
        return BaseResult.ok(productTypeService.findById(id));
    }

    /**
     * 根据条件分页查询商品类型
     *
     * @param productType 商品类型对象
     * @param page        页码
     * @param size        每页大小
     * @return 商品类型对象
     */
    @GetMapping ("/search")
    public BaseResult<Page<ProductType>> search(ProductType productType, @RequestParam ("page") int page, @RequestParam ("size") int size) {
        return BaseResult.ok(productTypeService.search(productType, page, size));
    }

    /**
     * 查询所有商品类型
     *
     * @param productType 商品类型对象
     * @return 商品类型对象列表
     */
    @GetMapping ("/findProductType")
    public BaseResult<List<ProductType>> findProductType(ProductType productType) {
        return BaseResult.ok(productTypeService.findProductType(productType));
    }

    /**
     * 根据父级id查询商品类型
     *
     * @param parentId 父级id
      * @return 商品类型对象列表
     */
    @GetMapping ("/findByParentId")
    public BaseResult<List<ProductType>> findByParentId(@RequestParam ("parentId") Long parentId) {
        // 构建查询条件对象
        ProductType productType = new ProductType();
        productType.setParentId(parentId);
        // 查询
        return BaseResult.ok(productTypeService.findProductType(productType));
    }
}

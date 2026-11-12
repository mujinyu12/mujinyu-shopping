package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Specification;
import com.mujinyu.shopping_common.pojo.SpecificationOptions;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.SpecificationService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping ("/specification")
public class SpecificationController {
    @DubboReference
    private SpecificationService specificationService;

    /**
     * 添加商品规格
     *
     * @param specification 商品规格
     * @return 添加结果
     */
    @PostMapping ("/add")
    public BaseResult add(@RequestBody Specification specification) {
        specificationService.add(specification);
        return BaseResult.ok("添加商品规格成功");
    }

    /**
     * 修改商品规格
     *
     * @param specification 商品规格
     * @return 修改结果
     */
    @PutMapping ("/update")
    public BaseResult update(@RequestBody Specification specification) {
        specificationService.update(specification);
        return BaseResult.ok("修改商品规格成功");
    }

    /**
     * 删除商品规格
     *
     * @param ids 商品规格ID数组
     * @return 删除结果
     */
    // DELETE 请求不带请求体，批量ID必须通过查询参数传递（?ids=1&ids=2 或 ?ids=1,2）
    // 写成 @RequestBody 时客户端不带 body 会抛 Required request body is missing
    @DeleteMapping ("/delete")
    public BaseResult delete(@RequestParam ("ids") Long[] ids) {
        specificationService.delete(ids);
        return BaseResult.ok("删除商品规格成功");
    }

    /**
     * 根据ID查询商品规格
     *
     * @param id 商品规格ID
     * @return 商品规格
     */
    @GetMapping ("/findById")
    public BaseResult<Specification> findById(@RequestParam ("id") Long id) {
        Specification specification = specificationService.findById(id);
        return BaseResult.ok(specification);
    }

    /**
     * 分页查询商品规格
     *
     * @param page 当前页
     * @param size 每页大小
     * @return 商品规格分页结果
     */
    @GetMapping ("/search")
    public BaseResult<Page<Specification>> search(@RequestParam ("page") Integer page, @RequestParam ("size") Integer size) {
        Page<Specification> specificationPage = specificationService.search(page, size);
        return BaseResult.ok(specificationPage);
    }

    /**
     * 根据商品类型ID查询商品规格
     *
     * @param id 商品类型ID
     * @return 商品规格列表
     */
    @GetMapping ("/findByProductTypeId")
    public BaseResult<List<Specification>> findByProductTypeId(@RequestParam ("id") Long id) {
        List<Specification> specifications = specificationService.findByProductTypeId(id);
        return BaseResult.ok(specifications);
    }

    /**
     * 添加商品规格选项
     *
     * @param specificationOptions 商品规格选项
     * @return 添加结果
     */
    @PostMapping ("/addOption")
    public BaseResult addOption(@RequestBody SpecificationOptions specificationOptions) {
        specificationService.addOption(specificationOptions);
        return BaseResult.ok("添加商品规格选项成功");
    }

    /**
     * 删除商品规格选项
     *
     * @param ids 商品规格选项ID数组
     * @return 删除结果
     */
    // 同上：批量删除走查询参数，不使用 @RequestBody
    @DeleteMapping ("/deleteOption")
    public BaseResult deleteOption(@RequestParam ("ids") Long[] ids) {
        specificationService.deleteOption(ids);
        return BaseResult.ok("删除商品规格选项成功");
    }
}

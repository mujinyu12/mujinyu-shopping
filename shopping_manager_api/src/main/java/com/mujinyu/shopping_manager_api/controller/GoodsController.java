package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Goods;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.GoodsService;
import lombok.Getter;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/goods")
public class GoodsController {
    @DubboReference
    private GoodsService goodsService;

    /**
     * 新增商品
     * @param goods 商品对象
     * @return 执行结果
     */
    @PostMapping("/add")
    public BaseResult add(@RequestBody Goods goods) {
        goodsService.add(goods);
        return BaseResult.ok("新增商品成功");
    }

    /**
     * 修改商品
     * @param goods 商品对象
     * @return 执行结果
     */
    @PutMapping("/update")
    public BaseResult update(@RequestBody Goods goods) {
        goodsService.update(goods);
        return BaseResult.ok("修改商品成功");
    }

    /**
     * 商品上/下架
     * @param id 商品id
     * @param isMarketable 是否上架
     * @return 执行结果
     */
    @PutMapping("/putAway")
    public BaseResult putAway(@RequestParam("id") Long id, @RequestParam("isMarketable") Boolean isMarketable) {
        goodsService.putAway(id, isMarketable);
        return BaseResult.ok("商品上/下架成功");
    }

    /**
     * 根据id查询商品详情
     * @param id 商品id
     * @return 商品详情
     */
    @GetMapping("/findById")
    public BaseResult<Goods> findById(@RequestParam("id") Long id) {
        return BaseResult.ok(goodsService.findById(id));
    }

    /**
     * 分页查询
     * @param goods 查询条件对象
     * @param page 页码
     * @param size 每页条数
     * @return 分页查询结果
     */
    @GetMapping("/search")
    public BaseResult<Page<Goods>> search(Goods goods, @RequestParam("page") int page, @RequestParam("size") int size) {
        return BaseResult.ok(goodsService.search(goods, page, size));
    }
}

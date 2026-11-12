package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.SeckillGoodsService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品
 */
@RestController
@RequestMapping("/seckillGoods")
public class SeckillGoodsController {
    @DubboReference
    private SeckillGoodsService seckillGoodsService;

    /**
     * 新增秒杀商品
     * @param seckillGoods 秒杀商品对象
     * @return 执行结果
     */
    @PostMapping("/add")
    public BaseResult add(@RequestBody SeckillGoods seckillGoods) {
        seckillGoodsService.add(seckillGoods);
        return BaseResult.ok("新增秒杀商品成功");
    }

    /**
     * 修改秒杀商品
     * @param seckillGoods 秒杀商品对象
     * @return 执行结果
     */
    @PutMapping("/update")
    public BaseResult update(@RequestBody SeckillGoods seckillGoods) {
        seckillGoodsService.update(seckillGoods);
        return BaseResult.ok("修改秒杀商品成功");
    }

    /**
     * 分页查询秒杀商品
     * @param page 页码
     * @param size 每页大小
     * @return 秒杀商品分页列表
     */
    @GetMapping("/findPage")
    public BaseResult<Page<SeckillGoods>> findPage(@RequestParam int page, @RequestParam int size) {
        return BaseResult.ok(seckillGoodsService.findPage(page, size));
    }
}

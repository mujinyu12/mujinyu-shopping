package com.mujinyu.shopping_common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Goods;
import com.mujinyu.shopping_common.pojo.GoodsDesc;

import java.util.List;

public interface GoodsService {
    // 新增商品
    void add(Goods goods);
    // 修改商品
    void update(Goods goods);
    // 根据id查询商品详情
    Goods findById(Long id);
    // 上架/下架商品
    void putAway(Long id, Boolean isMarketable);
    // 分页查询商品
    Page<Goods> search(Goods goods, int page, int size);
    // 查询所有商品详情
    List<GoodsDesc> findAll();
    // 查询商品详情
    GoodsDesc findDesc(Long id);
    // 查询在售商品详情（下架商品返回null），供顾客端商品详情使用
    GoodsDesc findMarketableDesc(Long id);
    // 批量查询商品（供购物车等场景刷新快照，返回在售+下架，由调用方判断 isMarketable）
    List<Goods> findByIds(List<Long> ids);
}

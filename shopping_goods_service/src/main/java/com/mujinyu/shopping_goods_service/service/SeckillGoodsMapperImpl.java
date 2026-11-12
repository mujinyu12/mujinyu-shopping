package com.mujinyu.shopping_goods_service.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import com.mujinyu.shopping_common.service.SeckillGoodsService;
import com.mujinyu.shopping_goods_service.mapper.SeckillGoodsMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

@DubboService
@Transactional
@RequiredArgsConstructor
public class SeckillGoodsMapperImpl implements SeckillGoodsService {

    private final SeckillGoodsMapper seckillGoodsMapper;

    @Override
    public void add(SeckillGoods seckillGoods) {
        seckillGoodsMapper.insert(seckillGoods);
    }

    @Override
    public void update(SeckillGoods seckillGoods) {
        seckillGoodsMapper.updateById(seckillGoods);
    }

    @Override
    public Page<SeckillGoods> findPage(int page, int size) {
        return seckillGoodsMapper.selectPage(new Page<>(page, size), null);
    }
}

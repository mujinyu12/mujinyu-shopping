package com.mujinyu.shopping_goods_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.*;
import com.mujinyu.shopping_common.service.GoodsService;
import com.mujinyu.shopping_goods_service.mapper.GoodsImageMapper;
import com.mujinyu.shopping_goods_service.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@DubboService
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GoodsServiceImpl implements GoodsService {

    private final GoodsMapper goodsMapper;

    private final GoodsImageMapper goodsImageMapper;

    private final RocketMQTemplate rocketMQTemplate; // RocketMQ工具类

    // 同步商品数据主题
    private static final String SYNC_GOODS_QUEUE = "sync_goods_queue";
    // 删除商品数据主题
    private static final String DEL_GOOD_QUEUE = "del_goods_queue";
    // 同步商品到购物车主题
    private static final String SYNC_CART_QUEUE = "sync_cart_queue";
    // 删除商品到购物车主题
    private static final String DEL_CART_QUEUE = "del_cart_queue";

    @Override
    public void add(Goods goods) {
        // 插入商品数据
        goodsMapper.insert(goods);
        // 插入图片数据
        Long goodsId = goods.getId(); // 获取商品主键
        List<GoodsImage> images = goods.getImages(); // 商品图片集合
        for (GoodsImage image : images) {
            image.setGoodsId(goodsId); // 给图片设置商品ID
            goodsImageMapper.insert(image); // 插入图片数据
        }
        // 插入商品——规格项数据
        // 1、获取商品的规格数据
        List<Specification> specifications = goods.getSpecifications();
        // 2、获取规格项
        List<SpecificationOption> options = new ArrayList<>();
        // 3、遍历规格，获取规格中的所有规格项
        for (Specification specification : specifications) {
            options.addAll(specification.getSpecificationOptions());
        }
        // 4、遍历规格项，插入商品_规格项数据
        for (SpecificationOption option : options) {
            goodsMapper.addGoodsSpecificationOption(goodsId, option.getId());
        }
        // 5、将商品数据同步到ES中
        GoodsDesc goodsDesc = goodsMapper.findDesc(goodsId);
        if (goodsDesc == null) {
            log.warn("商品【{}】详情为空，跳过ES同步", goodsId);
        } else {
            rocketMQTemplate.syncSend(SYNC_GOODS_QUEUE, goodsDesc);
        }
    }

    @Override
    public void update(Goods goods) {
        // 删除旧图片数据
        Long goodsId = goods.getId(); // 获取商品主键
        QueryWrapper<GoodsImage> queryWrapper = new QueryWrapper();
        queryWrapper.eq("goodsId", goodsId);
        goodsImageMapper.delete(queryWrapper);
        // 删除旧规格数据
        goodsMapper.deleteGoodsSpecificationOption(goodsId);
        // 修改商品数据
        goodsMapper.updateById(goods);
        // 插入图片数据
        List<GoodsImage> images = goods.getImages(); // 商品图片集合
        for (GoodsImage image : images) {
            image.setGoodsId(goodsId); // 给图片设置商品ID
            goodsImageMapper.insert(image); // 插入图片数据
        }
        // 插入商品——规格项数据
        // 1、获取商品的规格数据
        List<Specification> specifications = goods.getSpecifications();
        // 2、获取规格项
        List<SpecificationOption> options = new ArrayList<>();
        // 3、遍历规格，获取规格中的所有规格项
        for (Specification specification : specifications) {
            options.addAll(specification.getSpecificationOptions());
        }
        // 4、遍历规格项，插入商品_规格项数据
        for (SpecificationOption option : options) {
            goodsMapper.addGoodsSpecificationOption(goodsId, option.getId());
        }
        // 5、将商品数据同步到ES中
        // update 之后商品可能处于下架状态，此时 findDesc 仍能查到详情，由消费端按 isMarketable 决定写入还是删除
        GoodsDesc goodsDesc = goodsMapper.findDesc(goodsId);
        if (goodsDesc == null) {
            log.warn("商品【{}】详情为空，跳过ES同步", goodsId);
        } else {
            rocketMQTemplate.syncSend(SYNC_GOODS_QUEUE, goodsDesc);
        }
        // 6、将商品数据同步到购物车当中
        CartGoods cartGoods = new CartGoods();
        cartGoods.setGoodId(goods.getId());
        cartGoods.setGoodsName(goods.getGoodsName());
        cartGoods.setHeaderPic(goods.getHeaderPic());
        cartGoods.setPrice(goods.getPrice());
        rocketMQTemplate.syncSend(SYNC_CART_QUEUE, cartGoods);
    }

    @Override
    public Goods findById(Long id) {
        return goodsMapper.findById(id);
    }

    @Override
    public void putAway(Long id, Boolean isMarketable) {
        goodsMapper.putAway(id, isMarketable);
        // 上架时数据同步到ES，下架时删除ES数据（缺陷 21）
        // 用 Boolean.TRUE.equals 判断，避免 isMarketable 为 null 时自动拆箱抛 NPE
        if (Boolean.TRUE.equals(isMarketable)) {
            // 将商品数据同步到ES中
            GoodsDesc goodsDesc = goodsMapper.findDesc(id);
            if (goodsDesc == null) {
                log.warn("商品【{}】详情为空，跳过ES同步", id);
            } else {
                rocketMQTemplate.syncSend(SYNC_GOODS_QUEUE, goodsDesc);
            }
        } else {
            // 下架：先删ES文档，再清理购物车中的该商品
            rocketMQTemplate.syncSend(DEL_GOOD_QUEUE, id);
            // 将购物车的商品删除
            rocketMQTemplate.syncSend(DEL_CART_QUEUE, id);
        }
    }

    @Override
    public Page<Goods> search(Goods goods, int page, int size) {
        QueryWrapper<Goods> queryWrapper = new QueryWrapper<>();
        // 判断商品名不为空
        if (goods != null && StringUtils.hasText(goods.getGoodsName())) {
            queryWrapper.like("goodsName", goods.getGoodsName());
        }
        return goodsMapper.selectPage(new Page<>(page, size), queryWrapper);
    }

    @Override
    public List<GoodsDesc> findAll() {
        return goodsMapper.findAll();
    }

    @Override
    public GoodsDesc findDesc(Long id) {
        return goodsMapper.findDesc(id);
    }

    @Override
    public GoodsDesc findMarketableDesc(Long id) {
        // 顾客端商品详情：只返回上架商品，下架商品返回 null
        return goodsMapper.findMarketableDesc(id);
    }

    @Override
    public List<Goods> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 过滤null并去重：null会拼进IN条件导致SQL无意义，重复ID会拉长IN列表，影响SQL性能
        List<Long> validIds = ids.stream()
                .filter(Objects::nonNull) // 过滤掉 null
                .distinct() // 去重
                .toList();
        return goodsMapper.selectByIds(validIds);
    }
}

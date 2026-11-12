package com.mujinyu.shopping_goods_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Brand;
import com.mujinyu.shopping_common.service.BrandService;
import com.mujinyu.shopping_goods_service.mapper.BrandMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@DubboService
@RequiredArgsConstructor
@Transactional
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;

    /**
     * 根据id查询品牌
     * @param id 品牌id
     * @return 品牌对象
     */
    @Override
    public Brand findById(Long id) {
        return brandMapper.selectById(id);
    }

    @Override
    public List<Brand> findAll() {
        return brandMapper.selectList(null);
    }

    @Override
    public void add(Brand brand) {
        brandMapper.insert(brand);
    }

    @Override
    public void update(Brand brand) {
        brandMapper.updateById(brand);
    }

    @Override
    public void delete(Long id) {
        brandMapper.deleteById(id);
    }

    @Override
    public Page<Brand> search(Brand brand, int page, int size) {
        QueryWrapper<Brand> queryWrapper = new QueryWrapper<>();
        // 判断品牌名是否为空
        if (brand != null && StringUtils.hasText(brand.getName())) {
            queryWrapper.like("name", brand.getName());
        }
        return brandMapper.selectPage(new Page<>(page, size), queryWrapper);
    }
}

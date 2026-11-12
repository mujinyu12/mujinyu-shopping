package com.mujinyu.shopping_order_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mujinyu.shopping_common.pojo.Address;
import com.mujinyu.shopping_common.pojo.Area;
import com.mujinyu.shopping_common.pojo.City;
import com.mujinyu.shopping_common.pojo.Province;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.AddressService;
import com.mujinyu.shopping_order_service.mapper.AddressMapper;
import com.mujinyu.shopping_order_service.mapper.AreaMapper;
import com.mujinyu.shopping_order_service.mapper.CityMapper;
import com.mujinyu.shopping_order_service.mapper.ProvinceMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@DubboService
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    private final AddressMapper addressMapper;
    private final ProvinceMapper provinceMapper;
    private final CityMapper cityMapper;
    private final AreaMapper areaMapper;

    @Override
    public List<Province> findAllProvince() {
        return provinceMapper.selectList(null); // 查询所有省份
    }

    @Override
    public List<City> findCityByProvince(Long provinceId) {
        QueryWrapper<City> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("provinceId", provinceId);
        return cityMapper.selectList(queryWrapper); // 根据省份ID查询城市
    }

    @Override
    public List<Area> findAreaByCity(Long cityId) {
        QueryWrapper<Area> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("cityId", cityId);
        return areaMapper.selectList(queryWrapper); // 根据城市ID查询区域
    }

    @Override
    public void add(Address address) {
        addressMapper.insert(address);
    }

    @Override
    public void update(Address address) {
        addressMapper.updateById(address);
    }

    @Override
    public Address findById(Long id) {
        return addressMapper.selectById(id);
    }

    @Override
    public Address findOwned(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        Address address = addressMapper.selectById(id);
        if (address == null) {
            throw new BusException(CodeEnum.NOT_FOUND); // 地址不存在
        }
        if (!userId.equals(address.getUserId())) {
            throw new BusException(CodeEnum.PERMISSION_DENIED); // 不是自己的地址，禁止查看
        }
        return address;
    }

    @Override
    public void delete(Long id) {
        addressMapper.deleteById(id);

    }

    @Override
    public void deleteOwned(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 删除条件带上 userId，从SQL层面杜绝删到别人的地址
        QueryWrapper<Address> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id).eq("userId", userId);
        if (addressMapper.delete(queryWrapper) == 0) {
            throw new BusException(CodeEnum.PERMISSION_DENIED); // 影响0行：地址不存在或不属于该用户
        }
    }

    @Override
    public List<Address> findByUser(Long userId) {
        QueryWrapper<Address> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        return addressMapper.selectList(queryWrapper); // 根据用户ID查询地址
    }
}

package com.mujinyu.shopping_common.service;

import com.mujinyu.shopping_common.pojo.Address;
import com.mujinyu.shopping_common.pojo.Area;
import com.mujinyu.shopping_common.pojo.City;
import com.mujinyu.shopping_common.pojo.Province;

import java.util.List;

public interface AddressService {
  // 查询所有省份
  List<Province> findAllProvince();


  // 查询省份下的城市
  List<City> findCityByProvince(Long provinceId);


  // 查询城市下的区县
  List<Area> findAreaByCity(Long cityId);


  // 增加地址
  void add(Address address);


  // 修改地址
  void update(Address address);


  // 根据id获取地址
  Address findById(Long id);


  /**
   * 根据id查询地址并校验归属：不存在抛 404，不属于该用户抛 403
   *
   * @param id     地址id
   * @param userId 当前登录用户id
   * @return 地址对象
   */
  Address findOwned(Long id, Long userId);


  // 删除地址
  void delete(Long id);


  /**
   * 根据id + 用户id 删除地址，影响 0 行说明地址不存在或不属于该用户，抛 403
   *
   * @param id     地址id
   * @param userId 当前登录用户id
   */
  void deleteOwned(Long id, Long userId);


  // 查询登录用户的所有地址
  List<Address> findByUser(Long userId);
}

package com.mujinyu.shopping_order_customer_api.controller;

import com.mujinyu.shopping_common.pojo.Address;
import com.mujinyu.shopping_common.pojo.Area;
import com.mujinyu.shopping_common.pojo.City;
import com.mujinyu.shopping_common.pojo.Province;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.AddressService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收获地址
 */
@RestController
@RequestMapping("/user/address")
public class AddressController {
    @DubboReference
    private AddressService addressService;

    /**
     * 查询所有省份
     * @return 所有省份集合
     */
    @GetMapping("/findAllProvince")
    public BaseResult<List<Province>> findAllProvince(){
        List<Province> allProvince = addressService.findAllProvince();
        return BaseResult.ok(allProvince);
    }

    /**
     * 根据省份查询其下所有城市
     * @param provinceId 省份id
     * @return 所有城市集合
     */
    @GetMapping("/findCityByProvince")
    public BaseResult<List<City>> findCityByProvince(@RequestParam("provinceId") Long provinceId){
        List<City> cities = addressService.findCityByProvince(provinceId);
        return BaseResult.ok(cities);
    }

    /**
     * 根据城市查询其下所有区县
     * @param cityId 城市id
     * @return 所有区县集合
     */
    @GetMapping("/findAreaByCity")
    public BaseResult<List<Area>> findAreaByCity(@RequestParam("cityId") Long cityId){
        List<Area> areas = addressService.findAreaByCity(cityId);
        return BaseResult.ok(areas);
    }

    /**
     * 新增地址
     * @param userId 用户id
     * @param address 地址对象
     * @return 执行结果
     */
    @PostMapping("/add")
    public BaseResult add(@LoginUser Long userId, @RequestBody Address address){
        address.setUserId(userId);
        addressService.add(address);
        return BaseResult.ok("新增地址成功");
    }

    /**
     * 修改地址
     * @param userId 用户id
     * @param address 地址对象
     * @return 执行结果
     */
    @PutMapping("/update")
    public BaseResult update(@LoginUser Long userId, @RequestBody Address address) {
        if (address.getId() == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 必须带上地址id
        }
        // 只能改自己的地址：先按id+userId校验归属，不存在/越权直接抛异常
        addressService.findOwned(address.getId(), userId);
        // userId 一律以登录态为准，禁止通过请求体把地址改到别人名下
        address.setUserId(userId);
        addressService.update(address);
        return BaseResult.ok("修改地址成功");
    }

    /**
     * 根据id查询地址（只能查自己的地址）
     * @param userId 当前登录用户id
     * @param id 地址id
     * @return 查询结果
     */
    @GetMapping("/findById")
    public BaseResult<Address> findById(@LoginUser Long userId, @RequestParam("id") Long id){
        return BaseResult.ok(addressService.findOwned(id, userId));
    }

    /**
     * 根据id删除地址（只能删自己的地址）
     * @param userId 当前登录用户id
     * @param id 地址id
     * @return 执行结果
     */
    @DeleteMapping("/delete")
    public BaseResult delete(@LoginUser Long userId, @RequestParam("id") Long id){
        addressService.deleteOwned(id, userId);
        return BaseResult.ok("删除地址成功");
    }

    /**
     * 查询登录用户的所有地址
     * @param userId 用户Id
     * @return 所有地址集合
     */
    @GetMapping("/findByUser")
    public BaseResult<List<Address>> findByUser(@LoginUser Long userId){
        List<Address> addresses = addressService.findByUser(userId);
        return BaseResult.ok(addresses);
    }
}
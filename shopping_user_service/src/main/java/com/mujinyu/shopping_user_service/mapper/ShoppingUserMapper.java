package com.mujinyu.shopping_user_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.ShoppingUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商城用户 Mapper
 */
@Mapper
public interface ShoppingUserMapper extends BaseMapper<ShoppingUser> {
}

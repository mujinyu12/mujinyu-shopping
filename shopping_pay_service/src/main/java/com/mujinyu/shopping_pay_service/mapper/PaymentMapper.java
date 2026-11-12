package com.mujinyu.shopping_pay_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}

package com.mujinyu.shopping_seckill_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

    /**
     * 扣减秒杀商品库存，库存不足时不更新
     *
     * @param id  秒杀商品id
     * @param num 扣减数量
     * @return 受影响行数，0表示库存不足
     */
    @Update("UPDATE bz_seckill_goods SET stockCount = stockCount - #{num} WHERE id = #{id} AND stockCount >= #{num}")
    int reduceStock(@Param("id") Long id, @Param("num") Integer num);

    /**
     * 回退秒杀商品库存
     * 带上限保护：回补后不能超过该场次的总库存 num，用于兜住重复投递导致的二次加库存
     *
     * @param id  秒杀商品id（调用方需先按 goodsId 查到当前场次的 id：bz_seckill_goods.goodsId 允许重复）
     * @param num 回退数量
     * @return 受影响行数，0 表示已达上限（重复回补）或商品不存在
     */
    @Update("UPDATE bz_seckill_goods SET stockCount = stockCount + #{num} "
            + "WHERE id = #{id} AND stockCount + #{num} <= num")
    int addStock(@Param("id") Long id, @Param("num") Integer num);
}
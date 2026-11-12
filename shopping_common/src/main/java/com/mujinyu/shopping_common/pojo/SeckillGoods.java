package com.mujinyu.shopping_common.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀商品
 */
@Data
public class SeckillGoods implements Serializable{
    @TableId
    private Long id; // 秒杀商品Id
    private Long goodsId; // 对应的商品Id
    private String title; // 标题
    private String introduction; // 商品描述
    private String headerPic; // 商品图片
    private BigDecimal price; // 原价
    private BigDecimal costPrice; // 秒杀价
    // Jackson 默认按 UTC 解析日期，必须显式指定时区，否则前端传的 "16:00" 会被当成 UTC，
    // 经过 JDBC 按 Asia/Shanghai 写库后就变成次日 00:00（相差 8 小时），活动会晚 8 小时才开始
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime; // 开始时间
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime; // 结束时间
    private Integer num; // 秒杀商品数
    private Integer stockCount; // 剩余库存数
}
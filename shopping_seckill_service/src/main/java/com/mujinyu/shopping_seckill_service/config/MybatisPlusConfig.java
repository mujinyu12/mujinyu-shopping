package com.mujinyu.shopping_seckill_service.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：显式指定数据库类型，避免每次从连接推断方言
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(1000L); // 单页最大条数，防止 pageSize 过大拖垮数据库
        pagination.setOverflow(false); // 页码越界时返回空列表，而不是回到第一页
        pagination.setOptimizeJoin(true); // 生成 count 语句时去掉无用的 order by / left join

        // 存在多个内部拦截器时，分页必须最后添加
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}

package com.mujinyu.shopping_custcare_service.config;

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

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(1000L);   // 单页上限，防止 pageSize 传个 100000
        pagination.setOverflow(false);   // 页码越界返回空列表，而不是回到第一页
        pagination.setOptimizeJoin(true); // count 语句去掉无用 order by / left join

        // 多个内部拦截器时，分页必须最后加
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
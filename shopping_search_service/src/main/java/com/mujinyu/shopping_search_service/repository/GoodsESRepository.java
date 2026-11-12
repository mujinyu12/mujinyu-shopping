package com.mujinyu.shopping_search_service.repository;

import com.mujinyu.shopping_common.pojo.GoodsES;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoodsESRepository extends ElasticsearchRepository<GoodsES, Long> {
}

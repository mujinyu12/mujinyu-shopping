package com.mujinyu.shopping_category_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_category_service.mapper.CategoryMapper;
import com.mujinyu.shopping_common.pojo.Category;
import com.mujinyu.shopping_common.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@DubboService
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    /** 分类缓存 key（Redis list） */
    private static final String CATEGORY_KEY = "categories";

    /** 刷新时使用的临时 key 前缀，写入完成后通过 RENAME 原子替换正式 key */
    private static final String TEMP_KEY_PREFIX = CATEGORY_KEY + ":refreshing:";

    /** 缓存有效期：即使刷新逻辑失效，也能在 1 小时后自动回源数据库 */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final CategoryMapper categoryMapper;

    private final RedisTemplate redisTemplate;

    @Override
    public void add(Category category) {
        categoryMapper.insert(category);
        refreshRedisCategory();
    }

    @Override
    public void update(Category category) {
        categoryMapper.updateById(category);
        refreshRedisCategory();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Category category = categoryMapper.selectById(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
        refreshRedisCategory();
    }

    @Override
    public void delete(Long[] ids) {
        categoryMapper.deleteByIds(Arrays.asList(ids));
        refreshRedisCategory();
    }

    @Override
    public Category findById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public Page<Category> search(int page, int size) {
        return categoryMapper.selectPage(new Page<>(page, size), null);
    }

    @Override
    public List<Category> findAll() {
        // 1、从redis中查询启用的广告
        ListOperations<String, Category> listOperations = redisTemplate.opsForList();
        List<Category> categoryList = listOperations.range(CATEGORY_KEY, 0, -1); // 获取所有分类

        if (categoryList != null && !categoryList.isEmpty()) {
            // 2、如果查到结果，直接返回
            log.debug("从 Redis 获取已启用的分类，共 {} 条", categoryList.size());
            return categoryList;
        }

        // 3、如果redis中没有数据，则从数据库查询所有启用的广告，并同步到redis中
        log.debug("Redis 中没有分类缓存，从数据库加载");
        List<Category> categories = queryEnabledCategories();
        writeCache(categories);
        return categories;
    }

    /**
     * 更新redis中的广告数据
     */
    public void refreshRedisCategory() {
        writeCache(queryEnabledCategories());
    }

    /**
     * 查询所有已启用的分类
     */
    private List<Category> queryEnabledCategories() {
        QueryWrapper<Category> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        return categoryMapper.selectList(queryWrapper);
    }

    /**
     * 原子刷新分类缓存。
     * 不能先 delete 再 leftPushAll：两步之间读请求会看到空列表，然后回源数据库，
     * 并发刷新时还可能把旧数据重新写回去。这里先写临时 key，再用 RENAME 原子替换，
     * 读请求要么看到完整的旧列表，要么看到完整的新列表。
     * Redis list 无法表示“空集合”，所以空结果直接删除正式 key，
     * 下次 findAll 会回源数据库（数据库也为空时重复回源，但不会报错）。
     * 注意：当前部署是单机 Redis；若以后切换 Redis Cluster，RENAME 要求两个 key
     * 落在同一个 slot，需要给正式 key 和临时 key 加相同的 hash tag（如 {categories}）。
     */
    private void writeCache(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            redisTemplate.delete(CATEGORY_KEY); // 如果没有数据，删除正式 key
            return;
        }

        String tempKey = TEMP_KEY_PREFIX + UUID.randomUUID(); // 生成一个临时key
        ListOperations<String, Category> listOperations = redisTemplate.opsForList();
        try {
            // 1、把新数据写到临时 key，此时读请求仍然访问旧的正式 key
            listOperations.leftPushAll(tempKey, categories);
            // 2、设置过期时间；RENAME 后 TTL 会随 key 一起迁移
            redisTemplate.expire(tempKey, CACHE_TTL);
            // 3、原子替换正式 key
            redisTemplate.rename(tempKey, CATEGORY_KEY);
            // 兜底：某些 Redis 客户端/代理在 RENAME 时不保留 TTL，这里再设一次
            redisTemplate.expire(CATEGORY_KEY, CACHE_TTL);
            log.debug("分类缓存刷新成功，共 {} 条", categories.size());
        } catch (RuntimeException e) {
            // 写临时 key 失败或 RENAME 失败时清理残留，避免脏 key 堆积
            redisTemplate.delete(tempKey);
            log.error("分类缓存刷新失败，保留旧缓存", e);
            throw e;
        }
    }
}
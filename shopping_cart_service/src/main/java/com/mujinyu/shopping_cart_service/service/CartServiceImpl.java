package com.mujinyu.shopping_cart_service.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Goods;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.CartService;
import com.mujinyu.shopping_common.service.GoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 购物车服务
 * 购物车里的 price / goodsName / headerPic 只作为「展示快照」，不再作为结算依据；
 * 读取购物车时用商品服务的最新数据覆盖快照。商品变更时只让本地缓存失效，
 * 不再遍历所有用户的购物车。
 */
@DubboService
@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    /** 购物车在 Redis 中的 hash：field = userId，value = List<CartGoods> */
    private static final String CART_HASH_KEY = "cartList";

    /** 商品快照本地缓存：60 秒过期，最多 1 万条，避免热点商品反复查询商品服务 */
    private static final Cache<Long, Goods> GOODS_CACHE = Caffeine.newBuilder()
            .maximumSize(10_000) // 设置缓存的最大容量为10000个条目
            .expireAfterWrite(Duration.ofSeconds(60)) // 设置缓存条目在写入后的60秒过期
            .build();

    private final RedisTemplate redisTemplate;

    @DubboReference(timeout = 2000, retries = 0, check = false)
    private GoodsService goodsService;

    /**
     * 让某个商品的本地快照缓存失效
     * 商品服务改价/改图/下架后，由 MQ 监听器调用；下一次读购物车会重新拉取最新商品。
     *
     * @param goodId 商品 ID
     */
    public static void evictGoodsCache(Long goodId) {
        if (goodId != null) {
            GOODS_CACHE.invalidate(goodId);
        }
    }

    @Override
    public void addCart(Long userId, CartGoods cartGoods) {
        // 参数校验：检查用户ID、购物车商品以及商品ID是否为空
        if (userId == null || cartGoods == null || cartGoods.getGoodId() == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 如果用户ID、商品为空或商品ID为空，抛出参数错误异常
        }
        // 参数校验：检查商品数量是否有效（必须大于0）
        if (cartGoods.getNum() == null || cartGoods.getNum() <= 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);  // 如果商品数量为空或小于等于0，抛出参数错误异常
        }
        // 1、价格/名称/图片一律以商品库为准，不信任前端
        Goods goods = goodsService.findById(cartGoods.getGoodId());
        if (goods == null || !Boolean.TRUE.equals(goods.getIsMarketable())) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 如果商品不存在或商品不可上架，抛出参数错误异常
        }
        cartGoods.setPrice(goods.getPrice()); // 设置商品价格
        cartGoods.setGoodsName(goods.getGoodsName()); // 设置商品名称
        cartGoods.setHeaderPic(goods.getHeaderPic()); // 设置商品图片

        // 2、同商品累加数量（写路径只读原始快照，不做刷新，避免多一次 RPC）
        List<CartGoods> cartList = getRawCartList(userId);
        for (CartGoods goods1 : cartList) {
            if (goods1.getGoodId().equals(cartGoods.getGoodId())) {
                goods1.setNum(goods1.getNum() + cartGoods.getNum());
                // 重复加购时也刷新价格/名称/图片，避免购物车里长期保留旧的商品信息
                goods1.setPrice(cartGoods.getPrice());
                goods1.setGoodsName(cartGoods.getGoodsName());
                goods1.setHeaderPic(cartGoods.getHeaderPic());
                redisTemplate.boundHashOps(CART_HASH_KEY).put(userId, cartList); // 将更新后的购物车列表同步到Redis
                return;
            }
        }
        cartList.add(cartGoods); // 如果购物车中没有该商品，则直接添加到购物车列表中
        redisTemplate.boundHashOps(CART_HASH_KEY).put(userId, cartList); // 将更新后的购物车列表同步到Redis
    }

    @Override
    public void handleCart(Long userId, Long goodId, Integer num) {
        // 参数校验
        if (userId == null || goodId == null || num == null || num <= 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 如果用户ID、商品ID、数量为空或数量小于等于0，抛出参数错误异常
        }
        // 1、获取用户购物车原始快照（写路径不刷新，避免依赖商品服务）
        List<CartGoods> cartList = getRawCartList(userId);
        // 2、遍历列表找到对应商品
        boolean found = false;
        for (CartGoods cartGoods : cartList) {
            if (goodId.equals(cartGoods.getGoodId())) {
                // 3、改变商品数量
                cartGoods.setNum(num);
                found = true;
                break;
            }
        }
        // 4、商品不在购物车中，属于非法请求，抛出异常，避免无效写回
        if (!found) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }

        // 5、将新的购物车列表保存到redis中
        redisTemplate.boundHashOps(CART_HASH_KEY).put(userId, cartList);
    }

    @Override
    public void deleteCartOption(Long userId, Long goodId) {
        // 参数校验
        if (userId == null || goodId == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 如果用户ID、商品ID为空，抛出参数错误异常
        }
        // 1、获取用户购物车原始快照（写路径不刷新）
        List<CartGoods> cartList = getRawCartList(userId);
        // 2、用 removeIf 安全删除，避免 for-each 中直接 remove 的问题
        boolean removed = cartList.removeIf(cartGoods -> goodId.equals(cartGoods.getGoodId()));
        if (removed) {
            // 3、只有在找到并删除商品后，才将新的购物车列表保存到redis中
            redisTemplate.boundHashOps(CART_HASH_KEY).put(userId, cartList);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CartGoods> findCartList(Long userId) {
        // 读路径：先取 Redis 里的快照，再用商品服务的最新数据覆盖
        List<CartGoods> cartList = getRawCartList(userId);
        return refreshByGoods(cartList);
    }

    /**
     * 读取用户购物车的原始快照（不做刷新，供写操作和读路径内部使用）
     */
    @SuppressWarnings("unchecked")
    private List<CartGoods> getRawCartList(Long userId) {
        Object cached = redisTemplate.boundHashOps(CART_HASH_KEY).get(userId);
        if (cached == null) {
            return new ArrayList<>();
        }
        return (List<CartGoods>) cached;
    }

    /**
     * 用商品服务的最新数据覆盖快照；已下架/已删除的商品不再展示（重新上架后会自动回来）
     * @param list  购物车商品列表
     * @return  刷新后的购物车商品列表
     */
    private List<CartGoods> refreshByGoods(List<CartGoods> list) {
        if (list == null || list.isEmpty()) {
            return list; // 如果列表为空或为空集合，直接返回
        }
        // 1、收集ID，未命中本地缓存的才走远程服务
        Map<Long, Goods> goodsMap = new HashMap<>(list.size()); // 创建一个HashMap来存储商品ID和商品数据
        Set<Long> miss = new LinkedHashSet<>(); // 创建一个LinkedHashSet来存储未命中本地缓存的商品ID
        for (CartGoods c : list) {
            Goods g = GOODS_CACHE.getIfPresent(c.getGoodId()); // 从本地缓存中获取商品数据
            if (g != null) {
                goodsMap.put(c.getGoodId(), g); // 如果商品数据存在，则将其添加到goodsMap中
            } else {
                miss.add(c.getGoodId()); // 如果商品数据不存在，则将其添加到miss中
            }
        }
        // 2、一次性批量查，失败就降级返回旧快照
        if (!miss.isEmpty()) {
            try {
                List<Goods> goodsList = goodsService.findByIds(new ArrayList<>(miss)); // 如果有未缓存的商品ID，则调用商品服务批量查询商品信息
                if (goodsList != null) { // 如果查询结果不为空
                    for (Goods g : goodsList) {
                        goodsMap.put(g.getId(), g); // 将商品数据添加到goodsMap中
                        GOODS_CACHE.put(g.getId(), g); // 将商品数据添加到本地缓存中
                    }
                }
            } catch (Exception e) {
                log.warn("刷新购物车商品快照失败，返回旧快照", e);
                return list; // 如果查询失败，则返回旧的购物车商品列表
            }
        }
        // 3、覆盖价格/名称/图片；下架/已删除的直接不返回
        List<CartGoods> result = new ArrayList<>(list.size());
        for (CartGoods c : list) {
            Goods g = goodsMap.get(c.getGoodId()); // 从goodsMap中获取商品数据
            if (g == null || !Boolean.TRUE.equals(g.getIsMarketable())) {
                continue; // 如果商品数据不存在或商品已下架，则跳过该商品
            }
            c.setGoodsName(g.getGoodsName()); // 更新商品名称
            c.setPrice(g.getPrice()); // 更新商品价格
            c.setHeaderPic(g.getHeaderPic()); // 更新商品图片
            result.add(c);
        }
        return result;
    }

    /**
     * 商品变更只让本地缓存失效，由读路径负责刷新。
     * 方法签名保留是为了兼容 Dubbo 接口与旧调用方。
     */
    @Override
    @Deprecated
    public void refreshCartGoods(CartGoods cartGoods) {
        if (cartGoods != null && cartGoods.getGoodId() != null) {
            evictGoodsCache(cartGoods.getGoodId());
            log.debug("商品变更，已失效购物车商品缓存 goodId={}", cartGoods.getGoodId());
        }
    }

    /**
     * 不再全量扫描所有用户的购物车：商品下架由读路径过滤，这里只失效缓存。
     * 方法签名保留是为了兼容 Dubbo 接口与旧调用方。
     */
    @Override
    @Deprecated
    public void deleteCartGoods(Long goodId) {
        evictGoodsCache(goodId);
        log.debug("商品下架，已失效购物车商品缓存 goodId={}", goodId);
    }
}
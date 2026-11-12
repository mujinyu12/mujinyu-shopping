package com.mujinyu.shopping_seckill_service.service;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.SeckillGoods;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.SeckillService;
import com.mujinyu.shopping_seckill_service.constant.SeckillRedisConstants;
import com.mujinyu.shopping_seckill_service.mapper.SeckillGoodsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@DubboService
@RequiredArgsConstructor
@Service
@Slf4j
public class SeckillServiceImpl implements SeckillService {
    /**
     * Redis库存扣减脚本，原子完成“读取库存-判断库存-扣减库存”
     */
    private static final DefaultRedisScript<Long> STOCK_SCRIPT = loadStockScript();

    private static final long ORDER_TTL_MINUTES = 5L; // 订单超时时间，单位：分钟

    /**
     * 从classpath加载Lua脚本，启动时只加载一次
     */
    private static DefaultRedisScript<Long> loadStockScript() {
        try (InputStream in = new ClassPathResource("lua/seckill_stock.lua").getInputStream()) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(StreamUtils.copyToString(in, StandardCharsets.UTF_8));
            script.setResultType(Long.class);
            return script;
        } catch (IOException e) {
            throw new IllegalStateException("加载秒杀库存扣减脚本失败", e);
        }
    }

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisTemplate redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final BitMapBloomFilter bitMapBloomFilter;
    /**
     * 每分钟查询一次数据库，更新Redis中的秒杀商品数据
     * 条件为startTime < 当前时间 < endTime，库存大于0
     * 数据库是秒杀商品数据的权威来源，库存扣减/回退会同步落库，因此这里只做单向同步（MySQL -> Redis）
     */
    @Scheduled(cron = "0 * * * * *")    // 每分钟执行一次，将mysql中的秒杀商品数据同步到redis
    public void refreshRedis() {
        log.info("同步mysql秒杀商品到Redis");
        // 1、查询数据库中“正在秒杀”的商品
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        Date date = new Date();
        queryWrapper.le("startTime", date) //当前时间晚于开始时间
                .ge("endTime", date) //当前时间早于结束时间
                .gt("stockCount", 0); //库存大于0

        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(queryWrapper);
        // 2、记录上一轮同步的商品id，用于清理不再参加秒杀的商品库存key
        Set<Object> oldGoodsIds = redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).keys();
        // 3、先写入临时键，全部写完后原子替换，避免刷新间隙前端读到空列表
        String refreshKey = "seckillGoods:refresh";
        redisTemplate.delete(refreshKey);
        Set<Long> currentGoodsIds = new HashSet<>(seckillGoodsList.size());
        if (seckillGoodsList.isEmpty()) {
            redisTemplate.delete(SeckillRedisConstants.SECKILL_GOODS_KEY);
        } else {
            Map<Object, Object> seckillGoodsMap = new HashMap<>(seckillGoodsList.size());
            for (SeckillGoods goods : seckillGoodsList) {
                // 统一用 goodsId 作为Redis的field和库存key，和下单链路保持一致（下单时就是按goodsId查的）
                Long goodsId = goods.getGoodsId();
                currentGoodsIds.add(goodsId);
                // 放进本次要整体替换的hash，否则下面rename出来的秒杀商品列表是空的
                seckillGoodsMap.put(goodsId, goods);
                // 布隆过滤器同步：refreshRedis 是唯一从数据库把商品带进缓存的入口，
                // 如果这里不写布隆过滤器，活动期间新开/重启后新同步的商品会被判定为不存在，下单直接返回库存不足
                bitMapBloomFilter.add(goodsId.toString());
                String key = SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId; //
                String dbStock = String.valueOf(goods.getStockCount()); //
                // 首次：仅在key不存在时初始化
                Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(key, dbStock);
                if (Boolean.TRUE.equals(created)) {
                    continue;
                }
                // 已存在：只有Redis值比DB大（被异常抬高）时才用Lua原子下调
                String current = stringRedisTemplate.opsForValue().get(key);
                if (current != null && Long.parseLong(current) > goods.getStockCount()) {
                    stringRedisTemplate.execute(CORRECT_STOCK_SCRIPT, Collections.singletonList(key), dbStock);
                    log.warn("秒杀库存【{}】由{}校正为{}", goodsId, current, dbStock);
                }
            }
            redisTemplate.opsForHash().putAll(refreshKey, seckillGoodsMap);
            // 4、原子替换旧数据，保证本次修改的商品数据能立即生效
            redisTemplate.rename(refreshKey, SeckillRedisConstants.SECKILL_GOODS_KEY);
        }
        // 5、清理已不在秒杀中的商品库存key
        if (oldGoodsIds != null && !oldGoodsIds.isEmpty()) {
            List<String> expiredStockKeys = new ArrayList<>();
            for (Object goodsId : oldGoodsIds) {
                if (!currentGoodsIds.contains(goodsId)) {
                    expiredStockKeys.add(SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId);
                }
            }
            if (!expiredStockKeys.isEmpty()) {
                stringRedisTemplate.delete(expiredStockKeys);
            }
        }
    }

    /**
     * 服务启动完成后主动同步一次秒杀商品缓存
     * 避免重启后到首次定时任务执行之间（最长60秒）库存key为空，
     * 导致下单时Lua脚本返回-1（商品不在秒杀中）
     * 启动阶段的同步失败不能阻断服务启动，异常只记录日志，等待定时任务重试
     */
    @PostConstruct
    public void initSeckillGoodsCache() {
        try {
            refreshRedis();
            log.info("服务启动完成，已主动同步秒杀商品缓存");
        } catch (Exception e) {
            log.error("服务启动阶段同步秒杀商品缓存失败，等待定时任务重试", e);
        }
    }

    @SentinelResource("findPageByRedis")
    @Override
    public Page<SeckillGoods> findPageByRedis(int page, int size) {
        // 1、查询所有秒杀商品列表
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).values();
        // 2、获取当前页商品列表
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;
        int total = seckillGoodsList.size(); // 总记录数
        // 开启截取索引
        int start = Math.min((page - 1) * size, total); // start不能超过总数
        // 结束截取的索引
        int end = Math.min(start + size, total);
        // 获取当前页的结果集
        List<SeckillGoods> seckillGoods = new ArrayList<>(seckillGoodsList.subList(start, end));
        // 3、构造页面对象
        Page<SeckillGoods> seckillGoodsPage = new Page();
        seckillGoodsPage.setCurrent(page) // 设置当前页码
                .setSize(size) // 设置每页条数
                .setTotal(seckillGoodsList.size()) // 设置总记录数
                .setRecords(seckillGoods); // 设置当前页结果集
        return seckillGoodsPage;
    }

    @Override
    public SeckillGoods findSeckillGoodsByRedis(Long goodsId) {
        // 布隆过滤器判断商品是否存在，如果不存在，直接返回空
        if (!bitMapBloomFilter.contains(goodsId.toString())) {
            log.info("布隆过滤器判断商品不存在");
            return null;
        }
        // 1、从Redis中查询秒杀商品
        SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).get(goodsId);
        // 2、如果查到商品，返回
        if (seckillGoods != null) {
            log.info("从Redis中查询到秒杀商品{}", seckillGoods);
            return seckillGoods;
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Orders createOrder(Orders orders) {
        // 1、生成订单对象
        orders.setId(IdWorker.getIdStr()); // 手动使用雪花算法生成订单ID
        orders.setStatus(1); // 设置订单状态为未支付
        orders.setOrderType(2); // 设置订单类型为秒杀订单
        orders.setCreateTime(new Date()); // 设置订单创建时间
        orders.setExpire(new Date(System.currentTimeMillis() + ORDER_TTL_MINUTES * 60_000L)); // 设置订单过期时间为5分钟
        // 计算商品价格
        List<CartGoods> cartGoodsList = orders.getCartGoods();
        if (cartGoodsList == null || cartGoodsList.isEmpty()) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 订单中没有商品，直接拒绝
        }
        CartGoods cartGoods = cartGoodsList.get(0); // 获取购物车商品对象
        Integer num =  cartGoods.getNum(); // 获取购买数量
        if (num == null || num <= 0 || num > 1) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 购买数量非法，防止负数扣减变成增加库存
        }
        // 先取服务端秒杀商品，价格、时间窗全部以它为准
        Long goodsId = cartGoods.getGoodId();
        SeckillGoods seckillGoods = findSeckillGoodsByRedis(goodsId); // 从Redis中查询秒杀商品
        if (seckillGoods == null) {
            throw new BusException(CodeEnum.NO_STOCK_ERROR); // 秒杀商品不存在
        }
        Date now = new Date();
        if (now.before(seckillGoods.getStartTime()) || now.after(seckillGoods.getEndTime())) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 活动未开始/已结束
        }
        // 单价只认服务端：秒杀价（costPrice），绝不使用前端传的 cartGoods.price
        BigDecimal price = seckillGoods.getCostPrice(); // 获取秒杀价
        if (price == null || price.signum() < 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR); // 秒杀商品价格非法
        }
        orders.setSeckillId(seckillGoods.getId()); // 记录场次，退款回补时才能加到正确的场次
        orders.setPayment(price.multiply(BigDecimal.valueOf(num))); // 设置订单支付金额
        // 回填购物车商品单价：后续支付会把该订单交给订单服务落库，那边会用 cartGoods.price 重算金额，
        // 不回填就会被前端的价格再次污染（这条链路是客户端可控的）
        cartGoods.setPrice(price);
        // 回填商品名称与图片：秒杀订单落库后，订单详情/订单列表只认 cartGoods 里的这两个字段，
        // 不回填就会出现"有价格没名字、没有图"的空订单
        cartGoods.setGoodsName(seckillGoods.getTitle());
        cartGoods.setHeaderPic(seckillGoods.getHeaderPic());
        // 执行Lua脚本，原子完成“读取库存-判断库存-扣减库存”，并发下不会超卖
        Long remain = stringRedisTemplate.execute(STOCK_SCRIPT,
                Collections.singletonList(SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId),
                String.valueOf(num));
        if (remain == null || remain < 0) {
            log.info("秒杀商品{}库存扣减失败，脚本返回{}", goodsId, remain);
            throw new BusException(CodeEnum.NO_STOCK_ERROR);
        }
        // 扣减数据库库存（原子SQL），失败时回补Redis库存，避免少卖
        if (seckillGoodsMapper.reduceStock(seckillGoods.getId(), num) == 0) {
            stringRedisTemplate.opsForValue().increment(SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId, num);
            throw new BusException(CodeEnum.NO_STOCK_ERROR);
        }
        // 同步商品详情缓存中的库存，供前端展示（以脚本返回的剩余库存为准）
        seckillGoods.setStockCount(remain.intValue());
        redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).put(goodsId, seckillGoods);
        // 3、将订单数据保存到Redis中
        try {
            // 设置订单5分钟过期，与 orders.expire 保持一致（副本多1分钟，保证过期事件能取到订单详情）
            redisTemplate.opsForValue().set(orders.getId(), orders, ORDER_TTL_MINUTES, TimeUnit.MINUTES); // 键：订单ID，值：订单对象
            /**
             * 给订单对象设置一个副本，保存到Redis中，键为订单ID+"_copy"，副本过期时间长于原订单
             * redis过期后触发过期事件时，redis的数据已经过期，此时只能拿到key，无法拿到value
             * 而过期事件需要回退商品库存，必须拿到value，即订单详情，才能拿到商品数据，进行回退操作
             * 因此，需要将订单详情保存到Redis中，作为副本，过期时间比原订单长，这样在过期事件触发时，可以拿到订单详情，进行回退操作
             */
            redisTemplate.opsForValue().set(orders.getId()+"_copy", orders, ORDER_TTL_MINUTES + 1, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            // 订单缓存写入失败：数据库库存由事务回滚，Redis库存需要回补，避免库存被白扣
            stringRedisTemplate.opsForValue().increment(SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId, num);
            throw e;
        }
        log.info("订单创建成功，订单ID：{}", orders.getId());
        return orders;
    }

    @Override
    public Orders findOrder(String id) {
        return (Orders) redisTemplate.opsForValue().get(id);
    }

    @Override
    public Orders pay(String orderId, Long userId) {
        // 1、查询订单，设置支付相关数据
        Orders orders = findOrder(orderId);
        // 幂等性处理
        if (orders == null || !Integer.valueOf(1).equals(orders.getStatus())) {
            throw new BusException(CodeEnum.ORDER_EXPIRED_ERROR); // 订单不存在或已支付，直接返回
        }
        if (userId == null || !userId.equals(orders.getUserId())) {
            throw new BusException(CodeEnum.NO_PERMISSION); // 用户未登录或者订单不属于该用户，用户无权支付
        }
        orders.setStatus(2); // 设置订单状态为已支付
        orders.setPaymentTime(new Date()); // 设置支付时间
        orders.setPaymentType(1); // 设置支付类型为微信支付
        // 返回订单数据
        return orders;
    }

    @Override
    public void confirmPaid(String orderId) {
        redisTemplate.delete(orderId); // 删除订单缓存
        redisTemplate.delete(orderId + "_copy"); // 删除订单副本缓存
    }

    @Override
    public void addRedisSeckillGoods(SeckillGoods seckillGoods) {
        redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).put(seckillGoods.getGoodsId(), seckillGoods);
        bitMapBloomFilter.add(seckillGoods.getGoodsId().toString());
        // 同步库存key，保证从数据库兜底加载的商品也能被Lua脚本扣减
        // 只在key不存在时初始化，避免把并发扣减后的库存覆盖回数据库的旧值（与定时任务同样的竞态）
        stringRedisTemplate.opsForValue().setIfAbsent(SeckillRedisConstants.STOCK_KEY_PREFIX + seckillGoods.getGoodsId(),
                String.valueOf(seckillGoods.getStockCount()));
    }

    @SentinelResource(value = "findSeckillGoodsByMySql", blockHandler = "mysqlBlockHandler")
    @Override
    public SeckillGoods findSeckillGoodsByMySql(Long goodsId) {
        //3、如果没有查到商品，那么从数据库查询秒杀商品
        // 同一商品可能存在多个秒杀场次，只查询当前正在秒杀且库存大于0的那一场，避免selectOne返回多条抛异常
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        Date now = new Date();
        queryWrapper.eq("goodsId", goodsId)
                .le("startTime", now)
                .ge("endTime", now)
                .gt("stockCount", 0)
                .orderByAsc("startTime")
                .last("LIMIT 1");
        SeckillGoods seckillGoodsMysql = seckillGoodsMapper.selectOne(queryWrapper);
        // 4、如果该商品不在秒杀状态，返回null
        if (seckillGoodsMysql == null) {
            return null;
        }
        log.info("从数据库中查询到秒杀商品{}", seckillGoodsMysql);
        // 5、如果该商品在秒杀状态，将商品保存到Redis中，并返回该商品
        addRedisSeckillGoods(seckillGoodsMysql);
        return seckillGoodsMysql;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreStockForRefund(Long seckillId, Long goodsId, Integer num) {
        if (goodsId == null || num == null || num <= 0) {
            return false; // 参数错误
        }
        // 1、数据库库存：老订单没记seckillId，按goodsId取最近一场兜底
        Long targetId = seckillId;
        if (targetId == null) {
            QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("goodsId", goodsId)
                    .orderByDesc("id")
                    .last("LIMIT 1");
            targetId = seckillGoodsMapper.selectList(queryWrapper)
                    .stream().findFirst().map(SeckillGoods::getId).orElse(null); // 获取最近一场的秒杀ID
        }
        if (targetId == null) {
            log.warn("退款回补找不到秒杀场次 goodsId={}", goodsId);
            return false; // 找不到秒杀场次
        }
        seckillGoodsMapper.addStock(targetId, num); // 回补库存

        // 2、Redis库存：key可能因为活动结束被清掉，incr会自动创建
        String stockKey = SeckillRedisConstants.STOCK_KEY_PREFIX + goodsId;
        Long remain = stringRedisTemplate.opsForValue().increment(stockKey, num);
        stringRedisTemplate.expire(stockKey, 1, TimeUnit.DAYS); // 设置库存key过期时间，避免活动结束后残留

        // 3、同步商品详情缓存里的stockCount，避免前端看到退款前的旧库存
        SeckillGoods cached = (SeckillGoods) redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY)
                .get(goodsId);
        if (cached != null && remain != null) {
            cached.setStockCount(remain.intValue()); // 更新缓存里的库存
            redisTemplate.boundHashOps(SeckillRedisConstants.SECKILL_GOODS_KEY).put(goodsId, cached);
        }
        log.info("秒杀库存已回补 seckillId={} goodsId={} num={} remain={}", targetId, goodsId, num, remain);
        return true;
    }

    /**
     * 降级处理
     * @param goodsId 商品id
     * @param ex 异常
     * @return 空值
     */
    public SeckillGoods mysqlBlockHandler(Long goodsId, BlockException ex) {
        log.info("服务降级方法");
        return null;
    }

    /**
     * 仅当Redis当前值 > 目标写入值时写入，避免覆盖并发扣减
     */
    private static final DefaultRedisScript<Long> CORRECT_STOCK_SCRIPT = new DefaultRedisScript<>(
            "local cur = tonumber(redis.call('GET', KEYS[1])) " +
                    "local target = tonumber(ARGV[1]) " +
                    "if cur == nil or cur <= target then return 0 end " +
                    "redis.call('SET', KEYS[1], target) return 1", Long.class);
}

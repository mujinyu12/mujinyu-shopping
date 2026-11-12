-- 秒杀库存原子扣减脚本
-- Redis单线程执行脚本，执行期间不会插入其他命令，
-- 因此“读取库存 - 判断库存 - 扣减库存”三步天然原子，并发下不会超卖
-- KEYS[1] 库存key（seckill:stock:{goodsId}）
-- ARGV[1] 购买数量
-- 返回值：>=0 扣减后的剩余库存；-1 商品不在秒杀中（库存key不存在）；-2 库存不足；-3 购买数量非法

local stock = redis.call('GET', KEYS[1])
if stock == false then
    return -1
end
stock = tonumber(stock)
local num = tonumber(ARGV[1])
if num == nil or num <= 0 then
    return -3
end
if stock < num then
    return -2
end
redis.call('DECRBY', KEYS[1], num)
return stock - num
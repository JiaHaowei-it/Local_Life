--1.参数列表
--1.1优惠券id
local voucherId = ARGV[1]
--1.2用户id
local userId = ARGV[2]

--2.数据key
--2.1.库存Key
local stockKey = "seckill:stock:"..voucherId
--2.2.订单key
local orderKey = "seckill:order:"..voucherId
--3.脚本业务
--3.1.判断库存是否充足 get stockKey
if(tonumber(redis.call('get',stockKey))<=0)then
    -- 库存不足，返回1
    return 1
end
--3.2.判断用户是否下过单 SISMEMBER orderKey userId
if (redis.call('sismember',orderKey,userId) == 1) then
    -- 存在，说明重复下单，返回2
    return 2
end

--3.3.扣库存 incrby stockKey -1
redis.call('incrby',stockKey,-1)
--3.4.下单，保存用户 sadd orderkey userid
redis.call('sadd',orderKey,userId)
return 0
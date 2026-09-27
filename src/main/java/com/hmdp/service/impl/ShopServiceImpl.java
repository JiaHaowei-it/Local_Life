package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //利用互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //7.返回
        return Result.ok(shop);
    }

    private Shop queryWithMutex(Long id){
        String Key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(Key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopCache, Shop.class);
        }

        //判断从缓存中查到的是否是空值
        if (shopCache != null){
            //返回错误信息
            return null;
        }

        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2判断是否获取成功
            if (!isLock){
                //4.3失败,休眠并重试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            //4.成功，根据商铺id查询数据库
            shop = getById(id);

            //模拟重建延时
            Thread.sleep(200);

            //5.不存在，返回错误
            if (shop == null){
                //缓存空值
                stringRedisTemplate.opsForValue().set(Key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis
            stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }

    private Shop queryWithPassThrough(Long id){
        String Key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(Key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)){
            //3.存在，直接返回
            return JSONUtil.toBean(shopCache, Shop.class);
        }

        //判断从缓存中查到的是否是空值
        if (shopCache != null){
            //返回错误信息
            return null;
        }

        //4.不存在，根据商铺id查询数据库
        Shop shop = getById(id);
        //5.不存在，返回错误
        if (shop == null){
            //缓存空值
            stringRedisTemplate.opsForValue().set(Key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    /**
     * 更新商铺信息
     * @param shop 商铺数据
     */
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空!");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}

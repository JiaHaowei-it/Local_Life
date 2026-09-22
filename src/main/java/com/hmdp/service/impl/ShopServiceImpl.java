package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

        String Key = CACHE_SHOP_KEY + id;

        //1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(Key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopCache)){
            //3.存在，直接返回
            JSONUtil.toBean(shopCache, Shop.class);
            return Result.ok(shopCache);
        }
        //4.不存在，根据商铺id查询数据库
        Shop shop = getById(id);
        //5.不存在，返回错误
        if (shop == null){
            return Result.fail("店铺不存在!");
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop));

        //7.返回
        return Result.ok(shop);
    }
}

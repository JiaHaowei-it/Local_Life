package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    String key = CACHE_SHOP_TYPE_KEY;
    public List<ShopType> queryTypeList() {
        //1.从redis中查询商铺类型
        List<String> shopTypeListJson = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.判断是否存在
        if (shopTypeListJson != null && !shopTypeListJson.isEmpty()){
            List<ShopType> TypeList = shopTypeListJson.stream()
                    .map(JSON -> JSONUtil.toBean(JSON, ShopType.class))
                    .collect(Collectors.toList());
            //3.存在,直接返回
            return TypeList;
        }
        //4.不存在,查询数据库
        List<ShopType> shopTypeListDB = query().orderByAsc("sort").list();

        //5.数据库中不存在,返回错误信息
        if (shopTypeListDB == null){
            //缓存空列表
            stringRedisTemplate.opsForList().rightPushAll(key, Collections.emptyList());
            stringRedisTemplate.expire(key,30, TimeUnit.MINUTES);
            return Collections.emptyList();
        }
        //6.存在,将商铺类型写入redis
        List<String> shopTypeList = shopTypeListDB.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key,shopTypeList);
        //7.返回
        return shopTypeListDB;
    }
}

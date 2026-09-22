package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisTest {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSet() {
        stringRedisTemplate.opsForValue().set("test:connect", "ok", 1, TimeUnit.MINUTES);
        String res = stringRedisTemplate.opsForValue().get("test:connect");
        System.out.println("redis结果：" + res);
    }
}

package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    
    /*@Override
    public Result secVoucherKill(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        //3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束!");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");
        }

        //8.返回订单id
        Long userId = UserHolder.getUser().getId();

        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //获取锁
        boolean isLock = lock.tryLock();

        //判断是否获取成功
        if (!isLock){
            return Result.fail("不允许重复下单");
        }

        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally{
            //释放锁……
            lock.unlock();
        }

    }*/

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024*1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTask.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取成功
        if (!isLock){
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        }finally{
            //释放锁……
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result secVoucherKill(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //2.判断结果是否为0
        int res = result.intValue();
        if(res != 0){
            //2.1.不为0，代表没有购买资格
            return Result.fail(res==1?"库存不足":"不能重复下单");
        }
        //2.2.为0，代表有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.3订单id
        long orderId = redisIdWorker.nextId("order");

        voucherOrder.setId(orderId);
        //2.4用户id
        voucherOrder.setUserId(userId);
        //2.5优惠券id
        voucherOrder.setVoucherId(voucherId);
        //2.6.放入阻塞队列
        orderTask.add(voucherOrder);
        //3.获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.一人一单（异步落库线程无 UserHolder，userId 从订单对象取）
        Long userId = voucherOrder.getUserId();

            //查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0){
                log.error("该优惠券每人仅限一单!");
                return ;
            }

            //6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0)
                    .update();
            if (!success){
                //扣减失败
                log.error("库存不足");
                return;
            }
            //7.创建订单
            save(voucherOrder);
    }
}

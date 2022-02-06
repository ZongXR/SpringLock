package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.lock.annotation.OptimisticLock;
import org.springframework.lock.timer.InterruptTimer;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 乐观锁的切面
 */
@Aspect
public class OptimisticLockAspect {

    /**
     * 日志
     */
    private static final Log LOGGER = LogFactory.getLog(OptimisticLockAspect.class);

    /**
     * 乐观锁的切面
     * @param jp 切入点
     * @return 原函数返回值
     * @throws Throwable 原函数抛出异常
     */
    @Around("@annotation(org.springframework.lock.annotation.OptimisticLock)")
    public Object aroundOptimisticLock(ProceedingJoinPoint jp) throws Throwable {
        // 获取注解属性值
        long pollingTime = 500L;
        long waitTime = Long.MAX_VALUE;
        long executeTime = Long.MAX_VALUE;
        boolean isContinueIfElapsed = false;
        boolean withLockIfContinue = false;
        String lockName = null;

        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        OptimisticLock annotation = method.getAnnotation(OptimisticLock.class);
        if (annotation != null) {
            pollingTime = annotation.pollingTime();
            waitTime = annotation.waitTime();
            executeTime = annotation.executeTime();
            isContinueIfElapsed = annotation.isContinueIfElapsed();
            withLockIfContinue = annotation.withLockIfContinue();
            lockName = annotation.value();
        }

        // 获取锁对象
        AtomicLong lock = null;
        Object obj = jp.getTarget();
        Class<?> clz = obj.getClass();
        Field field = null;

        if (StringUtils.hasText(lockName)){
            field = clz.getDeclaredField(lockName);
            field.setAccessible(true);
            lock = (AtomicLong) field.get(obj);
        }else{
            field = clz.getDeclaredField("$opLock");
            field.setAccessible(true);
            lock = (AtomicLong) field.get(obj);
        }

        if (lock == null){
            LOGGER.warn(clz.getSimpleName() + "获取锁错误，请检查锁名称是否正确");
            return jp.proceed();
        }

        // 尝试加锁，不行就解掉别人的锁
        Object result = null;
        boolean locked = this.tryLock(lock, waitTime, pollingTime);
        if (locked){
            result = this.processMethod(jp, lock, executeTime);
        }else {
            if (isContinueIfElapsed){
                if (withLockIfContinue){
                    Thread lockedThread = findThread(lock.get());
                    if (lockedThread == null)
                        result = this.processMethod(jp, lock, executeTime);
                    else {
                        lockedThread.interrupt();
                        if (this.tryLock(lock, waitTime, pollingTime)){
                            LOGGER.warn("等待时间耗尽，终止线程" + lockedThread + "以强制获得乐观锁@" + lock.hashCode());
                            result = this.processMethod(jp, lock, executeTime);
                        }else {
                            lockedThread.stop();
                            if (this.tryLock(lock, waitTime, pollingTime)){
                                LOGGER.warn("等待时间耗尽，终止线程" + lockedThread + "以强制获得乐观锁@" + lock.hashCode());
                                result = this.processMethod(jp, lock, executeTime);
                            }
                        }
                    }
                }else {
                    LOGGER.warn("等待时间耗尽，将不带锁执行" + method.getName());
                    result = jp.proceed();
                }
            }else {
                LOGGER.warn("等待时间耗尽，放弃执行" + method.getName());
            }
        }
        return result;
    }

    /**
     * 处理方法
     * @param jp 切入点
     * @param lock 乐观锁，调用方法前必须加锁
     * @param executeTime 最长执行时间，超出此时间结束线程
     * @return 原方法返回值
     * @throws Throwable 异常
     */
    private Object processMethod(ProceedingJoinPoint jp, AtomicLong lock, long executeTime) throws Throwable {
        Object result = null;
        LOGGER.info(Thread.currentThread().getName() + "获得乐观锁@" + lock.hashCode());
        if (System.currentTimeMillis() + executeTime > 0)
            new InterruptTimer(Thread.currentThread(), executeTime);
        try{
            result = jp.proceed();
        } finally {
            LOGGER.info(Thread.currentThread().getName() + "释放乐观锁@" + lock.hashCode());
            lock.set(0);
        }
        return result;
    }

    /**
     * 通过线程id获取线程
     * @param threadId 线程id
     * @return 线程
     */
    private static Thread findThread(long threadId) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while(group != null) {
            Thread[] threads = new Thread[(int)(group.activeCount() * 1.2)];
            int count = group.enumerate(threads, true);
            for(int i = 0; i < count; i++) {
                if(threadId == threads[i].getId()) {
                    return threads[i];
                }
            }
            group = group.getParent();
        }
        return null;
    }

    /**
     * 忙等待，直到超时或获取锁
     * @param lock 锁
     * @param waitTime 最长等待时长
     * @param pollingTime 轮询周期
     * @return 是否获得锁
     * @throws InterruptedException 中断异常
     */
    private boolean tryLock(AtomicLong lock, long waitTime, long pollingTime) throws InterruptedException {
        long startWaitTime = System.currentTimeMillis();
        while (true){
            if (lock.compareAndSet(0, Thread.currentThread().getId())) {
                // 拿到了锁
                return true;
            }else if (System.currentTimeMillis() - startWaitTime > waitTime)
                // 超时了
                return false;
            else
                // 如果没拿到锁就忙等待
                sleep(pollingTime);
        }
    }
}

package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.lock.annotation.WriteLock;
import org.springframework.lock.timer.InterruptTimer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 用于处理写锁的切面
 */
@Aspect
public class WriteLockAspect {

    /**
     * 日志
     */
    private static final Log LOGGER = LogFactory.getLog(WriteLockAspect.class);

    /**
     * 环绕通知
     * @param jp 切入点
     * @return 原函数返回值
     * @throws Throwable 原函数抛出异常
     */
    @Around("@annotation(org.springframework.lock.annotation.WriteLock)")
    public Object aroundWriteLock(ProceedingJoinPoint jp) throws Throwable {
        Object obj = jp.getTarget();
        Class<?> clz = obj.getClass();
        Lock writeLock = null;
        ReentrantReadWriteLock lock = null;
        for (Field field : clz.getDeclaredFields()) {
            if ("$writeLock".equals(field.getName())){
                field.setAccessible(true);
                writeLock = (Lock) field.get(obj);
            }
            if ("$lock".equals(field.getName())){
                field.setAccessible(true);
                lock = (ReentrantReadWriteLock) field.get(obj);
            }
            if (lock != null && writeLock != null)
                // 都找到了
                break;
        }
        if (lock == null || writeLock == null){
            // 连锁都没拿到，说明编译期间出了问题
            LOGGER.warn(clz.getSimpleName() + "编译时生成读写锁锁失败,未能加锁");
            return jp.proceed();
        }

        long waitTime = Long.MAX_VALUE;
        long executeTime = Long.MAX_VALUE;
        boolean isContinueIfElapsed = false;
        boolean withLockIfContinue = false;

        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        if (method == null) {
            // 没拿到方法，那注解给了谁呢？
            LOGGER.warn("没拿到方法" + signature);
            return jp.proceed();
        }
        WriteLock annotation = method.getAnnotation(WriteLock.class);
        if (annotation != null) {
            waitTime = annotation.waitTime();
            executeTime = annotation.executeTime();
            isContinueIfElapsed = annotation.isContinueIfElapsed();
            withLockIfContinue = annotation.withLockIfContinue();
        }

        Object result = null;
        boolean locked = writeLock.tryLock(waitTime, MILLISECONDS);
        if (locked) {
            result = this.processMethod(jp, writeLock, executeTime);
        }else {
            if (isContinueIfElapsed){
                if (withLockIfContinue){
                    // 解别人锁的逻辑，这是终极大招
                    Method getOwner = ReentrantReadWriteLock.class.getDeclaredMethod("getOwner");
                    getOwner.setAccessible(true);
                    Thread lockedThread = (Thread) getOwner.invoke(lock);
                    lockedThread.interrupt();
                    if (writeLock.tryLock(waitTime, MILLISECONDS)){
                        LOGGER.warn("等待时间耗尽，中断线程" + lockedThread + "以强制获得锁");
                        result = this.processMethod(jp, writeLock, executeTime);
                    }else {
                        lockedThread.stop();
                        if (writeLock.tryLock(waitTime, MILLISECONDS)) {
                            LOGGER.warn("等待时间耗尽，终止线程" + lockedThread + "以强制获得锁");
                            result = this.processMethod(jp, writeLock, executeTime);
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
     * 处理方法的过程
     * @param jp 切入点
     * @param writeLock 写锁，在调用方法前必须加锁
     * @param executeTime 最大执行时长
     * @return 原方法返回值
     * @throws Throwable 异常
     */
    private Object processMethod(ProceedingJoinPoint jp, Lock writeLock, long executeTime) throws Throwable {
        Object result = null;
        LOGGER.info(Thread.currentThread().getName() + "获得写锁");
        new InterruptTimer(Thread.currentThread(), executeTime);
        try {
            result = jp.proceed();
        } finally {
            writeLock.unlock();
            LOGGER.info(Thread.currentThread().getName() + "释放写锁");
        }
        return result;
    }
}

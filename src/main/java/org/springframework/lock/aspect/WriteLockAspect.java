package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;

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
        for (Field field : clz.getDeclaredFields()) {
            if ("$writeLock".equals(field.getName())){
                field.setAccessible(true);
                Object unknownLock = field.get(obj);
                writeLock = (Lock) unknownLock;
            }
        }
        Object result = null;
        if (writeLock != null) {
            writeLock.lock();
            LOGGER.info(clz.getSimpleName() + "获得写锁");
            try {
                result = jp.proceed();
            } finally {
                writeLock.unlock();
                LOGGER.info(clz.getSimpleName() + "释放写锁");
            }
        }else {
            LOGGER.warn(clz.getSimpleName() + "生成读锁失败,未能加锁");
            result = jp.proceed();
        }
        return result;
    }

}

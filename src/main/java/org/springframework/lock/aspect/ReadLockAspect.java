package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;

/**
 * 用于处理读锁的切面
 */
@Aspect
public class ReadLockAspect {

    /**
     * 日志
     */
    private static final Log LOGGER = LogFactory.getLog(ReadLockAspect.class);

    /**
     * 环绕通知
     * @param jp 切入点
     * @return 原函数返回值
     * @throws Throwable 原函数异常
     */
    @Around("@annotation(org.springframework.lock.annotation.ReadLock)")
    public Object aroundReadLock(ProceedingJoinPoint jp) throws Throwable {
        Object obj = jp.getTarget();
        Class<?> clz = obj.getClass();
        Lock readLock = null;
        for (Field field : clz.getDeclaredFields()) {
            if ("$readLock".equals(field.getName())){
                field.setAccessible(true);
                Object unknownLock = field.get(obj);
                readLock = (Lock) unknownLock;
            }
        }
        Object result = null;
        if (readLock != null) {
            readLock.lock();
            try {
                LOGGER.info(clz.getSimpleName() + "获得读锁");
                result = jp.proceed();
                LOGGER.info(clz.getSimpleName() + "释放读锁");
            } finally {
                readLock.unlock();
            }
        }else {
            LOGGER.warn(clz.getSimpleName() + "生成读锁失败,未能加锁");
            result = jp.proceed();
        }
        return result;
    }
}

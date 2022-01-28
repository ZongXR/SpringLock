package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;


@Aspect
public class ReadLockAspect {

    private static final Log LOGGER = LogFactory.getLog(ReadLockAspect.class);

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

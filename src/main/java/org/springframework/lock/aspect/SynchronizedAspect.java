package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;

@Aspect
public class SynchronizedAspect {

    private static final Log LOGGER = LogFactory.getLog(SynchronizedAspect.class);

    @Around(value = "@annotation(org.springframework.lock.annotation.Synchronized)")
    public Object aroundSynchronized(ProceedingJoinPoint jp) throws Throwable {
        Class<?> clz = jp.getTarget().getClass();
        Object result = null;
        synchronized (clz) {
            LOGGER.info(clz.getSimpleName() + "获得锁");
            result = jp.proceed();
            LOGGER.info(clz.getSimpleName() + "释放锁");
        }
        return result;
    }
}

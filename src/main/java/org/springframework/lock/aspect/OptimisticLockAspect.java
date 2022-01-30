package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

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
        Object result = jp.proceed();
        return result;
    }

}

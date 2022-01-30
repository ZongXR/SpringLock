package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.lock.annotation.OptimisticLock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.sleep;

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
        Object obj = jp.getTarget();
        Class<?> clz = obj.getClass();
        // 获取等待时长，默认500毫秒
        long waitTime = 500L;
        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        if (method != null) {
            OptimisticLock annotation = method.getAnnotation(OptimisticLock.class);
            if (annotation != null) {
                waitTime = annotation.value();
            }
        }
        // 获取锁对象
        AtomicBoolean lock = null;
        for (Field field : clz.getDeclaredFields()) {
            if ("$opLock".equals(field.getName())){
                field.setAccessible(true);
                Object unknownLock = field.get(obj);
                lock = (AtomicBoolean) unknownLock;
            }
        }
        Object result = null;
        if (lock != null){
            while (true){
                if (lock.compareAndSet(true, false))
                    // 拿到了锁
                    break;
                else
                    // 如果没拿到锁就忙等待
                    sleep(waitTime);
            }
            try {
                LOGGER.info(clz.getSimpleName() + "获得乐观锁");
                result = jp.proceed();
                LOGGER.info(clz.getSimpleName() + "释放乐观锁");
            }finally {
                lock.set(true);
            }
        }else{
            LOGGER.warn(clz.getSimpleName() + "生成乐观锁失败,未能加锁");
            result = jp.proceed();
        }
        return result;
    }
}

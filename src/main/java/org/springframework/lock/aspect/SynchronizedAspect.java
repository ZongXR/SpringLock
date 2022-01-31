package org.springframework.lock.aspect;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.lock.annotation.Synchronized;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 用来处理互斥锁的切面
 */
@Aspect
public class SynchronizedAspect {

    /**
     * 日志
     */
    private static final Log LOGGER = LogFactory.getLog(SynchronizedAspect.class);

    /**
     * 环绕通知
     * @param jp 切入点
     * @return 原函数返回值
     * @throws Throwable 原函数抛出的异常
     */
    @Around(value = "@annotation(org.springframework.lock.annotation.Synchronized)")
    public Object aroundSynchronized(ProceedingJoinPoint jp) throws Throwable {
        Class<?> clz = jp.getTarget().getClass();
        Object lock = clz;
        MethodSignature signature = (MethodSignature) jp.getSignature();
        Method method = signature.getMethod();
        if (method != null) {
            Synchronized annotation = method.getAnnotation(Synchronized.class);
            if (annotation != null){
                String varName = annotation.value();
                if (!"".equals(varName)) {
                    for (Field field : clz.getDeclaredFields()) {
                        field.setAccessible(true);
                        if (varName.equals(field.getName())){
                            lock = field.get(jp.getTarget());
                            break;
                        }
                    }
                }
            }
        }
        Object result = null;
        synchronized (lock) {
            LOGGER.info(clz.getSimpleName() + "获得互斥锁");
            result = jp.proceed();
            LOGGER.info(clz.getSimpleName() + "释放互斥锁");
        }
        return result;
    }
}

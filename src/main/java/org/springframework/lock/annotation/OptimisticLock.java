package org.springframework.lock.annotation;


import java.lang.annotation.*;

/**
 * 乐观锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface OptimisticLock {

    /**
     * 乐观锁忙等待毫秒数
     * @return 乐观锁忙等待时间
     */
    long value() default 500L;
}

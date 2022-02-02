package org.springframework.lock.annotation;

import java.lang.annotation.*;

/**
 * 读锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ReadLock {

    /**
     * 是否是公平锁
     * @return 默认非公平
     */
    boolean fair() default false;
}

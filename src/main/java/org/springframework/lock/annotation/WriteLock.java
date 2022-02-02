package org.springframework.lock.annotation;

import java.lang.annotation.*;

/**
 * 写锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface WriteLock {

    /**
     * 是否是公平锁
     * @return 默认非公平锁
     */
    boolean fair() default false;
}

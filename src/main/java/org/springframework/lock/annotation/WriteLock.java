package org.springframework.lock.annotation;

import java.lang.annotation.*;

/**
 * 写锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface WriteLock {
}

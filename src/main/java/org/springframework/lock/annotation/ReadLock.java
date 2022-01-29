package org.springframework.lock.annotation;

import java.lang.annotation.*;

/**
 * 读锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ReadLock {
}

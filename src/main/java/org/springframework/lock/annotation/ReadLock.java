package org.springframework.lock.annotation;

import org.springframework.lock.enumeration.BooleanEnum;

import java.lang.annotation.*;

import static org.springframework.lock.enumeration.BooleanEnum.*;

/**
 * 读锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ReadLock {

    /**
     * 是否公平锁
     * @return 默认null，如果有自定义值则覆盖默认值
     */
    BooleanEnum fair() default NULL;
}

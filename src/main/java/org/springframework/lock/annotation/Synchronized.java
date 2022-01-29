package org.springframework.lock.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 互斥锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Synchronized {

    /**
     * 用来当作锁的成员变量名，默认使用当前类的字节码当作锁，同lock
     * @return 锁对象
     */
    String value() default "";

}

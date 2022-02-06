package org.springframework.lock.annotation;

import java.lang.annotation.*;

/**
 * 自动生成读写锁
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface MakeReadWriteLocks {

    /**
     * 锁变量的名称，必须是合法变量名，并且不与已有变量声明重合
     * @return 默认无锁变量
     */
    String[] value() default {};
}

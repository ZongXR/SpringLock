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
     * 乐观锁变量的名称
     * @return 默认使用自动生成的锁
     */
    String value() default "";

    /**
     * 乐观锁忙等待周期毫秒数
     * @return 乐观锁忙等待时间
     */
    long pollingTime() default 500L;

    /**
     * 最长等待时间
     * @return 默认Long.MAX_VALUE
     */
    long waitTime() default Long.MAX_VALUE;

    /**
     * 最长执行时间，超过此时间自动解锁
     * @return 默认Long.MAX_VALUE
     */
    long executeTime() default Long.MAX_VALUE;

    /**
     * 是否强制执行，如果等待时间耗尽还没有拿到锁的话，是否还要执行
     * @return 默认放弃
     */
    boolean isContinueIfElapsed() default false;

    /**
     * 如果强制执行，是否中断其他线程以强制获得锁. 该属性仅在{@code isContinueIfElapsed}为{@code true}时才生效
     * @return 默认否
     */
    boolean withLockIfContinue() default false;
}

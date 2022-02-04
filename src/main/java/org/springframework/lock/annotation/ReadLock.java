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
     * 读写锁变量的名称，该变量必须是{@code ReentrantReadWriteLock}类的对象或其子类对象
     * @return 默认使用编译生成的
     */
    String value() default "";

    /**
     * 编译时生成的是否公平锁，该属性仅在{@code value}属性保持默认时才生效
     * @return 默认null，如果有自定义值则覆盖默认值
     */
    BooleanEnum fair() default NULL;

    /**
     * 等待时长，没获得锁的时候最多等待多长时间
     * @return 默认最多等待Long.MAX_VALUE
     */
    long waitTime() default Long.MAX_VALUE;

    /**
     * 执行时长，超过此时间无论有没有执行完毕都要释放锁
     * @return 默认Long.MAX_VALUE
     */
    long executeTime() default Long.MAX_VALUE;

    /**
     * 是否强制执行，如果等待时间耗尽还没有拿到锁的话，是否还要执行
     * @return 默认放弃
     */
    boolean isContinueIfElapsed() default false;

    /**
     * 如果强制执行，是否中断其他线程以强制获得锁
     * @return 默认否
     */
    boolean withLockIfContinue() default false;
}

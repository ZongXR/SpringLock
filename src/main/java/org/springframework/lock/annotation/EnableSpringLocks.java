package org.springframework.lock.annotation;

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.lock.aspect.ReadLockAspect;
import org.springframework.lock.aspect.SynchronizedAspect;
import org.springframework.lock.aspect.WriteLockAspect;

import java.lang.annotation.*;

/**
 * 如果要使注解生效，需要在启动类加上这个注解
 */
@Import({
        SynchronizedAspect.class,
        ReadLockAspect.class,
        WriteLockAspect.class
})
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface EnableSpringLocks {
}

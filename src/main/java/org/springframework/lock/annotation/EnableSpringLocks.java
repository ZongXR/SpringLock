package org.springframework.lock.annotation;

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.lock.aspect.ReadLockAspect;
import org.springframework.lock.aspect.SynchronizedAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Import({
        SynchronizedAspect.class,
        ReadLockAspect.class
})
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableSpringLocks {
}

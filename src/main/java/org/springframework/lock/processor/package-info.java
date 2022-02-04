/**
 * 这个包才是本项目的精华，存储了注解的处理器.
 * 这些处理器的作用是将锁对象动态编译进入对应的类，使之成为类的成员.
 * 它的原理与lombok有些许类似，但代码风格比lombok更好理解，一看就懂
 * 每个处理器都写了注释，写得都很清楚，不加以赘述
 * @author 宗祥瑞
 * @version 0.8.1.0
 */
package org.springframework.lock.processor;
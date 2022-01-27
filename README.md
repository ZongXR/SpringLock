<h1>SpringLock</h1>
<b>让你用更优雅的方式加锁，基于spring的注解的加锁实现方式</b>
<h2>功能简介</h2>
这个jar包能够让你使用更优雅的方式加锁——<b>基于注解的加锁方式</b>。
一直以来，线程安全问题是考验web后端人员的主要问题之一，加锁是解决线程安全问题的主要形式。
从锁的形式上看，锁可以分为乐观锁和悲观锁。从锁的内容上看，锁可以分为互斥锁和共享锁。
这个Jar包旨在提供全套的锁解决方案，让你使用一个注解就能对目标进行加锁，就像下面这样子。<br />
<code>@Synchronized</code><br />
<code>public Consumer queryConsumerById(String id){...}</code><br />
等同于<br />
<code>public synchronized Consumer queryConsumerById(String id){...}</code><br />
再比如<br />
<code>@ReadLock</code><br />
<code>public Consumer queryConsumerById(String id){...}</code><br />
等同于<br />
<code>public Consumer queryConsumerById(String id){</code><br />
    <code>&nbsp;&nbsp;&nbsp;&nbsp;this.readLock.lock();</code><br />
    <code>&nbsp;&nbsp;&nbsp;&nbsp;try{</code><br />
    <code>    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;...</code><br />
    <code>&nbsp;&nbsp;&nbsp;&nbsp;}finally{</code><br />
    <code>    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;this.readLock.unlock();</code><br />
    <code>&nbsp;&nbsp;&nbsp;&nbsp;}</code><br />
    <code>&nbsp;&nbsp;&nbsp;&nbsp;return ...</code><br />
<code>}</code><br />
如何？看了上面的简介是不是觉得使用很方便呢？
<h2>版本更新</h2>
<table>
<tr>
<th>版本号</th><th>更新内容</th><th>更新日期</th>
</tr>
<tr>
<td>0.1.0.0</td><td>完成基本框架搭建；新增Synchronized注解，实现同步代码块</td><td>2022年1月27日</td>
</tr>
</table>

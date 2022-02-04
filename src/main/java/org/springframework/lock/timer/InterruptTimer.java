package org.springframework.lock.timer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 定时器，在delay时间后将指定线程中断
 */
public class InterruptTimer extends Timer {

    /**
     * 要中断的线程
     */
    private Thread parent = null;

    /**
     * 构造方法
     * @param parent 要中断的线程
     * @param delay 多长时间后执行
     */
    public InterruptTimer(Thread parent, long delay){
        super("" + parent.getName() + "Interrupter");
        this.parent = parent;
        this.schedule(new InterruptTimerTask(), delay);
    }

    /**
     * 中断线程
     */
    class InterruptTimerTask extends TimerTask{

        /**
         * 中断线程的任务
         */
        @Override
        public void run() {
            if (InterruptTimer.this.parent != null)
                InterruptTimer.this.parent.interrupt();
        }
    }
}

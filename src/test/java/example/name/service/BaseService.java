package example.name.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lock.annotation.*;
import org.springframework.stereotype.Service;

import static org.springframework.lock.enumeration.BooleanEnum.*;


@Service
@MakeReadWriteLocks({
        "myLock1",
        "myLock2",
        "myLock3"
})
public class BaseService {

    private static final Log LOGGER = LogFactory.getLog(BaseService.class);

//    private final ReentrantReadWriteLock $lock = new ReentrantReadWriteLock();
//
//    private final Lock $readLock = $lock.readLock();
//
//    private final Lock writeLock = lock.writeLock();

    @Synchronized
    public String testSynchronized() {
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testSynchronized 执行结束";
    }

    @ReadLock(executeTime = 60000, isContinueIfElapsed = true, withLockIfContinue = true)
    public String testReadLock() {
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testReadLock 执行结束";
    }

    @WriteLock(fair = TRUE, waitTime = 3000, isContinueIfElapsed = true, withLockIfContinue = true)
    public String testWriteLock2(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(500000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testWriteLock2 执行结束";
    }

    @WriteLock(waitTime = 3000, executeTime = 60000, isContinueIfElapsed = true, withLockIfContinue = true)
    public String testWriteLock(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testWriteLock 执行结束";
    }

    @OptimisticLock(waitTime = 3000, isContinueIfElapsed = true, withLockIfContinue = true)
    public String testOptimisticLock(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testOptimisticLock 执行结束";
    }

    @OptimisticLock(executeTime = 300000)
    public String testOptimisticLock2(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(500000000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testOptimisticLock2 执行结束";
    }

    @ReadLock("myLock1")
    public String testMakeLockRead(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testMakeLockRead 执行结束";
    }

    @WriteLock("myLock1")
    public String testMakeLockWrite(){
        String name = Thread.currentThread().getName();
        LOGGER.info(name + "开始执行");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info(name + "执行结束");
        return "testMakeLockWrite 执行结束";
    }
}

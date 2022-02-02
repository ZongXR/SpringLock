package example.name.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lock.annotation.ReadLock;
import org.springframework.lock.annotation.WriteLock;
import org.springframework.stereotype.Service;

import static org.springframework.lock.enumeration.BooleanEnum.*;

@Service
public class BaseService2 {

    private static final Log LOGGER = LogFactory.getLog(BaseService2.class);

    @ReadLock(fair = FALSE)
    public String testReadLock2(){
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

    @WriteLock
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
}

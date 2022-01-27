package example.name;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.lock.annotation.EnableSpringLocks;

@EnableSpringLocks
@SpringBootApplication
public class StarterSpringLock {
    public static void main(String[] args) {
        SpringApplication.run(StarterSpringLock.class, args);
    }
}

package gov.nic.apar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AparApplication {
    public static void main(String[] args) {
        SpringApplication.run(AparApplication.class, args);
    }
}

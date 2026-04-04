package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventWorkerApplication.class, args);
    }
}

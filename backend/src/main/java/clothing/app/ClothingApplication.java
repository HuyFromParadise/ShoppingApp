package clothing.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} drives the unpaid-order timeout sweep (ADR-005). */
@SpringBootApplication
@EnableScheduling
public class ClothingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClothingApplication.class, args);
	}

}

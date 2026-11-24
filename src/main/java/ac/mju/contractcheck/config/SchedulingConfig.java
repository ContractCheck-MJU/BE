package ac.mju.contractcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업(멈춘 비동기 작업 정리 등)을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

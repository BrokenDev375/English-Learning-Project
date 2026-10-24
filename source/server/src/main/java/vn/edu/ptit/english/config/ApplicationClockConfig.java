package vn.edu.ptit.english.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationClockConfig {
    @Bean @ConditionalOnMissingBean(Clock.class)
    public Clock applicationClock() { return Clock.systemUTC(); }
}

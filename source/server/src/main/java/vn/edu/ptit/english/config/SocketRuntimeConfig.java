package vn.edu.ptit.english.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration @EnableScheduling
public class SocketRuntimeConfig {
    @Bean public Clock socketClock() { return Clock.systemUTC(); }
}

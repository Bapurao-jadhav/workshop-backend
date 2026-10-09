package com.workshop.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AppConfig {

    /** Business clock: workshop dates and deadlines are interpreted in this time zone. */
    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }
}

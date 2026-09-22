package com.srip;

import com.srip.config.AnalyticsProperties;
import com.srip.config.ClaudeProperties;
import com.srip.config.DemoProperties;
import com.srip.config.GradingProperties;
import com.srip.config.JwtProperties;
import com.srip.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableConfigurationProperties({
        JwtProperties.class,
        ClaudeProperties.class,
        GradingProperties.class,
        AnalyticsProperties.class,
        UploadProperties.class,
        DemoProperties.class
})
public class StudentResultIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentResultIntelligenceApplication.class, args);
    }
}

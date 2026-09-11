/**
 * VmDeployApplication.java
 *
 * @author Soumojit Makar
 * @date 28-Jul-2026
 */
package com.nnp.dms;

import com.nnp.dms.config.GitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Binds git.repository.* properties from application.properties into GitProperties
@EnableConfigurationProperties(GitProperties.class)
// Enables @Scheduled methods (health-check scheduler + SSH-key cache purge)
@EnableScheduling
public class VmDeployApplication {
    public static void main(String[] args) {
        // Entry point for the Spring Boot application
        SpringApplication.run(VmDeployApplication.class, args);
    }
}

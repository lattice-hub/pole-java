package io.github.latticehub.agent.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootAgentSmokeTest {
    @Test
    void agentInstallsVersionSpecificAdapter() {
        try (var context = new SpringApplication(TestApplication.class).run(
                "--spring.main.web-application-type=none",
                "--spring.main.banner-mode=off")) {
            assertTrue(context.containsBean("poleSidecarHttpListenerProvider"));
            assertEquals("spring-cloud-5x", System.getProperty("pole.agent.spring-cloud.selected-plugin"));
        }
    }

    @SpringBootConfiguration
    static class TestApplication {
    }
}

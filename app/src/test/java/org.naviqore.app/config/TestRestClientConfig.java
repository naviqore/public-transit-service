package org.naviqore.app.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Test configuration for RestClient used in integration tests.
 */
@TestConfiguration
public class TestRestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}


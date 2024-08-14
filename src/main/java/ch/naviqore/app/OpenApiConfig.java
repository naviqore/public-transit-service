package ch.naviqore.app;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private final String version;

    @Autowired
    public OpenApiConfig(BuildProperties buildProperties) {
        version = buildProperties.getVersion();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title("Naviqore - Public Transit Service API")
                .version(version)
                .description("API for Public Transit Routing System"));
    }

}

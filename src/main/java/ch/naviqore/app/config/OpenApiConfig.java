package ch.naviqore.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private final String name;
    private final String version;
    private final String description;

    @Autowired
    public OpenApiConfig(BuildProperties buildProperties) {
        name = "Naviqore - " + buildProperties.getName() + " API";
        version = buildProperties.getVersion();
        description = buildProperties.get("description");
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title(name).version(version).description(description));
    }

}

package org.naviqore.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String TITLE = "Naviqore Public Transit Service API";

    private static final Map<String, String> COORDINATE_DESCRIPTIONS = Map.of("latitude",
            "Latitude in WGS84 degrees, from -90 to 90.", "longitude", "Longitude in WGS84 degrees, from -180 to 180.");

    private final @Nullable String version;
    private final @Nullable String description;

    public OpenApiConfig(BuildProperties buildProperties) {
        version = buildProperties.getVersion();
        description = buildProperties.get("description");
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title(TITLE).version(version).description(description));
    }

    /**
     * Describes the coordinates of the answers, whose class in naviqore-utils carries no OpenAPI annotations.
     */
    @Bean
    public OpenApiCustomizer coordinateDescriptions() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> coordinate = openApi.getComponents().getSchemas().get(GeoCoordinate.class.getSimpleName());
            if (coordinate == null || coordinate.getProperties() == null) {
                return;
            }
            coordinate.setDescription("A location in WGS84 degrees.");
            COORDINATE_DESCRIPTIONS.forEach((name, text) -> {
                Schema<?> property = coordinate.getProperties().get(name);
                if (property != null) {
                    property.setDescription(text);
                }
            });
        };
    }

}

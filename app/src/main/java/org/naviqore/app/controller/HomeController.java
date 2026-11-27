package org.naviqore.app.controller;

import org.jspecify.annotations.Nullable;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Validity;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * The HTML home page at the root path. It shows the version of the service, its health status and the validity of the
 * loaded schedule, and links to the API documentation and the actuator endpoints that are enabled. The beans of the
 * health endpoint, of the actuator's web endpoints and of springdoc are absent where these are disabled.
 */
@Controller
@ConditionalOnBooleanProperty(name = "naviqore.home-page.enabled", matchIfMissing = true)
public class HomeController {

    private static final EndpointId METRICS_ENDPOINT_ID = EndpointId.of("metrics");

    private final @Nullable String version;
    private final @Nullable String description;
    private final ScheduleInformationService service;
    private final @Nullable HealthEndpoint healthEndpoint;
    private final Links links;

    public HomeController(BuildProperties buildProperties, ScheduleInformationService service,
                          @Nullable HealthEndpoint healthEndpoint, @Nullable PathMappedEndpoints actuatorEndpoints,
                          @Nullable SpringDocConfigProperties springDocProperties,
                          @Nullable SwaggerUiConfigProperties swaggerUiProperties, Environment environment) {
        this.version = buildProperties.getVersion();
        String desc = buildProperties.get("description");
        this.description = (desc != null && desc.endsWith(".")) ? desc.substring(0, desc.length() - 1) : desc;
        this.service = service;
        this.healthEndpoint = healthEndpoint;
        this.links = Links.of(actuatorEndpoints, springDocProperties, swaggerUiProperties, environment);
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("productName", "Naviqore");
        model.addAttribute("serviceName", "Public Transit Service");
        model.addAttribute("version", version);
        model.addAttribute("description", description);
        if (healthEndpoint != null) {
            model.addAttribute("health", HealthStatus.of(healthEndpoint.health().getStatus()));
        }
        model.addAttribute("schedule", Schedule.of(service.getValidity(), LocalDate.now(ZoneId.systemDefault())));
        model.addAttribute("links", links);

        return "home";
    }

    /**
     * The health status of the service as shown: its label, and its tone, which is up, down or unknown.
     */
    public record HealthStatus(String label, String tone) {

        static HealthStatus of(Status status) {
            String tone = switch (status.getCode()) {
                case "UP" -> "up";
                case "DOWN", "OUT_OF_SERVICE" -> "down";
                default -> "unknown";
            };
            String label = StringUtils.capitalize(status.getCode().toLowerCase(Locale.ROOT).replace('_', ' '));

            return new HealthStatus(label, tone);
        }
    }

    /**
     * The validity of the loaded schedule, and whether it includes today in the time zone of the server.
     */
    public record Schedule(LocalDate startDate, LocalDate endDate, boolean validToday) {

        static Schedule of(Validity validity, LocalDate today) {
            return new Schedule(validity.getStartDate(), validity.getEndDate(), validity.isWithin(today));
        }
    }

    /**
     * The paths of the pages the home page links to. A path is null where its target is disabled, or served on the
     * management port, whose host and port the page does not know.
     */
    public record Links(@Nullable String apiConsole, @Nullable String openApi, @Nullable String health,
                        @Nullable String metrics) {

        static Links of(@Nullable PathMappedEndpoints actuatorEndpoints,
                        @Nullable SpringDocConfigProperties springDocProperties,
                        @Nullable SwaggerUiConfigProperties swaggerUiProperties, Environment environment) {
            String apiConsole = null;
            String openApi = null;
            if (springDocProperties != null && !springDocProperties.isUseManagementPort()) {
                apiConsole = swaggerUiProperties != null ? swaggerUiProperties.getPath() : null;
                openApi = springDocProperties.getApiDocs().getPath();
            }
            String health = null;
            String metrics = null;
            if (actuatorEndpoints != null && ManagementPortType.get(environment) == ManagementPortType.SAME) {
                health = actuatorEndpoints.getPath(HealthEndpoint.ID);
                metrics = actuatorEndpoints.getPath(METRICS_ENDPOINT_ID);
            }

            return new Links(apiConsole, openApi, health, metrics);
        }

        public boolean isEmpty() {
            return apiConsole == null && openApi == null && health == null && metrics == null;
        }
    }
}

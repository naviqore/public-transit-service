package org.naviqore.app.controller;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Validity;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The home page rendered from its template, with the fake service and the beans of the actuator and of springdoc that
 * are present where their targets are enabled.
 */
class HomeControllerTest {

    private final FakePublicTransitService service = new FakePublicTransitService();
    private final MockEnvironment environment = new MockEnvironment();

    private static BuildProperties buildProperties() {
        Properties entries = new Properties();
        entries.setProperty("version", "5.0.0");

        return new BuildProperties(entries);
    }

    private static HealthEndpoint healthEndpoint(Status status) {
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("service", (HealthIndicator) () -> Health.status(status).build());
        HealthEndpointGroup primaryGroup = mock(HealthEndpointGroup.class);
        when(primaryGroup.isMember("service")).thenReturn(true);
        when(primaryGroup.getStatusAggregator()).thenReturn(StatusAggregator.getDefault());

        return new HealthEndpoint(registry, null, HealthEndpointGroups.of(primaryGroup, Map.of()), null);
    }

    private static PathMappedEndpoints actuatorEndpoints(String healthPath, @Nullable String metricsPath) {
        PathMappedEndpoints endpoints = mock(PathMappedEndpoints.class);
        when(endpoints.getPath(EndpointId.of("health"))).thenReturn(healthPath);
        when(endpoints.getPath(EndpointId.of("metrics"))).thenReturn(metricsPath);

        return endpoints;
    }

    private static String render(HomeController controller) throws Exception {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        viewResolver.setCharacterEncoding("UTF-8");

        return MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(viewResolver)
                .build()
                .perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private HomeController controller(ScheduleInformationService scheduleService,
                                      @Nullable HealthEndpoint healthEndpoint,
                                      @Nullable PathMappedEndpoints actuatorEndpoints,
                                      @Nullable SpringDocConfigProperties springDocProperties,
                                      @Nullable SwaggerUiConfigProperties swaggerUiProperties) {
        return new HomeController(buildProperties(), scheduleService, healthEndpoint, actuatorEndpoints,
                springDocProperties, swaggerUiProperties, environment);
    }

    @Test
    void showsTheVersionTheHealthStatusAndTheScheduleValidity() throws Exception {
        Validity validity = service.getValidity();

        String page = render(controller(service, healthEndpoint(Status.OUT_OF_SERVICE), null, null, null));

        assertThat(page).contains("5.0.0", "Out of service", "tone-down", validity.getStartDate().toString(),
                validity.getEndDate().toString()).doesNotContain("Not valid today");
    }

    @Test
    void flagsAScheduleThatIsNotValidToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Validity expired = mock(Validity.class);
        when(expired.getStartDate()).thenReturn(today.minusDays(30));
        when(expired.getEndDate()).thenReturn(today.minusDays(1));
        when(expired.isWithin(today)).thenReturn(false);
        FakePublicTransitService expiredService = new FakePublicTransitService() {
            @Override
            public Validity getValidity() {
                return expired;
            }
        };

        String page = render(controller(expiredService, null, null, null, null));

        assertThat(page).contains(today.minusDays(1).toString(), "Not valid today");
    }

    @Test
    void linksTheEnabledTargetsAtTheirPaths() throws Exception {
        SpringDocConfigProperties springDocProperties = new SpringDocConfigProperties();
        springDocProperties.getApiDocs().setPath("/docs/openapi");
        SwaggerUiConfigProperties swaggerUiProperties = new SwaggerUiConfigProperties();
        swaggerUiProperties.setPath("/docs/console");

        String page = render(controller(service, null, actuatorEndpoints("/manage/health", "/manage/metrics"),
                springDocProperties, swaggerUiProperties));

        assertThat(page).contains("href=\"/docs/console\"", "href=\"/docs/openapi\"", "href=\"/manage/health\"",
                "href=\"/manage/metrics\"");
    }

    @Test
    void omitsTheLinksOfDisabledTargets() throws Exception {
        // the OpenAPI document but not the Swagger UI, and the health endpoint but not the metrics endpoint
        String page = render(controller(service, null, actuatorEndpoints("/actuator/health", null),
                new SpringDocConfigProperties(), null));

        assertThat(page).contains("href=\"/v3/api-docs\"", "href=\"/actuator/health\"")
                .doesNotContain("API Console", "Metrics");
    }

    @Test
    void omitsTheLinksServedOnTheManagementPort() throws Exception {
        environment.setProperty("management.server.port", "9090");
        SpringDocConfigProperties springDocProperties = new SpringDocConfigProperties();
        springDocProperties.setUseManagementPort(true);

        String page = render(controller(service, null, actuatorEndpoints("/actuator/health", "/actuator/metrics"),
                springDocProperties, new SwaggerUiConfigProperties()));

        assertThat(page).doesNotContain("<nav");
    }

    @Test
    void omitsTheNavigationAndTheHealthStatusWhereTheirTargetsAreDisabled() throws Exception {
        String page = render(controller(service, null, null, null, null));

        assertThat(page).doesNotContain("<nav", "Status");
    }

    @Test
    void showsNoInformationAboutTheOperatingSystemOrJava() throws Exception {
        String page = render(controller(service, healthEndpoint(Status.UP), null, null, null));

        assertThat(page).doesNotContain(System.getProperty("os.name"), System.getProperty("os.arch"),
                System.getProperty("java.version"), "Built");
    }
}

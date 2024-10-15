package ch.naviqore.app;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate template;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void contextLoads() {
        assertThat(template).isNotNull();
    }

    @Nested
    class ScheduleEndpoints {

        @Test
        void nearestStops_shouldReturn200() {
            String url = baseUrl() + "/schedule/stops/nearest?latitude=36&longitude=-116&maxDistance=500000";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void autocompleteStopName_shouldReturn200() {
            String url = baseUrl() + "/schedule/stops/autocomplete?query=Sta&searchType=STARTS_WITH&limit=100";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void autocompleteStopName_invalidSearchType_shouldReturn400() {
            String url = baseUrl() + "/schedule/stops/autocomplete?query=Sta&searchType=NOT_A_TYPE&limit=100";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void departureAtStop_shouldReturn200() {
            String url = baseUrl() + "/schedule/stops/STAGECOACH/departures?departureDateTime=2010-01-01T01:10:01&limit=3";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void departureAtStop_invalidStopId_shouldReturn404() {
            String url = baseUrl() + "/schedule/stops/NOT_EXISTING/departures?departureDateTime=2010-01-01T01:10:01&limit=3";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class RoutingEndpoints {

        @Test
        void connectionBetweenStops_shouldReturn200() {
            String url = baseUrl() + "/routing/connections?sourceStopId=STAGECOACH&targetStopId=NANAA&dateTime=2010-01-01T01:10:01";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void connection_invalidSourceStopId_shouldReturn404() {
            String url = baseUrl() + "/routing/connections?sourceStopId=NOT_EXISTING&targetStopId=NANAA&dateTime=2010-01-01T01:10:01";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void isolineFromStop_shouldReturn200() {
            String url = baseUrl() + "/routing/isolines?sourceStopId=STAGECOACH&maxTransferNumber=3&maxTravelTime=7200&dateTime=2010-01-01T01:10:01";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void isoline_invalidSourceStopId_shouldReturn404() {
            String url = baseUrl() + "/routing/isolines?sourceStopId=NOT_EXISTING&maxTransferNumber=3&maxTravelTime=7200&dateTime=2010-01-01T01:10:01";
            ResponseEntity<String> response = template.getForEntity(url, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

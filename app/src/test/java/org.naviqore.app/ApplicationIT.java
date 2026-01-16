package org.naviqore.app;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.app.config.TestRestClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gtfs.static.uri=../libs/gtfs-sample/src/main/resources/org/naviqore/gtfs/schedule/sample-feed-1.zip"})
@Import(TestRestClientConfig.class)
class ApplicationIT {

    @Autowired
    private TestRestRequest request;

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
        assertThat(request).isNotNull();
    }

    @Nested
    class ScheduleEndpoints {

        @Test
        void nearestStops_shouldReturn200() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/nearest")
                    .addQueryParameter("latitude", "36")
                    .addQueryParameter("longitude", "-116")
                    .addQueryParameter("maxDistance", "500000")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void autocompleteStopName_shouldReturn200() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/autocomplete")
                    .addQueryParameter("query", "Sta")
                    .addQueryParameter("searchType", "STARTS_WITH")
                    .addQueryParameter("limit", "100")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void autocompleteStopName_invalidSearchType_shouldReturn400() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/autocomplete")
                    .addQueryParameter("query", "Sta")
                    .addQueryParameter("searchType", "NOT_A_TYPE")
                    .addQueryParameter("limit", "100")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void departureAtStop_shouldReturn200() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/STAGECOACH/times")
                    .addQueryParameter("from", "2010-01-01T01:10:01Z")
                    .addQueryParameter("limit", "3")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void departureAtStop_invalidStopId_shouldReturn404() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/NOT_EXISTING/departures")
                    .addQueryParameter("departureDateTime", "2010-01-01T01:10:01Z")
                    .addQueryParameter("limit", "3")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void departureAtStop_outsideScheduleValidity_shouldReturn400() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/schedule/stops/STAGECOACH/times")
                    .addQueryParameter("from", "2000-01-01T01:10:01Z")
                    .addQueryParameter("limit", "3")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class RoutingEndpoints {

        @Test
        void connectionBetweenStops_shouldReturn200() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/connections")
                    .addQueryParameter("sourceStopId", "STAGECOACH")
                    .addQueryParameter("targetStopId", "NANAA")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void connection_invalidMinTransferTime_shouldReturn400() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/connections")
                    .addQueryParameter("sourceStopId", "STAGECOACH")
                    .addQueryParameter("targetStopId", "NANAA")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .addQueryParameter("minTransferTime", "-1")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void connection_invalidSourceStopId_shouldReturn404() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/connections")
                    .addQueryParameter("sourceStopId", "NOT_EXISTING")
                    .addQueryParameter("targetStopId", "NANAA")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void connection_invalidTargetStopId_shouldReturn404() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/connections")
                    .addQueryParameter("sourceStopId", "STAGECOACH")
                    .addQueryParameter("targetStopId", "NOT_EXISTING")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void isolineFromStop_shouldReturn200() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/isolines")
                    .addQueryParameter("sourceStopId", "STAGECOACH")
                    .addQueryParameter("maxTransferNumber", "3")
                    .addQueryParameter("maxTravelTime", "7200")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void isoline_invalidSourceStopId_shouldReturn404() {
            ResponseEntity<String> response = request.setPort(port)
                    .setEndpoint("/routing/isolines")
                    .addQueryParameter("sourceStopId", "NOT_EXISTING")
                    .addQueryParameter("maxTransferNumber", "3")
                    .addQueryParameter("maxTravelTime", "7200")
                    .addQueryParameter("dateTime", "2010-01-01T01:10:01Z")
                    .get(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

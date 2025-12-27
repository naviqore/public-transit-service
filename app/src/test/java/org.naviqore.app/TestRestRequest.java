package org.naviqore.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class TestRestRequest {

    private final RestClient restClient;
    private UriComponentsBuilder uriBuilder;

    @Autowired
    public TestRestRequest(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.defaultStatusHandler(statusCode -> true, (request, response) -> {
            // don't throw exceptions, let tests handle the response
        }).build();
    }

    public TestRestRequest setPort(int port) {
        this.uriBuilder = UriComponentsBuilder.fromUriString(String.format("http://localhost:%d", port));
        return this;
    }

    public TestRestRequest setEndpoint(String path) {
        if (this.uriBuilder == null) {
            throw new IllegalStateException("Port must be set before setting the endpoint");
        }
        this.uriBuilder.path(path);

        return this;
    }

    public TestRestRequest addQueryParameter(String name, String value) {
        if (this.uriBuilder == null) {
            throw new IllegalStateException("Port must be set before adding parameters");
        }
        this.uriBuilder.queryParam(name, value);

        return this;
    }

    public <T> ResponseEntity<T> get(Class<T> responseType) {
        if (this.uriBuilder == null) {
            throw new IllegalStateException("Port and endpoint must be set before executing the request");
        }

        String url = this.uriBuilder.toUriString();
        log.info("Sending GET request to URL: {}", url);
        ResponseEntity<T> response = restClient.get().uri(url).retrieve().toEntity(responseType);
        log.info("Received response with status: {}", response.getStatusCode());

        return response;
    }

}

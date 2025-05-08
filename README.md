# Public Transit Service

Public transit schedule information and connection routing service based on GTFS data and the RAPTOR algorithm.

## Features

- **GTFS Integration:** Integrates public transit schedules using the General Transit Feed Specification (GTFS)
  format [1].
- **Efficient Routing:** Utilizes the RAPTOR (Round-based Public Transit Routing) algorithm for optimized transit
  routes [2].
- **RESTful API:** Provides an API for querying transit schedule information and connections.

## Installation

This project is structured as a Maven multi-module project under `org.naviqore`:

- **naviqore-app**: Spring REST application that exposes the public transit service library through HTTP endpoints.
- **naviqore-libs**: Parent POM for Naviqore public transit libraries.
    - **naviqore-public-transit-service**: Public transit schedule queries and connection routing service.
    - **naviqore-raptor**: Implementation of the RAPTOR algorithm.
    - **naviqore-gtfs**: Implementation of the General Transit Feed Specification.
    - **naviqore-gtfs-sample**: Access to GTFS sample datasets for testing purposes.
    - **naviqore-utils**: Common utility classes shared across the Naviqore libraries.
- **naviqore-benchmark**: Benchmarking the performance of the Naviqore libraries.

### Build Locally

Follow the steps below to build and run the project locally:

1. Clone the repository:
   ```bash
   git clone https://github.com/naviqore/public-transit-service.git
   cd public-transit-service
   ```

2. Build the project using Maven:

   ```bash
   ./mvnw clean install
   ```

3. Run the application module:

   ```bash
   export GTFS_STATIC_URI=<URL or PATH>
   ./mvnw spring-boot:run -pl app
   ```

### Maven Central

The project's library modules are available in
the [Maven Central Repository](https://central.sonatype.com/namespace/org.naviqore) and can be added to Maven projects
as needed. For example, to use the public transit service, include the following dependency:

```xml

<dependency>
    <groupId>org.naviqore</groupId>
    <artifactId>naviqore-public-transit-service</artifactId>
    <version>x.x.x</version> <!-- replace with latest version -->
</dependency>
```

A simple working example of how to use the public transit service in your Java application:

```java
public class PublicTransitServiceExample {
    public static final String GTFS_STATIC_URI = "https://github.com/google/transit/raw/refs/heads/master/gtfs/spec/en/examples/sample-feed-1.zip";
    public static final String ORIG_STOP_ID = "STAGECOACH";
    public static final GeoCoordinate DEST_LOCATION = new GeoCoordinate(36.9149, -116.7614);
    public static final LocalDateTime DEPARTURE_TIME = LocalDateTime.of(2007, 1, 1, 0, 0, 0);

    public static void main(
            String[] args) throws IOException, InterruptedException, StopNotFoundException, ConnectionRoutingException {

        GtfsScheduleRepository repo = () -> {
            new FileDownloader(GTFS_STATIC_URI).downloadTo(Path.of("."), "gtfs.zip", true);
            return new GtfsScheduleReader().read("gtfs.zip");
        };

        ServiceConfig serviceConfig = ServiceConfig.builder().gtfsScheduleRepository(repo).build();
        PublicTransitService service = new PublicTransitServiceFactory(serviceConfig).create();

        Stop orig = service.getStopById(ORIG_STOP_ID);
        ConnectionQueryConfig queryConfig = ConnectionQueryConfig.builder().build();
        List<Connection> connections = service.getConnections(orig, DEST_LOCATION, DEPARTURE_TIME, TimeType.DEPARTURE,
                queryConfig);
    }
}
```

## Deployment

To deploy `public-transit-service` using Docker, run the following command:

```bash
docker run -p 8080:8080 -e GTFS_STATIC_URI=<URL or PATH> ghcr.io/naviqore/public-transit-service:latest
```

For more configuration options, refer to
the [application.properties](app/src/main/resources/application.properties) file.

Access the service at http://localhost:8080 to explore schedules and query transit connections.

## Contributing

We welcome contributions! See our [Contribution Guidelines](CONTRIBUTING.md) for how to submit bug reports, feature
requests, and pull requests.

## License

This project is licensed under the MIT license. See the [LICENSE](LICENSE) file for details.

## References

[1] General Transit Feed Specification. (n.d.). Retrieved May 25, 2024, from [https://gtfs.org/](https://gtfs.org/)

[2] Delling, D., Pajor, T., & Werneck, R. F. (2012). Round-Based Public Transit Routing. In *2012 Proceedings of the
Meeting on Algorithm Engineering and Experiments (ALENEX)* (pp. 130-140).
SIAM. [https://doi.org/10.1137/1.9781611972924.13](https://epubs.siam.org/doi/abs/10.1137/1.9781611972924.13)

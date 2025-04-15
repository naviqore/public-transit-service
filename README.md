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

- **public-transit-service**: Library for transit schedule queries and connection routing using GTFS data and the
  RAPTOR algorithm.
- **public-transit-service-app**: Spring Boot application that exposes the public transit service via RESTful APIs.
- **public-transit-service-benchmark**: Benchmarking the performance of the public transit service library.

Follow the steps below to build and run the project locally:

1. Clone the repository:
   ```bash
   git clone https://github.com/naviqore/public-transit-service.git
   cd public-transit-service
   ```

2. Build the project using Maven:

   ```bash
   mvn clean install
   ```

3. Run the application module:

   ```bash
   export GTFS_STATIC_URI=<URL or PATH>
   mvn spring-boot:run -pl public-transit-service-app
   ```

## Deployment

To deploy `public-transit-service` using Docker, run the following command:

```bash
docker run -p 8080:8080 -e GTFS_STATIC_URI=<URL or PATH> ghcr.io/naviqore/public-transit-service:latest
```

For more configuration options, refer to
the [application.properties](public-transit-service-app/src/main/resources/application.properties) file.

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

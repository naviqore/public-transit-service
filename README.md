# Public Transit Service

Public transit schedule information and connection routing service based on GTFS data and the RAPTOR algorithm.

## Features

- **GTFS Integration:** Integrates public transit schedules using the General Transit Feed Specification (GTFS)
  format [1].
- **Efficient Routing:** Utilizes the RAPTOR (Round-based Public Transit Routing) algorithm for optimized transit
  routes [2].
- **RESTful API:** Provides an API for querying transit schedule information and connections.

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/naviqore/public-transit-service.git
   cd public-transit-service
   ```

2. Build the project using Maven:

   ```bash
   mvn clean install
   ```

3. Run the application:

   ```bash
    mvn spring-boot:run
   ```

## Deployment

To deploy `public-transit-service` using Docker, run the following command:

```bash
docker run -p 8080:8080 -e GTFS_STATIC_URI=<URL or PATH> ghcr.io/naviqore/public-transit-service:latest
```

For more configuration options, refer to the [application.properties](src/main/resources/application.properties) file.

## Usage

Access the service at http://localhost:8080 to explore schedules and query transit connections.

## License

This project is licensed under the GPL-3.0 license. See the [LICENSE](LICENSE) file for details.

## References

[1] General Transit Feed Specification. (n.d.). Retrieved May 25, 2024, from [https://gtfs.org/](https://gtfs.org/)

[2] Delling, D., Pajor, T., & Werneck, R. F. (2012). Round-Based Public Transit Routing. In *2012 Proceedings of the
Meeting on Algorithm Engineering and Experiments (ALENEX)* (pp. 130-140).
SIAM. [https://doi.org/10.1137/1.9781611972924.13](https://epubs.siam.org/doi/abs/10.1137/1.9781611972924.13)

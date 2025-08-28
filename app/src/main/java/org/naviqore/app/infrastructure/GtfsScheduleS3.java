package org.naviqore.app.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.repo.GtfsScheduleRepository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repository for loading a static GTFS feed from an S3 bucket.
 * <p>
 * Expected format for the S3 URI: {@code s3://bucket-name/path/to/gtfs.zip}. AWS credentials and region must be
 * provided via standard environment variables. Supports S3-compatible endpoints like MinIO via
 * {@code AWS_ENDPOINT_URL_S3} and {@code AWS_S3_FORCE_PATH_STYLE}.
 */
@Slf4j
@RequiredArgsConstructor
public class GtfsScheduleS3 implements GtfsScheduleRepository {

    private static final String AWS_REGION = "AWS_REGION";
    private static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";
    private static final String AWS_ENDPOINT_URL_S_3 = "AWS_ENDPOINT_URL_S3";
    private static final String AWS_S_3_FORCE_PATH_STYLE = "AWS_S3_FORCE_PATH_STYLE";
    private static final String TMP_DIRECTORY_PREFIX = "tmp_gtfs_";
    private static final String FILE_NAME = "gtfs.zip";

    private final String s3Uri;

    @Override
    public GtfsSchedule get() throws IOException, InterruptedException {
        URI uri = URI.create(s3Uri);
        String bucket = uri.getHost();
        String key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();

        Path tempDir = Files.createTempDirectory(TMP_DIRECTORY_PREFIX);
        Path filePath = tempDir.resolve(FILE_NAME);

        // setup default builder
        S3ClientBuilder s3ClientBuilder = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build());

        // configure region from environment variable
        String region = System.getenv(AWS_REGION);
        if (region != null && !region.isBlank()) {
            s3ClientBuilder.region(Region.of(region));
        }

        // configure endpoint override; use the S3-specific variable first, then fall back to the generic one
        String endpointOverride = System.getenv(AWS_ENDPOINT_URL_S_3);
        if (endpointOverride == null || endpointOverride.isBlank()) {
            endpointOverride = System.getenv(AWS_ENDPOINT_URL);
        }
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            log.debug("S3 endpoint override is set to: {}", endpointOverride);
            s3ClientBuilder.endpointOverride(URI.create(endpointOverride));
        }

        // force path-style access if requested
        String forcePathStyle = System.getenv(AWS_S_3_FORCE_PATH_STYLE);
        if ("true".equalsIgnoreCase(forcePathStyle)) {
            log.info("S3 path-style access is enabled.");
            s3ClientBuilder.forcePathStyle(true);
        }

        try (S3Client s3 = s3ClientBuilder.build()) {
            log.info("Downloading file: {}", s3Uri);
            s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toFile(filePath));

            return new GtfsScheduleReader().read(filePath.toString());

        } finally {
            Files.deleteIfExists(filePath);
            Files.deleteIfExists(tempDir);
        }
    }
}

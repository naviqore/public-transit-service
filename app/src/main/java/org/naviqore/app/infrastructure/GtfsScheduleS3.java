package org.naviqore.app.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.repo.GtfsScheduleRepository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repository for loading a static GTFS feed from an S3 bucket.
 * <p>
 * Expected format for the S3 URI: {@code s3://bucket-name/path/to/gtfs.zip}. AWS credentials and region must be
 * provided via environment variables: {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}, {@code AWS_REGION}.
 */
@Slf4j
@RequiredArgsConstructor
public class GtfsScheduleS3 implements GtfsScheduleRepository {

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

        try (S3Client s3 = S3Client.builder().credentialsProvider(DefaultCredentialsProvider.create()).build()) {

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

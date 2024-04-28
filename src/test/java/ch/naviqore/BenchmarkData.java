package ch.naviqore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Benchmark data provider
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
final class BenchmarkData {
    private static final Path DATA_DIRECTORY = Path.of("benchmark/input");
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static void downloadFile(String fileURL, Path directory,
                                    String fileName) throws IOException, InterruptedException {
        Path filePath = directory.resolve(fileName);
        if (Files.notExists(filePath)) {
            log.info("Downloading file: {}", fileURL);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fileURL)).build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(filePath));

            if (response.statusCode() == 200) {
                log.info("Dataset downloaded successfully to {}", filePath);
            } else {
                log.error("Download failed, HTTP status code: {}", response.statusCode());
                Files.deleteIfExists(filePath);
            }
        } else {
            log.debug("Dataset already exists: {}", filePath);
        }
    }

    private static void ensureDirectory() throws IOException {
        if (Files.notExists(DATA_DIRECTORY)) {
            Files.createDirectories(DATA_DIRECTORY);
            log.info("Directory created: {}", DATA_DIRECTORY);
        }
    }

    public static String get(Dataset dataset) throws IOException, InterruptedException {
        ensureDirectory();
        String fileName = dataset.getFileName();
        Path filePath = DATA_DIRECTORY.resolve(fileName);
        if (Files.notExists(filePath)) {
            downloadFile(dataset.getUrl(), DATA_DIRECTORY, fileName);
        } else {
            log.info("Returning path to existing file: {}", filePath);
        }
        return filePath.toString();
    }

    /**
     * GTFS schedule datasets
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public enum Dataset {
        SWITZERLAND("https://opentransportdata.swiss/en/dataset/timetable-2024-gtfs2020/permalink"),
        GERMANY("https://download.gtfs.de/germany/free/latest.zip");

        private final String url;

        public String getFileName() {
            return this.name().toLowerCase() + ".zip";
        }
    }
}

package ch.naviqore.utils.network;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Downloading files from a specified URL. Ensures that the directory exists before downloading and provides an option
 * to overwrite existing files.
 */
@Log4j2
public final class FileDownloader {

    private final HttpClient httpClient;
    private final URI url;

    /**
     * Constructs a new FileDownloader for the specified URL.
     *
     * @param url the URL to download the file from
     */
    public FileDownloader(String url) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), URI.create(url));
    }

    /**
     * Constructs a new FileDownloader for the specified URL and HttpClient.
     *
     * @param httpClient the HttpClient to use for the request
     * @param url        the URI to download the file from
     */
    FileDownloader(HttpClient httpClient, URI url) {
        this.httpClient = httpClient;
        this.url = url;
    }

    /**
     * Downloads a file to the specified directory with the given file name. If the file already exists, it will be
     * overwritten if the overwrite parameter is true. Ensures that the directory exists before downloading.
     *
     * @param directory the directory to download the file to
     * @param fileName  the name of the file to be saved
     * @param overwrite whether to overwrite the file if it already exists
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the download is interrupted
     */
    public void downloadTo(Path directory, String fileName,
                           boolean overwrite) throws IOException, InterruptedException {
        ensureDirectory(directory);
        Path filePath = directory.resolve(fileName);

        if (shouldDownload(filePath, overwrite)) {
            downloadFile(filePath, overwrite);
        } else {
            log.debug("Dataset already exists: {}", filePath);
        }
    }

    // checks if the file should be downloaded based on its existence and the overwrite flag
    private boolean shouldDownload(Path filePath, boolean overwrite) {
        return Files.notExists(filePath) || overwrite;
    }

    // downloads the file from the URL to the specified path
    private void downloadFile(Path filePath, boolean overwrite) throws IOException, InterruptedException {
        if (Files.exists(filePath) && overwrite) {
            log.info("Overwriting existing file: {}", filePath);
            Files.delete(filePath);
        }
        log.info("Downloading file: {}", url);
        HttpRequest request = HttpRequest.newBuilder().uri(url).build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(filePath));

        if (response.statusCode() == 200) {
            log.info("Dataset downloaded successfully to: {}", filePath);
        } else {
            log.error("Download failed, HTTP status code: {}", response.statusCode());
            Files.deleteIfExists(filePath);
        }
    }

    // ensures that the specified directory exists
    private void ensureDirectory(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
            log.info("Directory created: {}", directory);
        }
    }
}

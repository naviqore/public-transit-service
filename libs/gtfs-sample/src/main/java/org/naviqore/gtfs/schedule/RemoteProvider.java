package org.naviqore.gtfs.schedule;

import lombok.extern.slf4j.Slf4j;
import org.naviqore.utils.network.FileDownloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
class RemoteProvider implements DataProvider {

    private final String url;

    public RemoteProvider(String url) {
        this.url = url;
    }

    @Override
    public File getZip(Path dir, String name) throws IOException {
        log.info("Loading remote dataset: {}", url);

        Files.createDirectories(dir);
        Path zipPath = dir.resolve(name + ".zip");

        if (!Files.exists(zipPath)) {
            FileDownloader downloader = new FileDownloader(url);
            try {
                downloader.downloadTo(dir, zipPath.getFileName().toString(), false);
            } catch (InterruptedException e) {
                // restore interrupted state and wrap exception in IOException
                Thread.currentThread().interrupt();
                throw new IOException("Download was interrupted", e);
            }
        } else {
            log.debug("Dataset already exists locally: {}", zipPath);
        }

        return zipPath.toFile();
    }
}

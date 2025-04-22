package org.naviqore.gtfs.schedule;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
class LocalProvider implements DataProvider {

    private final String resourcePath;

    public LocalProvider(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public File getZip(Path dir, String name) throws IOException {
        log.info("Loading local dataset: {}", resourcePath);
        Files.createDirectories(dir);
        Path zipPath = dir.resolve(name + ".zip");

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            Files.copy(is, zipPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied local resource to: {}", zipPath);
        }

        return zipPath.toFile();
    }
}

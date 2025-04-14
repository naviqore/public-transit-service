package org.naviqore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.utils.network.FileDownloader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Benchmark data provider
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class BenchmarkData {

    private static final Path DATA_DIRECTORY = Path.of("benchmark/input");

    public static String get(Dataset dataset) throws IOException, InterruptedException {
        String fileName = dataset.getFileName();
        Path filePath = DATA_DIRECTORY.resolve(fileName);
        FileDownloader downloader = new FileDownloader(dataset.getUrl());

        downloader.downloadTo(DATA_DIRECTORY, fileName, false);

        return filePath.toString();
    }

    /**
     * GTFS schedule datasets
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public enum Dataset {
        SWITZERLAND("https://opentransportdata.swiss/en/dataset/timetable-2024-gtfs2020/permalink"),
        GERMANY("https://download.gtfs.de/germany/free/latest.zip"),
        ZURICH_TRAMS("https://connolly.ch/zuerich-trams.zip"),
        ZURICH("https://connolly.ch/zvv.zip");

        private final String url;

        public String getFileName() {
            return this.name().toLowerCase() + ".zip";
        }
    }
}

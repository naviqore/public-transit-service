package org.naviqore.gtfs.schedule;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * GTFS datasets, local or remote.
 */
@RequiredArgsConstructor
public enum GtfsScheduleDataset {

    SAMPLE_FEED_1(new LocalProvider("org/naviqore/gtfs/schedule/sample-feed-1.zip")),
    SWITZERLAND(new RemoteProvider("https://data.opentransportdata.swiss/dataset/timetable-2025-gtfs2020/permalink")),
    GERMANY(new RemoteProvider("https://download.gtfs.de/germany/free/latest.zip")),
    ZURICH_TRAMS(new RemoteProvider("https://connolly.ch/zuerich-trams.zip")),
    ZURICH(new RemoteProvider("https://connolly.ch/zvv.zip"));

    private final DataProvider provider;

    /**
     * Returns the GTFS ZIP file, downloading it first if needed.
     *
     * @param dir the directory where the ZIP file will be stored
     */
    public File getZip(Path dir) throws IOException {
        return provider.getZip(dir, name().toLowerCase());
    }

    /**
     * Returns the unzipped GTFS, downloading it first if needed.
     *
     * @param dir the directory into which the dataset will be extracted
     */
    public File getUnzipped(Path dir) throws IOException {
        return provider.getUnzipped(dir, name().toLowerCase());
    }
}

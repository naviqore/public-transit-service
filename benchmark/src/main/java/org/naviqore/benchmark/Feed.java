package org.naviqore.benchmark;

import org.naviqore.gtfs.schedule.GtfsScheduleDataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A GTFS feed to benchmark on: a dataset of {@link GtfsScheduleDataset}, downloaded once into the input directory, or a
 * ZIP file. Its name is the name of the directory of the outputs.
 *
 * @param name the name of the dataset in lower case, or the name of the file without its extension
 * @param path the ZIP file of the feed
 */
public record Feed(String name, Path path) {

    private static final Path INPUT_DIRECTORY = Path.of("benchmark/input");

    /**
     * The feed of a dataset by its name, or of a ZIP file by its path.
     */
    public static Feed of(String datasetOrPath) throws IOException {
        for (GtfsScheduleDataset dataset : GtfsScheduleDataset.values()) {
            if (dataset.name().equalsIgnoreCase(datasetOrPath)) {
                return new Feed(dataset.name().toLowerCase(Locale.ROOT), dataset.getZip(INPUT_DIRECTORY).toPath());
            }
        }

        Path path = Path.of(datasetOrPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "The feed " + datasetOrPath + " is neither a dataset of GtfsScheduleDataset nor a file");
        }

        return new Feed(path.getFileName().toString().replaceFirst("\\.zip$", ""), path);
    }

}

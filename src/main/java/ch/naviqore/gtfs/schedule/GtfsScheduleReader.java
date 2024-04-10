package ch.naviqore.gtfs.schedule;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A reader for General Transit Feed Specification (GTFS) schedule data
 * <p>
 * This class provides functionality to read GTFS data from either a directory containing individual GTFS CSV files or a
 * ZIP archive containing the GTFS dataset.
 * <p>
 * Supported GTFS files are enumerated in {@link GtfsFile}, and this reader will attempt to parse each specified file
 * into a list of {@link CSVRecord} objects.
 * <p>
 * Note: The GTFS data has to strictly follow the standard GTFS file naming and format. Non-standard files will not be
 * read.
 *
 * @author munterfi
 */
@NoArgsConstructor
@Log4j2
public class GtfsScheduleReader {

    private static final String ZIP_FILE_EXTENSION = ".zip";

    /**
     * Standard GTFS file types and their corresponding file names.
     */
    @RequiredArgsConstructor
    @Getter
    public enum GtfsFile {
        AGENCY("agency.txt"),
        CALENDAR_DATES("calendar_dates.txt"),
        CALENDAR("calendar.txt"),
        FARE_ATTRIBUTES("fare_attributes.txt"),
        FARE_RULES("fare_rules.txt"),
        FREQUENCIES("frequencies.txt"),
        ROUTES("routes.txt"),
        SHAPES("shapes.txt"),
        STOP_TIMES("stop_times.txt"),
        STOPS("stops.txt"),
        TRIPS("trips.txt");

        private final String fileName;
    }

    public Map<GtfsFile, List<CSVRecord>> read(String path) throws IOException {
        File file = new File(path);
        Map<GtfsFile, List<CSVRecord>> records = new HashMap<>();

        if (file.isDirectory()) {
            log.info("Reading GTFS CSV files from directory: {}", path);
            records = readFromDirectory(file);
        } else if (file.isFile() && path.endsWith(ZIP_FILE_EXTENSION)) {
            log.info("Reading GTFS from ZIP file: {}", path);
            records = readFromZip(file);
        } else {
            throw new IllegalArgumentException("Path must be a directory or a .zip file");
        }
        return records;
    }

    private Map<GtfsFile, List<CSVRecord>> readFromDirectory(File directory) throws IOException {
        Map<GtfsFile, List<CSVRecord>> records = new HashMap<>();

        for (GtfsFile fileType : GtfsFile.values()) {
            File csvFile = new File(directory, fileType.getFileName());
            if (csvFile.exists()) {
                log.debug("Reading GTFS CSV file: {}", csvFile.getAbsolutePath());
                records.put(fileType, readCsvFile(csvFile));
            } else {
                log.warn("GTFS CSV file {} not found", csvFile.getAbsolutePath());
            }
        }

        return records;
    }

    private Map<GtfsFile, List<CSVRecord>> readFromZip(File zipFile) throws IOException {
        Map<GtfsFile, List<CSVRecord>> records = new HashMap<>();

        try (ZipFile zf = new ZipFile(zipFile, StandardCharsets.UTF_8)) {
            for (GtfsFile fileType : GtfsFile.values()) {
                ZipEntry entry = zf.getEntry(fileType.getFileName());
                if (entry != null) {
                    log.debug("Reading GTFS file from ZIP: {}", entry.getName());
                    try (InputStreamReader reader = new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8)) {
                        records.put(fileType, readCsv(reader));
                    }
                } else {
                    log.warn("GTFS file {} not found in ZIP", fileType.getFileName());
                }
            }
        }

        return records;
    }

    private List<CSVRecord> readCsvFile(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return readCsv(reader);
        }
    }

    private List<CSVRecord> readCsv(InputStreamReader reader) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setIgnoreHeaderCase(true).setTrim(true).build();
        try (CSVParser parser = new CSVParser(reader, format)) {
            return parser.getRecords();
        }
    }

}

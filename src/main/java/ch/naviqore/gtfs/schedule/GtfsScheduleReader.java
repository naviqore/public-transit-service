package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A reader for General Transit Feed Specification (GTFS) schedule data
 * <p>
 * This class provides functionality to read GTFS data from either a directory containing individual GTFS CSV files or a
 * ZIP archive containing the GTFS dataset.
 * <p>
 * Supported GTFS files are enumerated in {@link GtfsScheduleFile}, and this reader will attempt to parse each specified
 * file into a list of {@link CSVRecord} objects.
 * <p>
 * Note: The GTFS data has to strictly follow the standard GTFS file naming and format. Non-standard files will not be
 * read.
 *
 * @author munterfi
 */
@NoArgsConstructor
@Slf4j
public class GtfsScheduleReader {

    private static final String ZIP_FILE_EXTENSION = ".zip";

    private static void readFromDirectory(File directory, GtfsScheduleParser parser) throws IOException {
        for (GtfsScheduleFile fileType : GtfsScheduleFile.values()) {
            File csvFile = new File(directory, fileType.getFileName());

            if (csvFile.exists()) {
                log.info("Reading GTFS CSV file: {}", csvFile.getAbsolutePath());
                readCsvFile(csvFile, parser, fileType);
            } else if (fileType.getPresence() == GtfsScheduleFile.Presence.REQUIRED) {
                throw new FileNotFoundException("Required GTFS CSV file" + csvFile.getAbsolutePath() + " not found");
            } else if (fileType.getPresence() == GtfsScheduleFile.Presence.CONDITIONALLY_REQUIRED) {
                GtfsScheduleFile alternativeRequiredFile = getAlternativeRequiredFile(fileType);
                File alternativeCsvFile = new File(directory, alternativeRequiredFile.getFileName());
                if (!alternativeCsvFile.exists()) {
                    throw new FileNotFoundException(
                            "Conditional requirement not met: either: " + csvFile.getAbsolutePath() + " or " + alternativeCsvFile.getAbsolutePath() + " must be present in the directory");
                }
            }
        }
    }

    private static void readFromZip(File zipFile, GtfsScheduleParser parser) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile, StandardCharsets.UTF_8)) {
            for (GtfsScheduleFile fileType : GtfsScheduleFile.values()) {
                ZipEntry entry = zf.getEntry(fileType.getFileName());

                if (entry != null) {
                    log.info("Reading GTFS file from ZIP: {}", entry.getName());
                    try (InputStreamReader reader = new InputStreamReader(BOMInputStream.builder()
                            .setInputStream(zf.getInputStream(entry))
                            .setByteOrderMarks(ByteOrderMark.UTF_8)
                            .setInclude(false)
                            .get(), StandardCharsets.UTF_8)) {
                        readCsvRecords(reader, parser, fileType);
                    }
                } else if (fileType.getPresence() == GtfsScheduleFile.Presence.REQUIRED) {
                    throw new FileNotFoundException(
                            "Required GTFS CSV file" + fileType.getFileName() + " not found in ZIP");
                } else if (fileType.getPresence() == GtfsScheduleFile.Presence.CONDITIONALLY_REQUIRED) {
                    GtfsScheduleFile alternativeRequiredFile = getAlternativeRequiredFile(fileType);
                    if (zf.getEntry(alternativeRequiredFile.getFileName()) != null) {
                        throw new FileNotFoundException(
                                "Conditional requirement not met: either: " + fileType.getFileName() + " or " + alternativeRequiredFile.getFileName() + " must be present in the ZIP");
                    }
                }
            }
        }
    }

    private static GtfsScheduleFile getAlternativeRequiredFile(GtfsScheduleFile fileType) {
        return switch (fileType) {
            case CALENDAR -> GtfsScheduleFile.CALENDAR_DATES;
            case CALENDAR_DATES -> GtfsScheduleFile.CALENDAR;
            default -> throw new IllegalArgumentException("No alternative required file for " + fileType);
        };
    }

    private static void readCsvFile(File file, GtfsScheduleParser parser,
                                    GtfsScheduleFile fileType) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BOMInputStream bomInputStream = BOMInputStream.builder()
                     .setInputStream(fileInputStream)
                     .setByteOrderMarks(ByteOrderMark.UTF_8)
                     .get(); InputStreamReader reader = new InputStreamReader(bomInputStream, StandardCharsets.UTF_8)) {
            readCsvRecords(reader, parser, fileType);
        }
    }

    private static void readCsvRecords(InputStreamReader reader, GtfsScheduleParser recordParser,
                                       GtfsScheduleFile fileType) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setIgnoreHeaderCase(true).setTrim(true).build();
        try (CSVParser csvParser = new CSVParser(reader, format)) {
            log.debug("CSV Headers: {}", csvParser.getHeaderMap().keySet());
            csvParser.forEach(record -> recordParser.parse(record, fileType));
        }
    }

    public GtfsSchedule read(String path) throws IOException {
        File file = new File(path);
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        GtfsScheduleParser parser = new GtfsScheduleParser(builder);

        if (file.isDirectory()) {
            log.info("Reading GTFS CSV files from directory: {}", path);
            readFromDirectory(file, parser);
        } else if (file.isFile() && path.endsWith(ZIP_FILE_EXTENSION)) {
            log.info("Reading GTFS from ZIP file: {}", path);
            readFromZip(file, parser);
        } else {
            throw new IllegalArgumentException("Path must be a directory or a .zip file");
        }

        return builder.build();
    }

}

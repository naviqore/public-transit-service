package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.type.ExceptionType;
import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, DayOfWeek> DAY_MAPPINGS = Map.of("monday", DayOfWeek.MONDAY, "tuesday",
            DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY, "thursday", DayOfWeek.THURSDAY, "friday",
            DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY, "sunday", DayOfWeek.SUNDAY);

    /**
     * Standard GTFS file types and their corresponding file names.
     */
    @RequiredArgsConstructor
    @Getter
    public enum GtfsFile {
        AGENCY("agency.txt"), CALENDAR_DATES("calendar_dates.txt"), CALENDAR("calendar.txt"), FARE_ATTRIBUTES(
                "fare_attributes.txt"), FARE_RULES("fare_rules.txt"), FREQUENCIES("frequencies.txt"), ROUTES(
                "routes.txt"), SHAPES("shapes.txt"), STOP_TIMES("stop_times.txt"), STOPS("stops.txt"), TRIPS(
                "trips.txt");

        private final String fileName;
    }

    public GtfsSchedule read(String path) throws IOException {
        File file = new File(path);
        Map<GtfsFile, List<CSVRecord>> records;

        if (file.isDirectory()) {
            log.info("Reading GTFS CSV files from directory: {}", path);
            records = readFromDirectory(file);
        } else if (file.isFile() && path.endsWith(ZIP_FILE_EXTENSION)) {
            log.info("Reading GTFS from ZIP file: {}", path);
            records = readFromZip(file);
        } else {
            throw new IllegalArgumentException("Path must be a directory or a .zip file");
        }

        return buildSchedule(records);
    }

    private GtfsSchedule buildSchedule(Map<GtfsFile, List<CSVRecord>> records) {
        GtfsScheduleBuilder builder = GtfsScheduleBuilder.builder();
        for (CSVRecord record : records.get(GtfsFile.AGENCY)) {
            builder.addAgency(record.get("agency_id"), record.get("agency_name"), record.get("agency_url"),
                    record.get("agency_timezone"));
        }
        for (CSVRecord record : records.get(GtfsFile.STOPS)) {
            builder.addStop(record.get("stop_id"), record.get("stop_name"), Double.parseDouble(record.get("stop_lat")),
                    Double.parseDouble(record.get("stop_lon")));
        }
        for (CSVRecord record : records.get(GtfsFile.ROUTES)) {
            builder.addRoute(record.get("route_id"), record.get("agency_id"), record.get("route_short_name"),
                    record.get("route_long_name"), RouteType.parse(record.get("route_type")));
        }
        for (CSVRecord record : records.get(GtfsFile.CALENDAR)) {
            EnumSet<DayOfWeek> serviceDays = EnumSet.noneOf(DayOfWeek.class);
            DAY_MAPPINGS.forEach((key, value) -> {
                if ("1".equals(record.get(key))) {
                    serviceDays.add(value);
                }
            });
            builder.addCalendar(record.get("service_id"), serviceDays,
                    LocalDate.parse(record.get("start_date"), DATE_FORMATTER),
                    LocalDate.parse(record.get("end_date"), DATE_FORMATTER));
        }
        for (CSVRecord record : records.get(GtfsFile.CALENDAR_DATES)) {
            builder.addCalendarDate(record.get("service_id"), LocalDate.parse(record.get("date"), DATE_FORMATTER),
                    ExceptionType.parse(record.get("exception_type")));
        }
        for (CSVRecord record : records.get(GtfsFile.TRIPS)) {
            builder.addTrip(record.get("trip_id"), record.get("route_id"), record.get("service_id"));
        }
        for (CSVRecord record : records.get(GtfsFile.STOP_TIMES)) {
            builder.addStopTime(record.get("trip_id"), record.get("stop_id"),
                    ServiceDayTime.parse(record.get("arrival_time")),
                    ServiceDayTime.parse(record.get("departure_time")));
        }
        return builder.build();
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
                    try (InputStreamReader reader = new InputStreamReader(zf.getInputStream(entry),
                            StandardCharsets.UTF_8)) {
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

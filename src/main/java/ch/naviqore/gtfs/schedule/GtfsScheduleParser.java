package ch.naviqore.gtfs.schedule;

import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.type.ExceptionType;
import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVRecord;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * GTFS CSV Records Parser
 *
 * @author munterfi
 */
@RequiredArgsConstructor
@Log4j2
class GtfsScheduleParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, DayOfWeek> DAY_MAPPINGS = Map.of("monday", DayOfWeek.MONDAY, "tuesday",
            DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY, "thursday", DayOfWeek.THURSDAY, "friday",
            DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY, "sunday", DayOfWeek.SUNDAY);
    private final GtfsScheduleBuilder builder;

    void parseAgencies(List<CSVRecord> records) {
        log.info("Parsing {} agency records", records.size());
        for (CSVRecord record : records) {
            builder.addAgency(record.get("agency_id"), record.get("agency_name"), record.get("agency_url"),
                    record.get("agency_timezone"));
        }
    }

    void parseCalendars(List<CSVRecord> records) {
        log.info("Parsing {} calendar records", records.size());
        for (CSVRecord record : records) {
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
    }

    void parseCalendarDates(List<CSVRecord> records) {
        log.info("Parsing {} calendar date records", records.size());
        for (CSVRecord record : records) {
            builder.addCalendarDate(record.get("service_id"), LocalDate.parse(record.get("date"), DATE_FORMATTER),
                    ExceptionType.parse(record.get("exception_type")));
        }
    }

    void parseStops(List<CSVRecord> records) {
        log.info("Parsing {} stop records", records.size());
        for (CSVRecord record : records) {
            builder.addStop(record.get("stop_id"), record.get("stop_name"), Double.parseDouble(record.get("stop_lat")),
                    Double.parseDouble(record.get("stop_lon")));
        }
    }

    void parseRoutes(List<CSVRecord> records) {
        log.info("Parsing {} route records", records.size());
        for (CSVRecord record : records) {
            // TODO: Route types are not standardized in any way.
            // RouteType.parse(record.get("route_type"))
            builder.addRoute(record.get("route_id"), record.get("agency_id"), record.get("route_short_name"),
                    record.get("route_long_name"), RouteType.RAIL);
        }
    }

    void parseTrips(List<CSVRecord> records) {
        log.info("Parsing {} trip records", records.size());
        for (CSVRecord record : records) {
            builder.addTrip(record.get("trip_id"), record.get("route_id"), record.get("service_id"));
        }
    }

    void parseStopTimes(List<CSVRecord> records) {
        log.info("Parsing {} stop time records", records.size());
        for (CSVRecord record : records) {
            builder.addStopTime(record.get("trip_id"), record.get("stop_id"),
                    ServiceDayTime.parse(record.get("arrival_time")),
                    ServiceDayTime.parse(record.get("departure_time")));
        }
    }

}

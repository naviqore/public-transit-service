package ch.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class GtfsSchedule {

    private final Map<String, Agency> agencies;

    public Agency getAgency(String id) {
        return agencies.get(id);
    }
}

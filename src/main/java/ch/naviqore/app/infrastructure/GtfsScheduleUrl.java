package ch.naviqore.app.infrastructure;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import ch.naviqore.utils.network.FileDownloader;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;

@RequiredArgsConstructor
public class GtfsScheduleUrl implements GtfsScheduleRepository {

    private final String url;
    private final String downloadDirectory;
    private final String fileName;

    @Override
    public GtfsSchedule get() throws IOException, InterruptedException {
        Path directory = Path.of(downloadDirectory);
        new FileDownloader(url).downloadTo(directory, fileName, true);
        return new GtfsScheduleReader().read(directory.resolve(fileName).toString());
    }
}

package org.naviqore.benchmark.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.benchmark.subject.ServiceSubject;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    private static URL[] classPathOfThisTest() throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }

        return urls.toArray(URL[]::new);
    }

    @Test
    void runsAVersionInAClassLoaderOfItsOwn(@TempDir Path directory) throws Exception {
        Version version = Version.load("isolated", classPathOfThisTest());
        version.build(GtfsScheduleDataset.SAMPLE_FEED_1.getZip(directory).toPath());

        // the subject is a proxy of another class loader's subject, called with JDK types only
        assertFalse(version.subject() instanceof ServiceSubject);
        assertEquals("isolated", version.name());
        assertEquals("America/Los_Angeles", version.subject().zone());
        assertEquals(List.of("2007-01-01", "2010-12-31"), version.subject().validity());
    }

    @Test
    void runsTheVersionOnTheClassPathItself() throws Exception {
        Version version = Version.load(Path.of(Version.CLASS_PATH));

        assertInstanceOf(ServiceSubject.class, version.subject());
        assertEquals(Version.CLASS_PATH, version.name());
    }

}

package org.naviqore.gtfs.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.jspecify.annotations.Nullable;
import org.naviqore.gtfs.schedule.GtfsScheduleParser.ParsedRecord;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A reader for General Transit Feed Specification (GTFS) schedule data
 * <p>
 * This class provides functionality to read GTFS data from either a directory containing individual GTFS CSV files or a
 * ZIP archive containing the GTFS dataset.
 * <p>
 * Supported GTFS files are enumerated in {@link GtfsScheduleFile}, and this reader parses each of them into
 * {@link CSVRecord} objects, reads them into the values of the schedule and adds these to the schedule builder one
 * after the other, in the order of the file.
 * <p>
 * A file is read in blocks of complete records: a thread of its own inflates the bytes and cuts them into blocks (see
 * {@link RecordBlocks}), whose records are parsed and read into values concurrently, a bounded number of blocks at a
 * time, while the calling thread adds the values to the builder, in the order of the file (see
 * {@link GtfsScheduleParser}). The schedule, its validation and its warnings are the same as when the file is parsed in
 * one piece, and reading never runs more than a few blocks ahead of the builder. A record commons-csv cannot parse
 * fails as it does in one piece; commons-csv counts lines from the start of a block, so the failure in a block after
 * the first line of the file names the line the block starts at.
 * <p>
 * Note: The GTFS data has to strictly follow the standard GTFS file naming and format. Non-standard files will not be
 * read.
 *
 * @author munterfi
 */
@Slf4j
public class GtfsScheduleReader {

    private static final String ZIP_FILE_EXTENSION = ".zip";
    private static final int BLOCK_SIZE = 1 << 20;
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .get();

    private final int blockSize;
    private final int concurrency;

    public GtfsScheduleReader() {
        this(BLOCK_SIZE, Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param blockSize   the number of bytes a block of records is cut from, see {@link RecordBlocks}
     * @param concurrency the number of blocks parsed at a time
     */
    GtfsScheduleReader(int blockSize, int concurrency) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("Block size must be at least 1, not " + blockSize);
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("Concurrency must be at least 1, not " + concurrency);
        }
        this.blockSize = blockSize;
        this.concurrency = concurrency;
    }

    private static GtfsScheduleFile getAlternativeRequiredFile(GtfsScheduleFile fileType) {
        return switch (fileType) {
            case CALENDAR -> GtfsScheduleFile.CALENDAR_DATES;
            case CALENDAR_DATES -> GtfsScheduleFile.CALENDAR;
            default -> throw new IllegalArgumentException("No alternative required file for " + fileType);
        };
    }

    /**
     * Parses the records of a block and reads them into the values of the schedule, on any thread.
     */
    private static ReadBlock read(byte[] block, CSVFormat format, GtfsScheduleFile fileType) {
        try (CSVParser csvParser = format.parse(new StringReader(decode(block)))) {
            return read(csvParser, fileType);
        } catch (IOException e) {
            // given the header, a parser of a string never fails to start or close
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parses the records of a parser and reads them into the values of the schedule, up to a record commons-csv cannot
     * parse.
     */
    private static ReadBlock read(CSVParser csvParser, GtfsScheduleFile fileType) {
        List<ParsedRecord> records = new ArrayList<>();
        try {
            for (CSVRecord record : csvParser) {
                records.add(GtfsScheduleParser.read(record, fileType));
            }
        } catch (UncheckedIOException e) {
            return new ReadBlock(records, csvParser.getCurrentLineNumber(), e);
        }

        return new ReadBlock(records, csvParser.getCurrentLineNumber(), null);
    }

    private static String decode(byte[] block) {
        return new String(block, StandardCharsets.UTF_8);
    }

    private static void validateCsvFileIsNotEmpty(GtfsScheduleFile fileType, long recordNumber) throws IOException {
        if (recordNumber == 0) {
            String message = String.format("CSV file %s (%s) is empty", fileType.getFileName(),
                    fileType.getPresence().name());
            if (fileType.getPresence() == GtfsScheduleFile.Presence.REQUIRED) {
                throw new IOException(message);
            } else {
                log.warn(message);
            }
        }
    }

    private void readFromDirectory(File directory, GtfsScheduleParser parser) throws IOException {
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

    private void readFromZip(File zipFile, GtfsScheduleParser parser) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile, StandardCharsets.UTF_8)) {
            for (GtfsScheduleFile fileType : GtfsScheduleFile.values()) {
                ZipEntry entry = zf.getEntry(fileType.getFileName());

                if (entry != null) {
                    log.info("Reading GTFS file from ZIP: {}", entry.getName());
                    try (InputStream input = BOMInputStream.builder()
                            .setInputStream(zf.getInputStream(entry))
                            .setByteOrderMarks(ByteOrderMark.UTF_8)
                            .setInclude(false)
                            .get()) {
                        readCsvRecords(input, parser, fileType);
                    }
                } else if (fileType.getPresence() == GtfsScheduleFile.Presence.REQUIRED) {
                    throw new FileNotFoundException(
                            "Required GTFS CSV file" + fileType.getFileName() + " not found in ZIP");
                } else if (fileType.getPresence() == GtfsScheduleFile.Presence.CONDITIONALLY_REQUIRED) {
                    GtfsScheduleFile alternativeRequiredFile = getAlternativeRequiredFile(fileType);
                    if (zf.getEntry(alternativeRequiredFile.getFileName()) == null) {
                        throw new FileNotFoundException(
                                "Conditional requirement not met: either: " + fileType.getFileName() + " or " + alternativeRequiredFile.getFileName() + " must be present in the ZIP");
                    }
                }
            }
        }
    }

    private void readCsvFile(File file, GtfsScheduleParser parser, GtfsScheduleFile fileType) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BOMInputStream bomInputStream = BOMInputStream.builder()
                     .setInputStream(fileInputStream)
                     .setByteOrderMarks(ByteOrderMark.UTF_8)
                     .get()) {
            readCsvRecords(bomInputStream, parser, fileType);
        }
    }

    /**
     * Reads the records of a file in blocks: the blocks up to the one holding the header are read and parsed on this
     * thread, the blocks after it are read ahead on a thread of their own, and their records parsed and read into
     * values concurrently, with that header. This thread adds the values to the schedule in the order of the records.
     */
    private void readCsvRecords(InputStream input, GtfsScheduleParser recordParser,
                                GtfsScheduleFile fileType) throws IOException {
        RecordBlocks blocks = new RecordBlocks(input, blockSize);
        InOrder inOrder = new InOrder(recordParser, fileType);

        List<String> header = List.of();
        byte @Nullable [] block;
        while (header.isEmpty() && (block = blocks.next()) != null) {
            try (CSVParser csvParser = FORMAT.parse(new StringReader(decode(block)))) {
                header = csvParser.getHeaderNames();
                inOrder.add(read(csvParser, fileType));
            }
        }
        log.debug("CSV Headers: {}", header);

        if (!header.isEmpty()) {
            CSVFormat format = FORMAT.builder().setHeader(header.toArray(String[]::new)).get();
            try (BlocksAhead ahead = new BlocksAhead(blocks, concurrency)) {
                ahead.stream()
                        .gather(Gatherers.mapConcurrent(concurrency, bytes -> read(bytes, format, fileType)))
                        .forEachOrdered(inOrder::add);
            } catch (RuntimeException e) {
                // mapConcurrent rethrows an error of a block it parsed wrapped in a runtime exception
                if (e.getClass() == RuntimeException.class && e.getCause() instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }

        validateCsvFileIsNotEmpty(fileType, inOrder.records());
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

    /**
     * The values read from the records of a block, the number of lines commons-csv read, and the failure of a record it
     * could not parse, which ends the block.
     */
    private record ReadBlock(List<ParsedRecord> records, long lines, @Nullable UncheckedIOException failure) {
    }

    /**
     * Adds the values read from the blocks of a file to the schedule, block after block in the order of the file, and
     * counts the records and the lines they were read from.
     */
    @RequiredArgsConstructor
    private static final class InOrder {

        private final GtfsScheduleParser recordParser;
        private final GtfsScheduleFile fileType;
        private long records;
        private long lines;

        /**
         * Adds the values of the next block. A record commons-csv could not parse fails after the records before it, as
         * in a file parsed in one piece; commons-csv counts the lines and characters of a block from the start of the
         * block, so the failure in a block after the first line of the file names the line the block starts at.
         */
        void add(ReadBlock block) {
            for (ParsedRecord record : block.records()) {
                recordParser.add(record);
            }
            records += block.records().size();

            UncheckedIOException failure = block.failure();
            if (failure != null) {
                if (lines == 0) {
                    throw failure;
                }
                throw new UncheckedIOException(
                        String.format("Cannot parse the block of %s from its line %d: %s", fileType.getFileName(),
                                lines + 1, failure.getMessage()), failure.getCause());
            }
            lines += block.lines();
        }

        long records() {
            return records;
        }
    }
}

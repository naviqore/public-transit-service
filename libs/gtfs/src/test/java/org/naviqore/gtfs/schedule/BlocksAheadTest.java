package org.naviqore.gtfs.schedule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading the blocks of a file ahead, on a thread of its own: in the order of the file, with a failure to read handed
 * to the thread that takes the blocks, and stopped when closed before the end.
 */
class BlocksAheadTest {

    private static final String RECORDS = "a,b\n1,2\n3,4\n5,6\n7,8\n9,10\n";

    private static List<String> read(RecordBlocks blocks) throws IOException {
        List<String> read = new ArrayList<>();
        byte[] block;
        while ((block = blocks.next()) != null) {
            read.add(new String(block, UTF_8));
        }

        return read;
    }

    private static boolean readingThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.getName().equals("gtfs-blocks"));
    }

    @Test
    void blocks_areThoseOfTheFile_inItsOrder() throws IOException {
        List<String> expected = read(new RecordBlocks(new ByteArrayInputStream(RECORDS.getBytes(UTF_8)), 4));

        List<String> ahead;
        try (BlocksAhead blocks = new BlocksAhead(
                new RecordBlocks(new ByteArrayInputStream(RECORDS.getBytes(UTF_8)), 4), 2)) {
            ahead = blocks.stream().map(block -> new String(block, UTF_8)).toList();
        }

        assertThat(ahead).hasSizeGreaterThan(1).isEqualTo(expected);
    }

    @Test
    void failureToRead_isThrownWhereTheBlocksAreTaken() {
        InputStream failing = new InputStream() {
            private final InputStream records = new ByteArrayInputStream(RECORDS.getBytes(UTF_8));

            @Override
            public int read() throws IOException {
                int b = records.read();
                if (b < 0) {
                    throw new IOException("disk gone");
                }
                return b;
            }
        };

        try (BlocksAhead blocks = new BlocksAhead(new RecordBlocks(failing, 4), 2)) {
            assertThatThrownBy(() -> blocks.stream().toList()).isInstanceOf(UncheckedIOException.class)
                    .hasRootCauseMessage("disk gone");
        }
    }

    @Test
    @Timeout(10)
    void failureOtherThanToRead_isThrownWhereTheBlocksAreTaken() {
        InputStream failing = new InputStream() {
            @Override
            public int read() {
                throw new IllegalStateException("not a stream");
            }
        };

        try (BlocksAhead blocks = new BlocksAhead(new RecordBlocks(failing, 4), 2)) {
            assertThatThrownBy(() -> blocks.stream().toList()).isInstanceOf(IllegalStateException.class)
                    .hasMessage("not a stream");
        }
    }

    @Test
    @Timeout(10)
    void close_beforeTheEnd_stopsTheReadingThread() {
        // more records than the queue holds, of which one block is taken
        byte[] many = RECORDS.repeat(1000).getBytes(UTF_8);
        try (BlocksAhead blocks = new BlocksAhead(new RecordBlocks(new ByteArrayInputStream(many), 16), 1)) {
            Iterator<byte[]> iterator = blocks.stream().iterator();
            assertThat(iterator.next()).isNotEmpty();
        }

        assertThat(readingThreadAlive()).isFalse();
    }
}

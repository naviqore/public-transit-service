package org.naviqore.gtfs.schedule;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cutting the bytes of a CSV file into blocks: for every block size, the blocks are the bytes of the file, and parsed
 * one by one they give the records commons-csv gives for the whole file.
 */
class RecordBlocksTest {

    static Stream<Arguments> files() {
        return Stream.of(Arguments.of("records", "a,b\n1,2\n3,4\n"),
                Arguments.of("carriage returns and line feeds", "a,b\r\n1,2\r\n3,4\r\n"),
                Arguments.of("carriage returns only", "a,b\r1,2\r\"x\ry\",3\r"),
                Arguments.of("no line break at the end", "a,b\n1,2\n3,4"),
                Arguments.of("empty lines", "a,b\n\n1,2\n\n\n3,4\n"),
                Arguments.of("line breaks in quoted values", "a,b\n\"x\ny\",2\n\"p\r\nq\",\"\n\"\n"),
                Arguments.of("quotes in quoted values", "a,b\n\"he said \"\"hi\"\"\",2\n\"\"\"\",\"x\n\"\"y\"\"\"\n"),
                Arguments.of("quotes in unquoted values", "a,b\nab\"c,2\n\"x\ny\",3\nd\"\",4\n"),
                Arguments.of("quote after a space", "a,b\n \"x,y\n\"z\",2\n"),
                Arguments.of("empty values", "a,b,c\n,,\n\"\",\"\",\"\"\n,\"\n\",\n"),
                Arguments.of("multi-byte characters", "a,b\nZürich,\"Genève, Cornavin\"\n東京,\"大阪\n京都\"\n"),
                Arguments.of("header only", "a,b\n"), Arguments.of("empty", ""));
    }

    private static List<byte[]> blocks(InputStream input, int blockSize) throws IOException {
        RecordBlocks recordBlocks = new RecordBlocks(input, blockSize);
        List<byte[]> blocks = new ArrayList<>();
        byte[] block;
        while ((block = recordBlocks.next()) != null) {
            blocks.add(block);
        }

        return blocks;
    }

    private static List<List<String>> parse(byte[] bytes) {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(new String(bytes, UTF_8)))) {
            return parser.getRecords().stream().map(CSVRecord::toList).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concatenate(List<byte[]> blocks) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        blocks.forEach(bytes::writeBytes);

        return bytes.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    void blocks_holdCompleteRecords(String name, String file) throws IOException {
        byte[] bytes = file.getBytes(UTF_8);
        List<List<String>> records = parse(bytes);

        for (int blockSize = 1; blockSize <= bytes.length + 1; blockSize++) {
            List<byte[]> blocks = blocks(new ByteArrayInputStream(bytes), blockSize);

            assertThat(concatenate(blocks)).as("bytes, block size %d", blockSize).isEqualTo(bytes);
            assertThat(blocks).as("blocks, block size %d", blockSize)
                    .allSatisfy(block -> assertThat(block).isNotEmpty());
            assertThat(blocks.stream().flatMap(block -> parse(block).stream()).toList()).as("records, block size %d",
                    blockSize).isEqualTo(records);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    void blocks_holdCompleteRecords_whenTheInputArrivesByteByByte(String name, String file) throws IOException {
        byte[] bytes = file.getBytes(UTF_8);

        for (int blockSize = 1; blockSize <= bytes.length + 1; blockSize++) {
            List<byte[]> blocks = blocks(new OneByteAtATime(new ByteArrayInputStream(bytes)), blockSize);

            assertThat(concatenate(blocks)).as("bytes, block size %d", blockSize).isEqualTo(bytes);
            assertThat(blocks.stream().flatMap(block -> parse(block).stream()).toList()).as("records, block size %d",
                    blockSize).isEqualTo(parse(bytes));
        }
    }

    @Test
    void blocks_endBeforeAQuoteNotClosed() throws IOException {
        // the quote opened in the third record is never closed: no record ends after it, so the rest of the file is a
        // block of its own, which commons-csv rejects as it rejects the whole file
        byte[] bytes = "a,b\n1,2\n\"x,3\n4,5\n".getBytes(UTF_8);

        List<byte[]> blocks = blocks(new ByteArrayInputStream(bytes), 4);

        assertThat(blocks.getLast()).isEqualTo("\"x,3\n4,5\n".getBytes(UTF_8));
        assertThat(concatenate(blocks)).isEqualTo(bytes);
    }

    @Test
    void blocks_ofTheGivenSize_endAtTheLastRecordInThem() throws IOException {
        byte[] bytes = "a,b\n1,2\n3,4\n5,6\n".getBytes(UTF_8);

        List<byte[]> blocks = blocks(new ByteArrayInputStream(bytes), 9);

        // the first nine bytes hold two records and the first byte of the third
        assertThat(blocks).extracting(block -> new String(block, UTF_8)).containsExactly("a,b\n1,2\n", "3,4\n5,6\n");
    }

    /**
     * An input that returns at most one byte per read, as a slow stream may.
     */
    private static final class OneByteAtATime extends FilterInputStream {

        private OneByteAtATime(InputStream input) {
            super(input);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return super.read(bytes, offset, Math.min(length, 1));
        }
    }
}

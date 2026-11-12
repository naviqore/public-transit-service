package org.naviqore.gtfs.schedule;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Cuts the bytes of a CSV file into blocks of complete records, which can be parsed independently of one another. A
 * block ends after the last line break that ends a record in the next given number of bytes, or in more bytes if no
 * record ends in them; the last block holds the rest of the file.
 * <p>
 * A line break inside a quoted value does not end a record. Quotes are recognized as commons-csv recognizes them with
 * the format {@code CSVFormat.DEFAULT}: a quote opens a quoted value only at the start of a value, two quotes in a
 * quoted value are a quote of the value, and a quote anywhere else is part of an unquoted value. Every line break, a
 * carriage return as well, starts a new value.
 * <p>
 * The bytes are cut, never decoded: in UTF-8, the bytes of a line break, a quote or a comma never occur inside the
 * bytes of another character.
 */
final class RecordBlocks {

    // the longest array the JDK allocates
    private static final int MAX_SIZE = Integer.MAX_VALUE - 8;
    private static final int INITIAL_CAPACITY = 1 << 20;

    private static final byte QUOTE = '"';
    private static final byte COMMA = ',';
    private static final byte LINE_FEED = '\n';
    private static final byte CARRIAGE_RETURN = '\r';

    // where a scan is: at the start of a value, in an unquoted value, in a quoted value, or after a quote in a quoted
    // value, which closes the value or, followed by another quote, is a quote of the value
    private static final int VALUE_START = 0;
    private static final int UNQUOTED = 1;
    private static final int QUOTED = 2;
    private static final int QUOTE_IN_QUOTED = 3;

    private final InputStream input;
    private final int blockSize;

    private byte[] buffer;
    private int length;
    private int scanned;
    private int state = VALUE_START;
    private int recordEnd;
    private boolean endOfInput;

    /**
     * @param input     the bytes of the file, after its byte order mark if it has one
     * @param blockSize the number of bytes a block is cut from
     */
    RecordBlocks(InputStream input, int blockSize) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("Block size must be at least 1, not " + blockSize);
        }
        this.input = input;
        this.blockSize = blockSize;
        this.buffer = new byte[Math.min(blockSize, INITIAL_CAPACITY)];
    }

    /**
     * Reads the next block.
     *
     * @return the bytes of the next block, or null at the end of the input
     * @throws IOException if the input cannot be read, or no record ends within the longest array
     */
    byte @Nullable [] next() throws IOException {
        // read until the buffer holds a block's bytes and a record ends in them, or the input ends
        int size = blockSize;
        while (true) {
            fill(size);
            scan();
            if (recordEnd > 0 || endOfInput) {
                break;
            }
            if (length >= MAX_SIZE) {
                throw new IOException("No record ends within " + MAX_SIZE + " bytes: a quoted value may not be closed");
            }
            size = (int) Math.min(2L * length, MAX_SIZE);
        }

        // at the end of the input, the bytes left are the last records
        int end = endOfInput ? length : recordEnd;
        if (end == 0) {
            return null;
        }

        byte[] block = Arrays.copyOf(buffer, end);
        length -= end;
        System.arraycopy(buffer, end, buffer, 0, length);
        // the bytes after the block are scanned, and no record ends in them
        scanned = length;
        recordEnd = 0;

        return block;
    }

    /**
     * Reads until the buffer holds the given number of bytes, or the input ends.
     */
    private void fill(int size) throws IOException {
        while (length < size && !endOfInput) {
            if (length == buffer.length) {
                buffer = Arrays.copyOf(buffer, (int) Math.min(2L * buffer.length, MAX_SIZE));
            }
            int read = input.read(buffer, length, Math.min(buffer.length, size) - length);
            if (read < 0) {
                endOfInput = true;
            } else {
                length += read;
            }
        }
    }

    /**
     * Scans the bytes read since the last scan, and records where the last record ends in them.
     */
    private void scan() {
        byte[] bytes = buffer;
        int current = state;
        int end = recordEnd;
        for (int i = scanned; i < length; i++) {
            byte b = bytes[i];
            if (current == QUOTED) {
                if (b == QUOTE) {
                    current = QUOTE_IN_QUOTED;
                }
            } else if (b == QUOTE) {
                current = current == VALUE_START || current == QUOTE_IN_QUOTED ? QUOTED : UNQUOTED;
            } else if (b == COMMA || b == CARRIAGE_RETURN) {
                current = VALUE_START;
            } else if (b == LINE_FEED) {
                current = VALUE_START;
                end = i + 1;
            } else {
                current = UNQUOTED;
            }
        }
        state = current;
        recordEnd = end;
        scanned = length;
    }
}

package org.naviqore.gtfs.schedule;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Stream;

/**
 * Reads the blocks of a file on a thread of its own, at most a given number ahead of the thread that takes them, so
 * that inflating and cutting the bytes overlap with parsing the blocks and building the schedule from their records.
 */
final class BlocksAhead implements AutoCloseable {

    private static final byte[] END = new byte[0];

    private final BlockingQueue<byte[]> blocks;
    private final Thread reader;
    private volatile @Nullable Throwable failure;

    /**
     * Starts reading the blocks.
     *
     * @param source   the blocks of the file
     * @param capacity the number of blocks read ahead at most
     */
    BlocksAhead(RecordBlocks source, int capacity) {
        blocks = new ArrayBlockingQueue<>(capacity);
        reader = Thread.ofPlatform().name("gtfs-blocks").daemon(true).start(() -> read(source));
    }

    private void read(RecordBlocks source) {
        try {
            byte[] block;
            while ((block = source.next()) != null) {
                blocks.put(block);
            }
        } catch (InterruptedException e) {
            // closed before the end of the file
            return;
        } catch (IOException | RuntimeException | Error e) {
            // handed to the thread that takes the blocks, which would otherwise wait for the end forever
            failure = e;
        }
        try {
            blocks.put(END);
        } catch (InterruptedException e) {
            // closed
        }
    }

    /**
     * The blocks, in the order of the file, as they are read.
     *
     * @throws UncheckedIOException if the file cannot be read, and any other failure of the reading thread as it is
     */
    Stream<byte[]> stream() {
        return Stream.iterate(take(), block -> block != END, _ -> take());
    }

    private byte[] take() {
        byte[] block;
        try {
            block = blocks.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading blocks", e);
        }
        Throwable cause = failure;
        if (block == END && cause != null) {
            switch (cause) {
                case IOException e -> throw new UncheckedIOException(e);
                case RuntimeException e -> throw e;
                case Error e -> throw e;
                default -> throw new IllegalStateException(cause);
            }
        }

        return block;
    }

    /**
     * Stops reading, and waits until the reading thread has ended, after which the input may be closed.
     */
    @Override
    public void close() {
        reader.interrupt();
        boolean interrupted = false;
        while (reader.isAlive()) {
            try {
                reader.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

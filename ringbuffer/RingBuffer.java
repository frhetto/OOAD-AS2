package ringbuffer;

import java.util.Arrays;
import java.util.Objects;

/**
 * Single-writer, multi-reader ring buffer.
 * - Fixed capacity
 * - Writer overwrites old entries
 * - Each Reader has its own cursor (sequence)
 */
public final class RingBuffer<T> {

    private final int capacity;
    private final Object[] data;

    // Sequence number of NEXT write (monotonic)
    private long writeSeq = 0;

    // Actual ring index where next write will go (0..capacity-1)
    private int writeIndex = 0;

    // Shared lock for writer + all readers
    final Object lock = new Object();

    public RingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.data = new Object[capacity];
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Single writer only.
     * Writes into the current ring slot and advances writeIndex in a loop.
     */
    public void write(T item) {
        Objects.requireNonNull(item, "item must not be null");
        synchronized (lock) {
            data[writeIndex] = item;                 // <-- real ring slot write
            writeIndex = (writeIndex + 1) % capacity; // <-- loop around
            writeSeq++;                               // <-- sequence for readers
        }
    }

   public Reader<T> createReader(ReaderStart mode, String name, long delayMs, long offsetItems) {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(name, "name");
    synchronized (lock) {
        long base = (mode == ReaderStart.FROM_NOW)
                ? writeSeq
                : oldestAvailableSeqUnsafe();

        long startSeq = base + Math.max(0, offsetItems);

        // // Don't allow starting past what is currently written
        // if (startSeq > writeSeq) startSeq = writeSeq;

        return new Reader<>(this, startSeq, name, delayMs);
    }
}

    long writeSeqUnsafe() {
        return writeSeq;
    }

    long oldestAvailableSeqUnsafe() {
        long oldest = writeSeq - capacity;
        return Math.max(0, oldest);
    }

    @SuppressWarnings("unchecked")
    T getAtSeqUnsafe(long seq) {
        int idx = (int) (seq % capacity); // sequence -> ring slot
        return (T) data[idx];
    }

    // Optional to see current ring state
    public String debugRing() {
        synchronized (lock) {
            return Arrays.toString(data) + " (nextWriteIndex=" + writeIndex + ", writeSeq=" + writeSeq + ")";
        }
    }
}
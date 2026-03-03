package ringbuffer;

import java.util.Optional;

public final class Reader<T> implements Runnable {

    private final RingBuffer<T> buffer;
    private final String name;
    private final long delayMs;

    private long nextSeqToRead;

    Reader(RingBuffer<T> buffer, long startSeq, String name, long delayMs) {
        this.buffer = buffer;
        this.nextSeqToRead = startSeq;
        this.name = name;
        this.delayMs = delayMs;
    }

    public Optional<T> read() {
        synchronized (buffer.lock) {
            long oldestAvailable = buffer.oldestAvailableSeqUnsafe();
            long writeSeq = buffer.writeSeqUnsafe();

            // If this reader is too slow, it missed overwritten items -> skip
            if (nextSeqToRead < oldestAvailable) {
                nextSeqToRead = oldestAvailable;
            }

            if (nextSeqToRead >= writeSeq) {
                return Optional.empty();
            }

            T item = buffer.getAtSeqUnsafe(nextSeqToRead);
            nextSeqToRead++;
            return Optional.ofNullable(item);
        }
    }

    public long position() {
        synchronized (buffer.lock) {
            return nextSeqToRead;
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Optional<T> v = read();
            v.ifPresent(val ->
                System.out.println("[" + name + "] read=" + val + " pos=" + ringIndex() + " | " + buffer.debugRing())
            );
            sleep(delayMs);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    public int ringIndex() {
    synchronized (buffer.lock) {
        return (int) (nextSeqToRead % buffer.capacity());
        }
    }
}
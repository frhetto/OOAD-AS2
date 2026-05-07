package ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Reader}.
 */
class ReaderTest {

    @Test
    @DisplayName("read() on an empty buffer returns Optional.empty()")
    void readEmptyBufferReturnsEmpty() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
        assertEquals(Optional.empty(), r.read());
    }

    @Test
    @DisplayName("read() returns items in the order they were written")
    void readReturnsItemsInOrder() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 0);
        rb.write(10);
        rb.write(20);
        rb.write(30);

        assertEquals(Optional.of(10), r.read());
        assertEquals(Optional.of(20), r.read());
        assertEquals(Optional.of(30), r.read());
        assertEquals(Optional.empty(), r.read(), "buffer is drained for this reader");
    }

    @Test
    @DisplayName("position() advances by 1 per successful read")
    void positionAdvancesAfterRead() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 0);
        rb.write(1);
        rb.write(2);
        assertEquals(0L, r.position());
        r.read();
        assertEquals(1L, r.position());
        r.read();
        assertEquals(2L, r.position());
    }

    @Test
    @DisplayName("position() does NOT advance when read() finds the buffer empty")
    void positionStableWhenNothingToRead() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
        long before = r.position();
        assertEquals(Optional.empty(), r.read());
        assertEquals(Optional.empty(), r.read());
        assertEquals(before, r.position());
    }

    @Test
    @DisplayName("a slow reader that fell behind is fast-forwarded to oldest available")
    void slowReaderSkipsOverwrittenItems() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 0);
        // Reader sits at seq 0; writer pushes 6 items into a 3-slot buffer.
        for (int i = 1; i <= 6; i++) rb.write(i);

        // oldestAvailable = 6 - 3 = 3, so the reader fast-forwards to seq 3.
        // Slot 0 holds the LAST overwrite (value 4), so seq 3 reads back as 4.
        Optional<Integer> first = r.read();
        assertEquals(Optional.of(4), first);
        assertEquals(4L, r.position());
    }

    @Test
    @DisplayName("FROM_NOW reader skips already-existing items")
    void fromNowSkipsExisting() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        rb.write(1);
        rb.write(2);
        rb.write(3);

        Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
        assertEquals(Optional.empty(), r.read(), "nothing new to read yet");

        rb.write(4);
        assertEquals(Optional.of(4), r.read());
        assertEquals(Optional.empty(), r.read());
    }

    @Test
    @DisplayName("offset shifts the starting position forward")
    void readWithOffset() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        rb.write(1);
        rb.write(2);
        rb.write(3);
        rb.write(4);

        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 2);
        assertEquals(Optional.of(3), r.read());
        assertEquals(Optional.of(4), r.read());
        assertEquals(Optional.empty(), r.read());
    }

    @Test
    @DisplayName("ringIndex() == position() % capacity")
    void ringIndexComputedModuloCapacity() {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
        assertEquals(0, r.ringIndex());
        rb.write(1);
        rb.write(2);
        rb.write(3);
        rb.write(4);
        // Reader was created FROM_NOW at seq 0. Now writeSeq=4, oldest=1.
        // First read fast-forwards to oldest (seq 1) then increments to 2.
        r.read();
        assertEquals(2L, r.position());
        assertEquals(2 % 3, r.ringIndex());
    }

    @Test
    @DisplayName("two readers see the same item independently — reads do not 'consume' for others")
    void readsDoNotConsumeForOtherReaders() {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        Reader<Integer> a = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "A", 0, 0);
        Reader<Integer> b = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "B", 0, 0);
        rb.write(100);
        rb.write(200);

        assertEquals(Optional.of(100), a.read());
        assertEquals(Optional.of(200), a.read());
        // b's cursor is independent; the same items are still readable.
        assertEquals(Optional.of(100), b.read());
        assertEquals(Optional.of(200), b.read());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("run() exits when the thread is interrupted")
    void runIsInterruptable() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 1, 0);

        // Suppress the println noise from Reader.run().
        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(new ByteArrayOutputStream())) {
            System.setOut(sink);
            Thread t = new Thread(r, "reader-test");
            t.start();
            Thread.sleep(40);
            t.interrupt();
            t.join(2000);
            assertFalse(t.isAlive(), "reader thread should stop after interrupt");
        } finally {
            System.setOut(original);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("run() consumes items written concurrently")
    void runReadsDataConcurrently() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(50);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 1, 0);

        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(new ByteArrayOutputStream())) {
            System.setOut(sink);
            Thread t = new Thread(r, "reader-test");
            t.start();

            for (int i = 1; i <= 5; i++) {
                rb.write(i);
                Thread.sleep(10);
            }

            // Wait for the reader to drain the buffer.
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline && r.position() < 5) {
                Thread.sleep(10);
            }

            t.interrupt();
            t.join(2000);
            assertFalse(t.isAlive(), "reader thread should stop after interrupt");
            assertTrue(r.position() >= 5,
                    "reader should have advanced to at least seq 5, was " + r.position());
        } finally {
            System.setOut(original);
        }
    }
}

package ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Writer}.
 */
class WriterTest {

    /**
     * Helper: silence Writer's stdout chatter while exercising it on a thread.
     */
    private static <T> T withSilentStdout(IOExceptionThrowing<T> block) throws InterruptedException {
        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(new ByteArrayOutputStream())) {
            System.setOut(sink);
            return block.run();
        } finally {
            System.setOut(original);
        }
    }

    @FunctionalInterface
    private interface IOExceptionThrowing<T> {
        T run() throws InterruptedException;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Writer.run() pushes monotonically increasing values into the buffer")
    void writerWritesIncreasingValues() throws InterruptedException {
        // Capacity is intentionally large so the writer cannot lap the buffer
        // during this short run; that lets us assert on the first slot.
        RingBuffer<Integer> rb = new RingBuffer<>(10_000);
        long seq = withSilentStdout(() -> {
            Writer w = new Writer(rb, 2);
            Thread t = new Thread(w, "writer-test");
            t.start();
            Thread.sleep(80);
            t.interrupt();
            t.join(2000);
            assertFalse(t.isAlive(), "writer thread should stop after interrupt");
            return rb.writeSeqUnsafe();
        });
        assertTrue(seq >= 1, "writer should have produced at least one item, got writeSeq=" + seq);
        // Writer.counter starts at 1, so the very first item it produces is the integer 1.
        assertEquals(1, rb.getAtSeqUnsafe(0));
        // Subsequent values must be strictly increasing.
        for (long s = 1; s < seq; s++) {
            int prev = rb.getAtSeqUnsafe(s - 1);
            int curr = rb.getAtSeqUnsafe(s);
            assertTrue(curr == prev + 1,
                    "expected strictly +1 sequence, got prev=" + prev + " curr=" + curr + " at s=" + s);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Writer.run() exits even when interrupted while sleeping in a long delay")
    void writerInterruptableWhileSleeping() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(5);
        withSilentStdout(() -> {
            Writer w = new Writer(rb, 100_000); // very long sleep between writes
            Thread t = new Thread(w, "writer-test");
            t.start();
            Thread.sleep(40);
            t.interrupt();
            t.join(2000);
            assertFalse(t.isAlive(), "writer thread should stop while sleeping");
            return null;
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Writer eventually wraps the buffer when it produces more than capacity items")
    void writerWrapsAfterCapacity() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(3);
        long seq = withSilentStdout(() -> {
            Writer w = new Writer(rb, 1);
            Thread t = new Thread(w, "writer-test");
            t.start();
            // Wait for the writer to overrun the 3-slot buffer at least once.
            long deadline = System.currentTimeMillis() + 2000;
            while (rb.writeSeqUnsafe() < 5 && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            t.interrupt();
            t.join(2000);
            return rb.writeSeqUnsafe();
        });
        assertTrue(seq >= 5, "writer should have produced > capacity items, got " + seq);
        assertTrue(rb.oldestAvailableSeqUnsafe() > 0, "overwriting should have started");
    }
}

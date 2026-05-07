package ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style tests that exercise the single-writer / multi-reader
 * threading contract of {@link RingBuffer}.
 */
class ConcurrencyTest {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("a writer plus two readers run together without crashing or deadlocking")
    void singleWriterMultipleReadersDontCrash() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(10);
        Reader<Integer> a = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "A", 1, 0);
        Reader<Integer> b = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "B", 2, 0);

        PrintStream original = System.out;
        try (PrintStream sink = new PrintStream(new ByteArrayOutputStream())) {
            System.setOut(sink);

            Thread tw = new Thread(new Writer(rb, 1), "w");
            Thread ta = new Thread(a, "a");
            Thread tb = new Thread(b, "b");

            tw.start();
            ta.start();
            tb.start();

            Thread.sleep(150);

            tw.interrupt();
            ta.interrupt();
            tb.interrupt();

            tw.join(2000);
            ta.join(2000);
            tb.join(2000);

            assertFalse(tw.isAlive());
            assertFalse(ta.isAlive());
            assertFalse(tb.isAlive());
        } finally {
            System.setOut(original);
        }

        assertTrue(rb.writeSeqUnsafe() > 0, "writer should have produced items");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("a reader that keeps up sees a strictly increasing stream of writer values")
    void readerSeesStrictlyIncreasingStream() throws InterruptedException {
        // Capacity is large enough that the reader cannot fall behind during this run.
        RingBuffer<Integer> rb = new RingBuffer<>(200);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "R", 0, 0);

        Thread writer = new Thread(() -> {
            for (int i = 1; i <= 50; i++) {
                rb.write(i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "writer");
        writer.start();

        List<Integer> seen = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && seen.size() < 50) {
            Optional<Integer> v = r.read();
            v.ifPresent(seen::add);
            if (v.isEmpty()) {
                Thread.sleep(1);
                if (!writer.isAlive() && r.read().isEmpty()) break;
            }
        }
        writer.join(2000);

        assertFalse(seen.isEmpty(), "reader should have observed at least one item");
        for (int i = 1; i < seen.size(); i++) {
            assertTrue(seen.get(i) > seen.get(i - 1),
                    "expected strictly increasing values from a fast reader, got: " + seen);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("a slow reader on a small buffer never returns a duplicate or non-monotonic value")
    void slowReaderNeverReturnsNonMonotonic() throws InterruptedException {
        RingBuffer<Integer> rb = new RingBuffer<>(4);
        Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "slow", 0, 0);

        Thread writer = new Thread(() -> {
            for (int i = 1; i <= 200; i++) {
                rb.write(i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "writer");
        writer.start();

        List<Integer> seen = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Optional<Integer> v = r.read();
            v.ifPresent(seen::add);
            // Deliberately slow: yield aggressively so the writer overruns us.
            Thread.sleep(2);
            if (!writer.isAlive() && r.read().isEmpty()) break;
        }
        writer.join(2000);

        // We may have skipped items (that is the documented "slow reader" behavior),
        // but the values we DID see must still be strictly increasing.
        for (int i = 1; i < seen.size(); i++) {
            assertTrue(seen.get(i) > seen.get(i - 1),
                    "slow reader still must not see duplicates or out-of-order values, got: " + seen);
        }
    }
}

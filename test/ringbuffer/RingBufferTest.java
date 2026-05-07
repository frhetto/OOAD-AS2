package ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RingBuffer}.
 * Located in package {@code ringbuffer} so the suite can exercise
 * package-private invariants ({@code writeSeqUnsafe}, {@code oldestAvailableSeqUnsafe},
 * {@code getAtSeqUnsafe}, and the shared {@code lock}).
 */
class RingBufferTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects capacity == 0 with IllegalArgumentException")
        void rejectsZeroCapacity() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new RingBuffer<Integer>(0));
            assertTrue(ex.getMessage().toLowerCase().contains("capacity"),
                    "exception message should mention capacity, was: " + ex.getMessage());
        }

        @Test
        @DisplayName("rejects negative capacity")
        void rejectsNegativeCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(-1));
            assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(-100));
            assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(Integer.MIN_VALUE));
        }

        @Test
        @DisplayName("accepts capacity of 1")
        void acceptsCapacityOfOne() {
            RingBuffer<String> rb = new RingBuffer<>(1);
            assertEquals(1, rb.capacity());
        }

        @Test
        @DisplayName("stores capacity verbatim")
        void capacityIsStored() {
            assertEquals(5, new RingBuffer<Integer>(5).capacity());
            assertEquals(1024, new RingBuffer<Integer>(1024).capacity());
        }

        @Test
        @DisplayName("starts with writeSeq == 0 and oldestAvailable == 0")
        void initialState() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            assertEquals(0L, rb.writeSeqUnsafe());
            assertEquals(0L, rb.oldestAvailableSeqUnsafe());
        }
    }

    @Nested
    @DisplayName("write()")
    class WriteTests {

        @Test
        @DisplayName("rejects null with NullPointerException")
        void rejectsNull() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            NullPointerException ex = assertThrows(NullPointerException.class, () -> rb.write(null));
            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("increments writeSeq by 1 per write")
        void incrementsWriteSeq() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            assertEquals(0, rb.writeSeqUnsafe());
            rb.write(10);
            assertEquals(1, rb.writeSeqUnsafe());
            rb.write(20);
            assertEquals(2, rb.writeSeqUnsafe());
            rb.write(30);
            assertEquals(3, rb.writeSeqUnsafe());
        }

        @Test
        @DisplayName("stores items in slot = (seq - 1) % capacity")
        void storesItemsInOrder() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(1);
            rb.write(2);
            rb.write(3);
            // seq 0 was written by the FIRST call (with value 1) into slot 0
            assertEquals(1, rb.getAtSeqUnsafe(0));
            assertEquals(2, rb.getAtSeqUnsafe(1));
            assertEquals(3, rb.getAtSeqUnsafe(2));
        }

        @Test
        @DisplayName("overwrites oldest slot when buffer is full")
        void overwritesOldestWhenFull() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(1);
            rb.write(2);
            rb.write(3);
            rb.write(4); // overwrites slot 0
            assertEquals(4L, rb.writeSeqUnsafe());
            // seq 3 maps to slot 0, which was just rewritten with the value 4
            assertEquals(4, rb.getAtSeqUnsafe(3));
            assertEquals(2, rb.getAtSeqUnsafe(1));
            assertEquals(3, rb.getAtSeqUnsafe(2));
        }

        @Test
        @DisplayName("wraps writeIndex modulo capacity over many writes")
        void wrapsIndexCorrectly() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            for (int i = 1; i <= 7; i++) rb.write(i);
            assertEquals(7L, rb.writeSeqUnsafe());
            // After 7 writes (capacity 3): slot 0 = 7, slot 1 = 5, slot 2 = 6
            assertEquals(7, rb.getAtSeqUnsafe(6)); // 6 % 3 = 0
            assertEquals(5, rb.getAtSeqUnsafe(4)); // 4 % 3 = 1
            assertEquals(6, rb.getAtSeqUnsafe(5)); // 5 % 3 = 2
        }

        @Test
        @DisplayName("works with capacity 1 (every write overwrites the same slot)")
        void capacityOneAlwaysOverwrites() {
            RingBuffer<Integer> rb = new RingBuffer<>(1);
            rb.write(10);
            assertEquals(10, rb.getAtSeqUnsafe(0));
            rb.write(20);
            assertEquals(20, rb.getAtSeqUnsafe(1));
            rb.write(30);
            assertEquals(30, rb.getAtSeqUnsafe(2));
            assertEquals(3L, rb.writeSeqUnsafe());
        }

        @Test
        @DisplayName("supports generic types other than Integer")
        void supportsGenericTypes() {
            RingBuffer<String> rb = new RingBuffer<>(2);
            rb.write("hello");
            rb.write("world");
            assertEquals("hello", rb.getAtSeqUnsafe(0));
            assertEquals("world", rb.getAtSeqUnsafe(1));
        }

        @Test
        @DisplayName("unwritten slots remain null")
        void unwrittenSlotsAreNull() {
            RingBuffer<Integer> rb = new RingBuffer<>(5);
            rb.write(1);
            rb.write(2);
            assertEquals(1, rb.getAtSeqUnsafe(0));
            assertEquals(2, rb.getAtSeqUnsafe(1));
            // Slots 2..4 have never been written
            assertNull(rb.getAtSeqUnsafe(2));
            assertNull(rb.getAtSeqUnsafe(3));
            assertNull(rb.getAtSeqUnsafe(4));
        }
    }

    @Nested
    @DisplayName("oldestAvailableSeqUnsafe()")
    class OldestAvailableTests {

        @Test
        @DisplayName("returns 0 on an empty buffer")
        void zeroWhenEmpty() {
            assertEquals(0L, new RingBuffer<Integer>(5).oldestAvailableSeqUnsafe());
        }

        @Test
        @DisplayName("returns 0 when fewer than capacity writes have happened")
        void zeroWhenBelowCapacity() {
            RingBuffer<Integer> rb = new RingBuffer<>(5);
            rb.write(1);
            rb.write(2);
            rb.write(3);
            assertEquals(0L, rb.oldestAvailableSeqUnsafe());
        }

        @Test
        @DisplayName("returns 0 when exactly capacity writes have happened")
        void zeroAtExactlyCapacity() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(1);
            rb.write(2);
            rb.write(3);
            assertEquals(0L, rb.oldestAvailableSeqUnsafe());
        }

        @Test
        @DisplayName("advances to (writeSeq - capacity) once overwriting starts")
        void advancesAfterOverwrite() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            for (int i = 0; i < 10; i++) rb.write(i);
            assertEquals(7L, rb.oldestAvailableSeqUnsafe()); // 10 - 3 = 7
        }

        @Test
        @DisplayName("never returns a negative number")
        void neverNegative() {
            RingBuffer<Integer> rb = new RingBuffer<>(100);
            // Even with no writes, capacity > 0 -> oldest must clamp to 0, not -100
            assertEquals(0L, rb.oldestAvailableSeqUnsafe());
            rb.write(1);
            assertEquals(0L, rb.oldestAvailableSeqUnsafe());
        }
    }

    @Nested
    @DisplayName("createReader()")
    class CreateReaderTests {

        @Test
        @DisplayName("rejects null mode")
        void rejectsNullMode() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            assertThrows(NullPointerException.class,
                    () -> rb.createReader(null, "X", 100, 0));
        }

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            assertThrows(NullPointerException.class,
                    () -> rb.createReader(ReaderStart.FROM_NOW, null, 100, 0));
        }

        @Test
        @DisplayName("FROM_NOW on empty buffer starts at seq 0")
        void fromNowOnEmptyStartsAtZero() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
            assertEquals(0L, r.position());
        }

        @Test
        @DisplayName("FROM_NOW after writes starts at writeSeq")
        void fromNowStartsAtWriteSeq() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(1);
            rb.write(2);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, 0);
            assertEquals(2L, r.position());
        }

        @Test
        @DisplayName("FROM_OLDEST_AVAILABLE on empty buffer starts at 0")
        void fromOldestEmptyStartsAtZero() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 0);
            assertEquals(0L, r.position());
        }

        @Test
        @DisplayName("FROM_OLDEST_AVAILABLE after overwrite starts at oldest available")
        void fromOldestAfterOverwrite() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            for (int i = 1; i <= 10; i++) rb.write(i);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 0);
            assertEquals(7L, r.position()); // 10 - 3 = 7
        }

        @Test
        @DisplayName("positive offset is added to base")
        void positiveOffsetIsApplied() {
            RingBuffer<Integer> rb = new RingBuffer<>(5);
            rb.write(1);
            rb.write(2);
            rb.write(3);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "X", 0, 2);
            assertEquals(2L, r.position());
        }

        @Test
        @DisplayName("negative offset is clamped to 0")
        void negativeOffsetClampedToZero() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(1);
            rb.write(2);
            Reader<Integer> r = rb.createReader(ReaderStart.FROM_NOW, "X", 0, -5);
            assertEquals(2L, r.position()); // base = writeSeq = 2; max(0,-5) = 0
        }

        @Test
        @DisplayName("two readers of the same buffer keep independent cursors")
        void independentCursors() {
            RingBuffer<Integer> rb = new RingBuffer<>(5);
            rb.write(1);
            rb.write(2);
            Reader<Integer> a = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "A", 0, 0);
            Reader<Integer> b = rb.createReader(ReaderStart.FROM_NOW, "B", 0, 0);
            assertEquals(0L, a.position());
            assertEquals(2L, b.position());
            a.read();
            assertEquals(1L, a.position());
            assertEquals(2L, b.position(), "second reader's position must not be affected by the first reader's read()");
        }
    }

    @Nested
    @DisplayName("debugRing()")
    class DebugRingTests {

        @Test
        @DisplayName("contains nulls and zeroed counters when empty")
        void includesNullsForUnwrittenSlots() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            String s = rb.debugRing();
            assertTrue(s.contains("null"), "expected nulls, got: " + s);
            assertTrue(s.contains("nextWriteIndex=0"), "expected nextWriteIndex=0, got: " + s);
            assertTrue(s.contains("writeSeq=0"), "expected writeSeq=0, got: " + s);
        }

        @Test
        @DisplayName("reflects current write state")
        void reflectsWriteState() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            rb.write(11);
            rb.write(22);
            String s = rb.debugRing();
            assertTrue(s.contains("11"), s);
            assertTrue(s.contains("22"), s);
            assertTrue(s.contains("nextWriteIndex=2"), s);
            assertTrue(s.contains("writeSeq=2"), s);
        }

        @Test
        @DisplayName("nextWriteIndex wraps back to 0")
        void wrapsBackToZero() {
            RingBuffer<Integer> rb = new RingBuffer<>(3);
            for (int i = 1; i <= 6; i++) rb.write(i);
            String s = rb.debugRing();
            assertTrue(s.contains("nextWriteIndex=0"), s);
            assertTrue(s.contains("writeSeq=6"), s);
        }
    }
}

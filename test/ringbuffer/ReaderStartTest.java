package ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link ReaderStart} enum.
 */
class ReaderStartTest {

    @Test
    @DisplayName("declares exactly two values")
    void hasTwoValues() {
        assertEquals(2, ReaderStart.values().length);
    }

    @Test
    @DisplayName("contains FROM_NOW")
    void containsFromNow() {
        assertNotNull(ReaderStart.FROM_NOW);
        assertEquals(ReaderStart.FROM_NOW, ReaderStart.valueOf("FROM_NOW"));
    }

    @Test
    @DisplayName("contains FROM_OLDEST_AVAILABLE")
    void containsFromOldestAvailable() {
        assertNotNull(ReaderStart.FROM_OLDEST_AVAILABLE);
        assertEquals(ReaderStart.FROM_OLDEST_AVAILABLE, ReaderStart.valueOf("FROM_OLDEST_AVAILABLE"));
    }

    @Test
    @DisplayName("values() contains both members")
    void valuesContainsBoth() {
        boolean hasFromNow = false;
        boolean hasFromOldest = false;
        for (ReaderStart rs : ReaderStart.values()) {
            if (rs == ReaderStart.FROM_NOW) hasFromNow = true;
            if (rs == ReaderStart.FROM_OLDEST_AVAILABLE) hasFromOldest = true;
        }
        assertTrue(hasFromNow);
        assertTrue(hasFromOldest);
    }

    @Test
    @DisplayName("valueOf() rejects unknown names")
    void valueOfRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ReaderStart.valueOf("DOES_NOT_EXIST"));
    }
}

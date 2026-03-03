package ringbuffer;

public final class Writer implements Runnable {

    private final RingBuffer<Integer> buffer;
    private final long delayMs;
    private long counter = 1;

    public Writer(RingBuffer<Integer> buffer, long delayMs) {
        this.buffer = buffer;
        this.delayMs = delayMs;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            int value = (int) counter++;
            buffer.write(value);
            
            // Print ring after every write
            System.out.println("[W] write=" + value + " | " + buffer.debugRing());

            sleep(delayMs);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
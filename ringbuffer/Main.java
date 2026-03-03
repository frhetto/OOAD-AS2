package ringbuffer;

public final class Main {

    public static void main(String[] args) {
        int capacity = (args.length > 0) ? Integer.parseInt(args[0]) : 5;

        RingBuffer<Integer> rb = new RingBuffer<>(capacity);

        Reader<Integer> readerA = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "A", 150, 0);
        Reader<Integer> readerB = rb.createReader(ReaderStart.FROM_OLDEST_AVAILABLE, "B", 450, 1);

        new Thread(new Writer(rb, 120), "writer").start();
        new Thread(readerA, "reader-A").start();
        new Thread(readerB, "reader-B").start();
    }
}
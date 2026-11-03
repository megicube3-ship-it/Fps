package dev.fpsgov;

import dev.fpsgov.core.FrameStats;

/** Menghitung frame nyata (dipanggil dari mixin tiap frame render). Semua di main thread. */
public final class FrameClock {
    private static final int CAP = 8192; // harus pangkat 2
    private static final long[] TS = new long[CAP];
    private static int head;
    private static int size;
    private static long total;

    private FrameClock() {}

    public static void onFrame() {
        TS[head] = System.nanoTime();
        head = (head + 1) & (CAP - 1);
        if (size < CAP) size++;
        total++;
    }

    public static long total() { return total; }

    public static FrameStats stats(long windowMs) {
        return FrameStats.compute(TS, head, size, System.nanoTime(), windowMs * 1_000_000L);
    }
}

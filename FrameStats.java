package dev.fpsgov.core;

import java.util.Arrays;

/** Statistik frame dari ring buffer timestamp (nanodetik). Murni Java, tanpa API Minecraft. */
public final class FrameStats {
    public final int frames;
    public final double avgFps;
    public final double lowFps;      // FPS di persentil 95 frame-time terburuk ("5% low")
    public final double maxGapMs;    // gap terpanjang, termasuk jeda sejak frame terakhir sampai sekarang

    public FrameStats(int frames, double avgFps, double lowFps, double maxGapMs) {
        this.frames = frames;
        this.avgFps = avgFps;
        this.lowFps = lowFps;
        this.maxGapMs = maxGapMs;
    }

    public static final FrameStats EMPTY = new FrameStats(0, 0, 0, 0);

    /**
     * @param ts      ring buffer timestamp (ukuran harus pangkat 2)
     * @param head    indeks slot berikutnya yang akan ditulis
     * @param size    jumlah slot terisi
     * @param nowNs   waktu sekarang
     * @param windowNs panjang jendela
     */
    public static FrameStats compute(long[] ts, int head, int size, long nowNs, long windowNs) {
        int mask = ts.length - 1;
        long from = nowNs - windowNs;
        int n = 0;
        // hitung berapa frame di dalam jendela (dari yang terbaru ke belakang)
        for (int i = 0; i < size; i++) {
            long t = ts[(head - 1 - i) & mask];
            if (t < from) break;
            n++;
        }
        if (n < 2) {
            double stall = size > 0 ? (nowNs - ts[(head - 1) & mask]) / 1e6 : 0;
            return new FrameStats(n, 0, 0, stall);
        }
        double[] gaps = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            long a = ts[(head - 1 - i) & mask];
            long b = ts[(head - 2 - i) & mask];
            gaps[i] = (a - b) / 1e6;
        }
        double newest = ts[(head - 1) & mask];
        double sinceLast = (nowNs - newest) / 1e6;
        double maxGap = sinceLast;
        for (double g : gaps) if (g > maxGap) maxGap = g;
        double windowSec = windowNs / 1e9;
        double avg = n / windowSec;
        double[] sorted = gaps.clone();
        Arrays.sort(sorted);
        double p95 = sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1)];
        double low = p95 > 0 ? 1000.0 / p95 : 0;
        return new FrameStats(n, avg, low, maxGap);
    }
}

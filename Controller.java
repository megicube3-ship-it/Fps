package dev.fpsgov.core;

/**
 * Pengendali adaptif: menurunkan "level kualitas" saat FPS di bawah target dan menaikkannya lagi
 * hanya bila yakin FPS tetap di atas target. Dipanggil sekali per detik.
 *
 * Fitur pengaman:
 *  - histeresis (turun cepat, naik lambat),
 *  - tahap "settle + measure" setelah tiap perubahan (chunk reload tidak dihitung),
 *  - deteksi "tidak ada gain": bila turun satu level tidak menaikkan FPS, langsung di-UNDO
 *    dan penurunan diblokir dengan backoff eksponensial (hindari flicker setting tiap menit),
 *  - belajar rasio gain per level untuk memprediksi apakah aman naik lagi,
 *  - backoff bila baru naik lalu turun lagi (anti osilasi).
 */
public final class Controller {
    public enum Action { NONE, DEGRADE, IMPROVE, UNDO }
    public enum State { STABLE, SETTLING, MEASURING }

    public static final class Tuning {
        public int maxLevel = 6;
        public long settleMs = 4_000;
        public long measureMs = 3_000;
        public double degradeRatio = 0.92;   // turun bila avg < target * ini
        public double improveRatio = 1.30;   // fallback naik bila avg > target * ini (rasio belum dipelajari)
        public double improveMargin = 1.05;  // naik bila prediksi FPS setelah naik > target * ini
        public double minGain = 0.03;        // gain < 3% => dianggap tidak berguna
        public int degradeSeconds = 4;
        public int severeSeconds = 2;        // bila FPS < 60% target
        public long upHoldMs = 20_000;
        public long maxUpHoldMs = 300_000;
        public long blockMs = 120_000;
        public long maxBlockMs = 1_800_000;
        public long resetBackoffAfterMs = 300_000;
    }

    private final Tuning t;
    private int level;
    private State state = State.STABLE;
    private long stateSince;
    private Action lastAction = Action.NONE;
    private double fpsBefore;
    private int below, above;
    private long upHoldMs;
    private long lastChangeMs = Long.MIN_VALUE / 4;
    private long lastImproveMs = Long.MIN_VALUE / 4;
    private long blockedUntilMs = Long.MIN_VALUE / 4;
    private long blockMs;
    private final double[] gainRatio;   // gainRatio[L] = FPS(L) / FPS(L-1) yang diukur, 0 = belum tahu
    private int changes;

    public Controller() { this(new Tuning()); }

    public Controller(Tuning tuning) {
        this.t = tuning;
        this.gainRatio = new double[tuning.maxLevel + 1];
        reset();
    }

    public void reset() {
        level = 0;
        state = State.STABLE;
        lastAction = Action.NONE;
        below = above = 0;
        upHoldMs = t.upHoldMs;
        blockMs = t.blockMs;
        blockedUntilMs = Long.MIN_VALUE / 4;
        lastChangeMs = Long.MIN_VALUE / 4;
        lastImproveMs = Long.MIN_VALUE / 4;
        java.util.Arrays.fill(gainRatio, 0);
        changes = 0;
    }

    /** Dipanggil saat kondisi tidak layak dinilai (layar menu, loading, game di background, dll). */
    public void pause() {
        below = above = 0;
        if (state != State.STABLE) state = State.STABLE;
    }

    public int level() { return level; }
    public State state() { return state; }
    public int changes() { return changes; }
    public long blockedUntilMs() { return blockedUntilMs; }

    /** Paksa ke level tertentu tanpa evaluasi (dipakai saat setting dipulihkan manual). */
    public void forceLevel(int newLevel) {
        level = Math.max(0, Math.min(t.maxLevel, newLevel));
        state = State.STABLE;
        below = above = 0;
    }

    public Action update(long nowMs, double avgFps, double target) {
        switch (state) {
            case SETTLING:
                if (nowMs - stateSince >= t.settleMs) { state = State.MEASURING; stateSince = nowMs; }
                return Action.NONE;
            case MEASURING:
                if (nowMs - stateSince < t.measureMs) return Action.NONE;
                state = State.STABLE;
                stateSince = nowMs;
                below = above = 0;
                if (lastAction == Action.DEGRADE) {
                    double ratio = fpsBefore > 0 ? avgFps / fpsBefore : 2.0;
                    if (ratio - 1.0 < t.minGain && level > 0) {
                        level--;
                        lastAction = Action.UNDO;
                        blockedUntilMs = nowMs + blockMs;
                        blockMs = Math.min(blockMs * 2, t.maxBlockMs);
                        state = State.SETTLING;
                        stateSince = nowMs;
                        lastChangeMs = nowMs;
                        changes++;
                        return Action.UNDO;
                    }
                    gainRatio[level] = Math.max(1.0, Math.min(3.0, ratio));
                }
                return Action.NONE;
            default:
                break;
        }

        if (nowMs - lastChangeMs > t.resetBackoffAfterMs) upHoldMs = t.upHoldMs;

        double degradeBelow = target * t.degradeRatio;
        double improveAbove;
        if (level > 0 && gainRatio[level] > 0) {
            improveAbove = target * t.improveMargin * gainRatio[level];
        } else {
            improveAbove = target * t.improveRatio;
        }

        if (avgFps < degradeBelow) { below++; above = 0; }
        else if (avgFps > improveAbove) { above++; below = 0; }
        else { below = 0; above = 0; }

        boolean severe = avgFps < target * 0.6;
        int needBelow = severe ? t.severeSeconds : t.degradeSeconds;

        if (below >= needBelow && level < t.maxLevel && nowMs >= blockedUntilMs) {
            fpsBefore = avgFps;
            level++;
            lastAction = Action.DEGRADE;
            if (nowMs - lastImproveMs < 60_000) upHoldMs = Math.min(upHoldMs * 2, t.maxUpHoldMs);
            state = State.SETTLING;
            stateSince = nowMs;
            lastChangeMs = nowMs;
            below = above = 0;
            changes++;
            return Action.DEGRADE;
        }
        if (level > 0 && above * 1000L >= upHoldMs) {
            level--;
            lastAction = Action.IMPROVE;
            lastImproveMs = nowMs;
            state = State.SETTLING;
            stateSince = nowMs;
            lastChangeMs = nowMs;
            below = above = 0;
            changes++;
            return Action.IMPROVE;
        }
        return Action.NONE;
    }
}

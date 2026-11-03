package dev.fpsgov;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/** Menerapkan "level kualitas" ke opsi vanilla dan mengingat nilai asli pemain. */
final class OptionsApplier {
    /** Pengurangan render distance (chunk) per level. */
    private static final int[] RD_DROP = {0, 1, 2, 3, 4, 6, 8};

    private boolean captured;
    private int level;

    private int baseRd;
    private int baseRdEff;
    private double baseEntity;
    private int baseParticles;      // ordinal: 0=semua, 1=dikurangi, 2=minimal

    private int curRd;
    private double curEntity;
    private int curParticles;

    boolean isCaptured() { return captured; }
    int level() { return level; }

    void capture(MinecraftClient mc) {
        GameOptions o = mc.options;
        baseRd = o.getViewDistance().getValue();
        baseRdEff = Math.min(baseRd, o.getClampedViewDistance());
        baseEntity = o.getEntityDistanceScaling().getValue();
        baseParticles = o.getParticles().getValue().ordinal();
        curRd = baseRd;
        curEntity = baseEntity;
        curParticles = baseParticles;
        level = 0;
        captured = true;
    }

    /** True bila pemain mengubah salah satu opsi sendiri sejak terakhir kali kita menerapkan. */
    boolean userChanged(MinecraftClient mc) {
        if (!captured) return false;
        GameOptions o = mc.options;
        return o.getViewDistance().getValue() != curRd
                || Math.abs(o.getEntityDistanceScaling().getValue() - curEntity) > 1e-6
                || o.getParticles().getValue().ordinal() != curParticles;
    }

    void apply(MinecraftClient mc, int newLevel, Config cfg) {
        if (!captured) return;
        GameOptions o = mc.options;
        level = newLevel;

        int rd = baseRd;
        if (newLevel > 0 && cfg.tuneRenderDistance) {
            int wanted = Math.max(Math.max(2, cfg.minRenderDistance), baseRdEff - RD_DROP[Math.min(newLevel, RD_DROP.length - 1)]);
            rd = Math.min(baseRdEff, wanted);
        }

        double entity = baseEntity;
        if (newLevel > 0 && cfg.tuneEntityDistance) {
            int steps = (newLevel + 1) / 2;
            double wanted = Math.floor((baseEntity - 0.25 * steps) * 4.0) / 4.0;
            entity = Math.min(baseEntity, Math.max(cfg.minEntityScale, wanted));
        }

        int particles = baseParticles;
        if (newLevel > 0 && cfg.tuneParticles) {
            int wantedOrd = newLevel >= 4 ? 2 : (newLevel >= 2 ? 1 : 0);
            particles = Math.max(baseParticles, wantedOrd);
        }

        boolean rdChanged = o.getViewDistance().getValue() != rd;
        if (rdChanged) o.getViewDistance().setValue(rd);
        if (Math.abs(o.getEntityDistanceScaling().getValue() - entity) > 1e-6) o.getEntityDistanceScaling().setValue(entity);
        setParticles(o, particles);

        // baca balik: nilai yang benar-benar dipakai game (bisa saja divalidasi ulang)
        curRd = o.getViewDistance().getValue();
        curEntity = o.getEntityDistanceScaling().getValue();
        curParticles = o.getParticles().getValue().ordinal();

        if (rdChanged) {
            try {
                o.sendClientSettings(); // beri tahu server jarak pandang baru supaya kirim chunk lebih sedikit
            } catch (Throwable ignored) {
                // tidak kritis
            }
        }
    }

    /** Kembalikan nilai asli pemain. */
    void restore(MinecraftClient mc, boolean notifyServer) {
        if (!captured) return;
        if (level != 0) {
            GameOptions o = mc.options;
            // hanya kembalikan bila nilai saat ini memang hasil kita (bukan perubahan pemain)
            if (!userChanged(mc)) {
                boolean rdChanged = o.getViewDistance().getValue() != baseRd;
                o.getViewDistance().setValue(baseRd);
                o.getEntityDistanceScaling().setValue(baseEntity);
                setParticles(o, baseParticles);
                if (rdChanged && notifyServer) {
                    try { o.sendClientSettings(); } catch (Throwable ignored) { }
                }
            }
        }
        level = 0;
        captured = false;
    }

    /** Set mode partikel tanpa menyebut nama kelas enum-nya (nama itu berubah antar versi). */
    private static void setParticles(GameOptions o, int ord) {
        var opt = o.getParticles();
        var current = opt.getValue();
        if (current.ordinal() == ord) return;
        var all = current.getDeclaringClass().getEnumConstants();
        if (ord < 0 || ord >= all.length) return;
        opt.setValue(all[ord]);
    }

    String describe(MinecraftClient mc) {
        if (!captured) return "belum ditangkap";
        GameOptions o = mc.options;
        return "renderDist " + baseRd + "->" + o.getViewDistance().getValue()
                + ", entity " + baseEntity + "->" + o.getEntityDistanceScaling().getValue()
                + ", partikel(0=semua,1=dikurangi,2=minimal) " + baseParticles + "->" + o.getParticles().getValue().ordinal();
    }
}

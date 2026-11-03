package dev.fpsgov;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;

/** Menerapkan "level kualitas" ke opsi vanilla dan mengingat nilai asli pemain. */
final class OptionsApplier {
    /** Pengurangan render distance (chunk) per level. */
    private static final int[] RD_DROP = {0, 1, 2, 3, 4, 6, 8};

    private boolean captured;
    private int level;

    private int baseRd;
    private int baseRdEff;
    private double baseEntity;
    private ParticlesMode baseParticles;

    private int curRd;
    private double curEntity;
    private ParticlesMode curParticles;

    boolean isCaptured() { return captured; }
    int level() { return level; }

    void capture(MinecraftClient mc) {
        GameOptions o = mc.options;
        baseRd = o.getViewDistance().getValue();
        baseRdEff = Math.min(baseRd, o.getClampedViewDistance());
        baseEntity = o.getEntityDistanceScaling().getValue();
        baseParticles = o.getParticles().getValue();
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
                || o.getParticles().getValue() != curParticles;
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

        ParticlesMode particles = baseParticles;
        if (newLevel > 0 && cfg.tuneParticles) {
            int wantedOrd = newLevel >= 4 ? 2 : (newLevel >= 2 ? 1 : 0);
            ParticlesMode[] all = ParticlesMode.values();
            particles = all[Math.min(all.length - 1, Math.max(baseParticles.ordinal(), wantedOrd))];
        }

        boolean rdChanged = o.getViewDistance().getValue() != rd;
        if (rdChanged) o.getViewDistance().setValue(rd);
        if (Math.abs(o.getEntityDistanceScaling().getValue() - entity) > 1e-6) o.getEntityDistanceScaling().setValue(entity);
        if (o.getParticles().getValue() != particles) o.getParticles().setValue(particles);

        // baca balik: nilai yang benar-benar dipakai game (bisa saja divalidasi ulang)
        curRd = o.getViewDistance().getValue();
        curEntity = o.getEntityDistanceScaling().getValue();
        curParticles = o.getParticles().getValue();

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
                o.getParticles().setValue(baseParticles);
                if (rdChanged && notifyServer) {
                    try { o.sendClientSettings(); } catch (Throwable ignored) { }
                }
            }
        }
        level = 0;
        captured = false;
    }

    String describe(MinecraftClient mc) {
        if (!captured) return "belum ditangkap";
        GameOptions o = mc.options;
        return "renderDist " + baseRd + "->" + o.getViewDistance().getValue()
                + ", entity " + baseEntity + "->" + o.getEntityDistanceScaling().getValue()
                + ", partikel " + baseParticles + "->" + o.getParticles().getValue();
    }
}

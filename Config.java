package dev.fpsgov;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Konfigurasi di .minecraft/config/fpsgovernor.json (bisa diubah juga lewat perintah /fpsgov). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public boolean enabled = true;
    /** FPS yang dituju tanpa shader. */
    public int targetFps = 60;
    /** FPS yang dituju saat shader pack aktif (Iris). */
    public int shaderTargetFps = 40;
    /** Batas bawah render distance (chunk) yang boleh dipakai governor. */
    public int minRenderDistance = 6;
    /** Batas bawah entity distance scaling (kelipatan 0.25, minimum vanilla 0.5). */
    public double minEntityScale = 0.75;
    public boolean tuneRenderDistance = true;
    public boolean tuneEntityDistance = true;
    public boolean tuneParticles = true;
    /** Detik setelah masuk dunia/server sebelum governor mulai menilai. */
    public int warmupSeconds = 20;

    public static Config get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fpsgovernor.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            }
        } catch (Throwable t) {
            FpsGovernorClient.LOG.warn("[FPS Governor] gagal membaca config, pakai default: {}", t.toString());
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException t) {
            FpsGovernorClient.LOG.warn("[FPS Governor] gagal menyimpan config: {}", t.toString());
        }
    }

    private void sanitize() {
        targetFps = Math.max(10, Math.min(240, targetFps));
        shaderTargetFps = Math.max(10, Math.min(240, shaderTargetFps));
        minRenderDistance = Math.max(2, Math.min(32, minRenderDistance));
        minEntityScale = Math.max(0.5, Math.min(5.0, Math.floor(minEntityScale * 4) / 4.0));
        warmupSeconds = Math.max(5, Math.min(120, warmupSeconds));
    }
}

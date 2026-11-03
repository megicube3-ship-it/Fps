package dev.fpsgov;

import dev.fpsgov.core.Controller;
import dev.fpsgov.core.FrameStats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FpsGovernorClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("fpsgovernor");

    static final Controller CONTROLLER = new Controller();
    static final OptionsApplier APPLIER = new OptionsApplier();

    static FrameStats lastStats = FrameStats.EMPTY;
    static double lastTarget = 60;
    static boolean lastShaders;
    static boolean lastEligible;

    private static int tickCounter;
    private static long joinedAtMs;
    private static boolean needCapture = true;
    private static boolean hookWarned;

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(FpsGovernorClient::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(FpsGovernorClient::onStopping);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> GovCommands.register(dispatcher));
        LOG.info("[FPS Governor] aktif. Perintah: /fpsgov status");
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }

    private static void onJoin() {
        joinedAtMs = nowMs();
        needCapture = true;
        CONTROLLER.reset();
        hookWarned = false;
    }

    private static void onDisconnect(MinecraftClient client) {
        APPLIER.restore(client, false);
        CONTROLLER.reset();
        joinedAtMs = 0;
        needCapture = true;
    }

    private static void onStopping(MinecraftClient client) {
        APPLIER.restore(client, false);
    }

    /** Dipanggil dari perintah /fpsgov off atau reset. */
    static void releaseNow(MinecraftClient client) {
        APPLIER.restore(client, true);
        CONTROLLER.reset();
        needCapture = true;
    }

    private static void onTick(MinecraftClient client) {
        if (++tickCounter < 20) return;
        tickCounter = 0;
        try {
            everySecond(client);
        } catch (Throwable t) {
            LOG.warn("[FPS Governor] error di loop utama: {}", t.toString());
        }
    }

    private static void everySecond(MinecraftClient mc) {
        Config cfg = Config.get();
        if (!cfg.enabled) return;

        boolean inWorld = mc.world != null && mc.player != null;
        if (!inWorld) {
            CONTROLLER.pause();
            lastEligible = false;
            return;
        }
        long now = nowMs();
        if (joinedAtMs == 0) joinedAtMs = now;

        FrameStats s = FrameClock.stats(5000);
        lastStats = s;

        if (FrameClock.total() == 0 && now - joinedAtMs > 10_000 && !hookWarned) {
            hookWarned = true;
            LOG.warn("[FPS Governor] hook frame tidak terpasang (mixin gagal) - governor tidak bisa mengukur FPS.");
        }

        boolean eligible = mc.currentScreen == null
                && now - joinedAtMs >= cfg.warmupSeconds * 1000L
                && s.frames >= 8
                && s.maxGapMs < 2500;
        lastEligible = eligible;
        if (!eligible) {
            CONTROLLER.pause();
            return;
        }

        if (needCapture) {
            APPLIER.capture(mc);
            CONTROLLER.reset();
            needCapture = false;
        } else if (APPLIER.userChanged(mc)) {
            LOG.info("[FPS Governor] pengaturan diubah pemain, base direset.");
            APPLIER.capture(mc);
            CONTROLLER.reset();
        }

        boolean shaders = ShaderDetect.active();
        double target = shaders ? cfg.shaderTargetFps : cfg.targetFps;
        int cap = mc.options.getMaxFps().getValue();
        if (cap < 260) target = Math.min(target, Math.max(10, cap - 2)); // jangan kejar target di atas batas FPS pemain
        lastShaders = shaders;
        lastTarget = target;

        Controller.Action action = CONTROLLER.update(now, s.avgFps, target);
        if (action != Controller.Action.NONE) {
            APPLIER.apply(mc, CONTROLLER.level(), cfg);
            LOG.info("[FPS Governor] {} -> level {} | fps {} target {} shader={} | {}",
                    action, CONTROLLER.level(), Math.round(s.avgFps), Math.round(target), shaders, APPLIER.describe(mc));
        }
    }
}

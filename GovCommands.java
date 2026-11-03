package dev.fpsgov;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

final class GovCommands {
    private GovCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("fpsgov")
                .executes(c -> status(c.getSource()))
                .then(ClientCommandManager.literal("status").executes(c -> status(c.getSource())))
                .then(ClientCommandManager.literal("on").executes(c -> {
                    Config.get().enabled = true;
                    Config.save();
                    say(c.getSource(), "FPS Governor: ON");
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(c -> {
                    Config.get().enabled = false;
                    Config.save();
                    FpsGovernorClient.releaseNow(MinecraftClient.getInstance());
                    say(c.getSource(), "FPS Governor: OFF (pengaturan asli dipulihkan)");
                    return 1;
                }))
                .then(ClientCommandManager.literal("reset").executes(c -> {
                    FpsGovernorClient.releaseNow(MinecraftClient.getInstance());
                    say(c.getSource(), "Dipulihkan ke pengaturan asli, penilaian dimulai ulang.");
                    return 1;
                }))
                .then(ClientCommandManager.literal("target")
                        .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(10, 240)).executes(c -> {
                            Config.get().targetFps = IntegerArgumentType.getInteger(c, "fps");
                            Config.save();
                            say(c.getSource(), "Target FPS (tanpa shader) = " + Config.get().targetFps);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("shadertarget")
                        .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(10, 240)).executes(c -> {
                            Config.get().shaderTargetFps = IntegerArgumentType.getInteger(c, "fps");
                            Config.save();
                            say(c.getSource(), "Target FPS (dengan shader) = " + Config.get().shaderTargetFps);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("minrd")
                        .then(ClientCommandManager.argument("chunks", IntegerArgumentType.integer(2, 32)).executes(c -> {
                            Config.get().minRenderDistance = IntegerArgumentType.getInteger(c, "chunks");
                            Config.save();
                            say(c.getSource(), "Render distance minimum = " + Config.get().minRenderDistance + " chunk");
                            return 1;
                        }))));
    }

    private static void say(FabricClientCommandSource src, String msg) {
        src.sendFeedback(Text.literal(msg));
    }

    private static int status(FabricClientCommandSource src) {
        Config cfg = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        say(src, "FPS Governor " + (cfg.enabled ? "ON" : "OFF")
                + " | level " + FpsGovernorClient.CONTROLLER.level() + "/6"
                + " | status " + FpsGovernorClient.CONTROLLER.state()
                + (FpsGovernorClient.lastEligible ? "" : " (menunggu: warmup/menu/loading)"));
        say(src, "FPS rata-rata 5 dtk: " + Math.round(FpsGovernorClient.lastStats.avgFps)
                + " | 5% low: " + Math.round(FpsGovernorClient.lastStats.lowFps)
                + " | target: " + Math.round(FpsGovernorClient.lastTarget)
                + (FpsGovernorClient.lastShaders ? " (shader aktif)" : ""));
        say(src, FpsGovernorClient.APPLIER.describe(mc));
        if (FrameClock.total() == 0) say(src, "PERINGATAN: hook frame belum aktif, cek latest.log.");
        return 1;
    }
}

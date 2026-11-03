package dev.fpsgov.mixin;

import dev.fpsgov.FrameClock;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Dipanggil sekali per frame render. require = 0: kalau target tidak ketemu, game tetap jalan. */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void fpsgov$onFrame(CallbackInfo ci) {
        FrameClock.onFrame();
    }
}

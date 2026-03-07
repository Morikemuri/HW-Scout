package ru.minewatch.mod.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.minewatch.mod.MineHudRenderer;
import ru.minewatch.mod.ScoreboardReader;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mw_onTick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient)(Object)this;
        MineHudRenderer.INSTANCE.onClientTick(mc);
        ScoreboardReader.INSTANCE.onClientTick(mc);
    }
}

package ru.minewatch.mod.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.minewatch.mod.MineHudRenderer;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void mw_onRender(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        MineHudRenderer.INSTANCE.onRenderHud(matrices, tickDelta);
    }
}

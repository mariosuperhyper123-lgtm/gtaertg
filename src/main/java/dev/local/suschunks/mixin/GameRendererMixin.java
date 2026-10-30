package dev.local.suschunks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import dev.local.suschunks.SusChunkRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void suschunks$onClose(CallbackInfo ci) {
		SusChunkRenderer.close();
	}
}

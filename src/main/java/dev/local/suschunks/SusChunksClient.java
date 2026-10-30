package dev.local.suschunks;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class SusChunksClient implements ClientModInitializer {
	public static final String MOD_ID = "suschunks";
	public static volatile boolean enabled = true;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.suschunks.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				enabled = !enabled;
				if (client.player != null) {
					client.player.sendSystemMessage(Component.literal("Sus Chunks: " + (enabled ? "an" : "aus")));
				}
			}
			SusChunkScanner.tick(client);
		});

		SusChunkRenderer.init();
	}
}

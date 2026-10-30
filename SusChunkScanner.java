package dev.local.suschunks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Bewertet geladene Chunks anhand der Block-Entities, die der Server ohnehin an den Client schickt.
 * Es werden KEINE Pakete gesendet, der Server sieht davon nichts.
 */
public final class SusChunkScanner {
	/** Ab dieser Punktzahl gilt ein Chunk als verdaechtig. Zum Feintunen aendern. */
	public static final int THRESHOLD = 8;
	/** Scan-Intervall in Ticks (20 = 1x pro Sekunde). */
	private static final int INTERVAL = 20;

	public record Hit(int chunkX, int chunkZ, int score) { }

	public static volatile List<Hit> hits = List.of();
	private static int cooldown = 0;

	private SusChunkScanner() { }

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || !SusChunksClient.enabled) {
			hits = List.of();
			cooldown = 0;
			return;
		}
		if (--cooldown > 0) return;
		cooldown = INTERVAL;

		int radius = mc.options.renderDistance().get();
		int pcx = mc.player.getBlockX() >> 4;
		int pcz = mc.player.getBlockZ() >> 4;

		List<Hit> result = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int cx = pcx + dx;
				int cz = pcz + dz;
				ChunkAccess access = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (!(access instanceof LevelChunk chunk)) continue;

				int score = 0;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					score += weight(be);
				}
				if (score >= THRESHOLD) result.add(new Hit(cx, cz, score));
			}
		}
		hits = List.copyOf(result);
	}

	/** Welche Block-Entities zaehlen wie viel. Hier anpassen. */
	private static int weight(BlockEntity be) {
		if (be instanceof EnderChestBlockEntity) return 2;
		if (be instanceof ChestBlockEntity
				|| be instanceof BarrelBlockEntity
				|| be instanceof ShulkerBoxBlockEntity
				|| be instanceof AbstractFurnaceBlockEntity
				|| be instanceof HopperBlockEntity
				|| be instanceof DispenserBlockEntity
				|| be instanceof BrewingStandBlockEntity
				
				|| be instanceof SignBlockEntity) {
			return 1;
		}
		return 0;
	}
}

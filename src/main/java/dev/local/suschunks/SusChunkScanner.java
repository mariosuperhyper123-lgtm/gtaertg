package dev.local.suschunks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class SusChunkScanner {
    /** Ab dieser Punktzahl gilt ein Chunk als verdaechtig. */
    public static final int THRESHOLD = 8;
    /** Scan-Intervall in Ticks (20 = 1x pro Sekunde). */
    private static final int INTERVAL = 20;

    /** Geode: so viele ausgewachsene Cluster braucht ein Chunk mindestens. */
    private static final int GEODE_MIN_CLUSTERS = 3;
    /** Punkte, die ein voll ausgewachsener Geode gibt (= alleine genug zum Markieren). */
    private static final int GEODE_SCORE = THRESHOLD;

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
                score += geodeScore(chunk);
                if (score >= THRESHOLD) result.add(new Hit(cx, cz, score));
            }
        }
        hits = List.copyOf(result);
    }

    /** Voll ausgewachsener Geode: Budding Amethyst + genug Cluster + keine kleinen Knospen. */
    private static int geodeScore(LevelChunk chunk) {
        int clusters = 0;
        int young = 0;
        boolean budding = false;

        for (LevelChunkSection section : chunk.getSections()) {
            if (section == null || section.hasOnlyAir()) continue;
            // Ueberspringt Abschnitte, die sicher keinen Amethyst enthalten (schnell).
            if (!section.maybeHas(SusChunkScanner::isAmethyst)) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block b = section.getBlockState(x, y, z).getBlock();
                        if (b == Blocks.AMETHYST_CLUSTER) clusters++;
                        else if (b == Blocks.SMALL_AMETHYST_BUD
                                || b == Blocks.MEDIUM_AMETHYST_BUD
                                || b == Blocks.LARGE_AMETHYST_BUD) young++;
                        else if (b == Blocks.BUDDING_AMETHYST) budding = true;
                    }
                }
            }
        }
        if (budding && young == 0 && clusters >= GEODE_MIN_CLUSTERS) return GEODE_SCORE;
        return 0;
    }

    private static boolean isAmethyst(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.AMETHYST_CLUSTER
                || b == Blocks.SMALL_AMETHYST_BUD
                || b == Blocks.MEDIUM_AMETHYST_BUD
                || b == Blocks.LARGE_AMETHYST_BUD
                || b == Blocks.BUDDING_AMETHYST;
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

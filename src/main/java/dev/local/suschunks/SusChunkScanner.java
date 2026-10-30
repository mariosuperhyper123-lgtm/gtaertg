package dev.local.suschunks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class SusChunkScanner {
    /** Ab dieser Punktzahl gilt ein Chunk als verdaechtig. */
    public static final int THRESHOLD = 8;
    /** Scan-Intervall in Ticks (20 = 1x pro Sekunde). */
    private static final int INTERVAL = 20;
    /** Wie viele neue Chunks pro Durchlauf Block fuer Block gescannt werden (gegen Lags). */
    private static final int MAX_NEW_SCANS = 6;

    /** Geode: Mindestanzahl voll ausgewachsener Cluster. */
    private static final int GEODE_MIN_CLUSTERS = 3;
    private static final int GEODE_SCORE = THRESHOLD;

    /** Gedrehte Tiefenschiefer-Bloecke: auf dieser Y-Hoehe oder tiefer, mindestens so viele. */
    private static final int ROT_MAX_Y = 20;
    private static final int ROT_MIN = 3;
    private static final int ROT_SCORE = THRESHOLD;

    /** Voll ausgewachsener Kelp (Alter 25): mindestens so viele Bloecke im Chunk. */
    private static final int KELP_MIN = 5;
    private static final int KELP_SCORE = THRESHOLD;

    public record Hit(int chunkX, int chunkZ, int score) { }
    private record CacheEntry(LevelChunk chunk, int score) { }

    public static volatile List<Hit> hits = List.of();
    private static int cooldown = 0;
    private static final Map<Long, CacheEntry> cache = new HashMap<>();
    private static ClientLevel lastLevel = null;

    private SusChunkScanner() { }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !SusChunksClient.enabled) {
            hits = List.of();
            cooldown = 0;
            cache.clear();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            cache.clear();
            lastLevel = level;
        }
        if (--cooldown > 0) return;
        cooldown = INTERVAL;

        int minY = level.dimensionType().minY();
        int radius = mc.options.renderDistance().get();
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;

        List<Hit> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int newScans = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                ChunkAccess access = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) continue;

                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                seen.add(key);

                CacheEntry entry = cache.get(key);
                int blockScore;
                if (entry != null && entry.chunk() == chunk) {
                    blockScore = entry.score();
                } else if (newScans < MAX_NEW_SCANS) {
                    blockScore = blockScore(chunk, minY);
                    cache.put(key, new CacheEntry(chunk, blockScore));
                    newScans++;
                } else {
                    blockScore = entry != null ? entry.score() : 0;
                }

                int score = blockScore;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    score += weight(be);
                }
                if (score >= THRESHOLD) result.add(new Hit(cx, cz, score));
            }
        }
        cache.keySet().retainAll(seen);
        hits = List.copyOf(result);
    }

    /** Scannt die Bloecke eines Chunks: Geoden, gedrehter Tiefenschiefer, ausgewachsener Kelp. */
    private static int blockScore(LevelChunk chunk, int minY) {
        int clusters = 0;
        int young = 0;
        int rotated = 0;
        int kelp = 0;
        boolean budding = false;

        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(SusChunkScanner::isInteresting)) continue;

            int baseY = minY + i * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState s = section.getBlockState(x, y, z);
                        Block b = s.getBlock();
                        if (b == Blocks.DEEPSLATE) {
                            if (baseY + y <= ROT_MAX_Y
                                    && s.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
                                rotated++;
                            }
                        } else if (b == Blocks.KELP) {
                            if (s.getValue(BlockStateProperties.AGE_25) >= 25) kelp++;
                        } else if (b == Blocks.AMETHYST_CLUSTER) {
                            clusters++;
                        } else if (b == Blocks.SMALL_AMETHYST_BUD
                                || b == Blocks.MEDIUM_AMETHYST_BUD
                                || b == Blocks.LARGE_AMETHYST_BUD) {
                            young++;
                        } else if (b == Blocks.BUDDING_AMETHYST) {
                            budding = true;
                        }
                    }
                }
            }
        }

        int score = 0;
        if (budding && young == 0 && clusters >= GEODE_MIN_CLUSTERS) score += GEODE_SCORE;
        if (rotated >= ROT_MIN) score += ROT_SCORE;
        if (kelp >= KELP_MIN) score += KELP_SCORE;
        return score;
    }

    private static boolean isInteresting(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.DEEPSLATE
                || b == Blocks.KELP
                || b == Blocks.AMETHYST_CLUSTER
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

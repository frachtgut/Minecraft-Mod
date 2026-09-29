package net.starfallen.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.starfallen.Starfallen;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe cache: a structure spans many chunks, generated on several worker threads. */
public final class BlueprintCache {
    private static final ConcurrentHashMap<String, SoftReference<Blueprint>> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    @Nullable
    public static Blueprint get(String kind, BlockPos origin, Rotation rotation, long seed, int param) {
        String key = kind + "|" + origin.asLong() + "|" + rotation.ordinal() + "|" + seed + "|" + param;
        SoftReference<Blueprint> ref = CACHE.get(key);
        Blueprint bp = ref == null ? null : ref.get();
        if (bp != null) return bp;
        Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            ref = CACHE.get(key);
            bp = ref == null ? null : ref.get();
            if (bp != null) return bp;
            long start = System.nanoTime();
            bp = build(kind, origin, rotation, seed, param);
            if (bp == null) return null;
            bp.finish();
            CACHE.put(key, new SoftReference<>(bp));
            if (CACHE.size() > 64) CACHE.entrySet().removeIf(e -> e.getValue().get() == null);
            Starfallen.LOGGER.debug("Built {} blueprint ({} blocks) in {} ms", kind, bp.size(), (System.nanoTime() - start) / 1_000_000);
            return bp;
        }
    }

    @Nullable
    private static Blueprint build(String kind, BlockPos origin, Rotation rotation, long seed, int param) {
        Blueprint bp = new Blueprint(origin, rotation, seed);
        switch (kind) {
            case "observatory" -> ObservatoryBuilder.build(bp);
            case "sanctum" -> SanctumBuilder.build(bp, param);
            default -> {
                Starfallen.LOGGER.error("Unknown blueprint kind {}", kind);
                return null;
            }
        }
        return bp;
    }

    private BlueprintCache() {}
}

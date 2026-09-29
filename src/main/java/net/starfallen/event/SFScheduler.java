package net.starfallen.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/** Tiny server-side delayed task queue (echo strikes, meteor storms, staged effects). */
public final class SFScheduler {
    private record Task(ResourceKey<Level> dim, long runAt, Consumer<ServerLevel> action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;

    public static void schedule(ServerLevel level, int delay, Consumer<ServerLevel> action) {
        synchronized (PENDING) {
            PENDING.add(new Task(level.dimension(), now + Math.max(1, delay), action));
        }
    }

    static void tick(MinecraftServer server) {
        now++;
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Task> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.runAt <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            ServerLevel level = server.getLevel(t.dim);
            if (level != null) {
                try {
                    t.action.accept(level);
                } catch (Exception e) {
                    net.starfallen.Starfallen.LOGGER.error("Starfallen scheduled task failed", e);
                }
            }
        }
    }

    static void clear() {
        TASKS.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    private SFScheduler() {}
}

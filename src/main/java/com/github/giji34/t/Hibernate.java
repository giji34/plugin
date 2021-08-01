package com.github.giji34.t;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;

public class Hibernate {
    private final JavaPlugin owner;
    private final long delayMillis = 1000L;
    private int taskId = -1;

    public Hibernate(JavaPlugin owner) {
        this.owner = owner;
    }

    public boolean enable() {
        if (enabled()) {
            return false;
        }
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(owner, () -> {
            if (Bukkit.getServer().getOnlinePlayers().isEmpty()) {
                try {
                    Thread.sleep(delayMillis);
                    unloadChunks();
                } catch (Exception e) {
                }
            }
        }, 0L, 1L);
        return true;
    }

    public boolean disable() {
        if (!enabled()) {
            return false;
        }
        Bukkit.getScheduler().cancelTask(this.taskId);
        this.taskId = -1;
        return true;
    }

    private void unloadChunks() {
        Iterator iterator = Bukkit.getWorlds().iterator();
        int numUnloaded = 0;
        while (iterator.hasNext()) {
            World world = (World) iterator.next();
            Chunk[] chunks = world.getLoadedChunks();
            for (Chunk chunk : chunks) {
                if (chunk.unload(true)) {
                    numUnloaded++;
                }
            }
        }

        if (numUnloaded > 0) {
            owner.getLogger().info(String.format("[hibernate] Unloaded %d chunks", numUnloaded));
            long before = Runtime.getRuntime().freeMemory();
            System.gc();
            long after = Runtime.getRuntime().freeMemory();
            long freedMemoryByte = after - before;
            if (freedMemoryByte > 0) {
                owner.getLogger().info(String.format("[hibernate] %d byte memory freed using Java garbage collector", freedMemoryByte));
            }
        }
    }

    private boolean enabled() {
        return taskId > -1;
    }
}
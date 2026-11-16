package ca.spottedleaf.moonrise.patches.chunk_system.level.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Paper(Moonrise) 청크 하나의 엔티티 묶음 자리(N2b-6 — 플러그인이 쓰는 것만). {@link #getAllEntities} 는 Bukkit {@code Chunk#getEntities} 와 같은
 * 엔티티(그 청크 안, 플레이어 포함)의 NMS 손잡이다. 스레드 규칙도 그 Bukkit 메서드와 같다.
 */
public final class ChunkEntitySlices {

    public final int chunkX;
    public final int chunkZ;
    private final ServerLevel level;

    ChunkEntitySlices(ServerLevel level, int chunkX, int chunkZ) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public List<Entity> getAllEntities() {
        List<Entity> out = new ArrayList<>();
        for (org.bukkit.entity.Entity e : level.getWorld().getChunkAt(chunkX, chunkZ).getEntities()) {
            out.add(((org.bukkit.craftbukkit.entity.CraftEntity) e).getHandle());
        }
        return out;
    }

    public boolean isEmpty() { return getAllEntities().isEmpty(); }
}

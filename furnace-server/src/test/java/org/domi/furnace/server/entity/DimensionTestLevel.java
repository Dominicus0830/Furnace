// 차원 이름을 고를 수 있고 다른 차원 레벨을 이어 줄 수 있는 슈퍼플랫 시험 레벨(청크 ±8).
package org.domi.furnace.server.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;

public class DimensionTestLevel extends ChunkLevel {
    private static final SuperflatWorldSource GEN = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);
    final Map<Long, ChunkColumn> chunks = new HashMap<>();
    final Map<String, ChunkLevel> others = new HashMap<>();
    final String dimension;
    long time;
    Player player;
    long seed;

    public DimensionTestLevel(String dimension) {
        super(1000000);
        this.dimension = dimension;
    }

    /** 두 레벨을 서로 잇는다. */
    public static void link(DimensionTestLevel a, DimensionTestLevel b) {
        a.others.put(b.dimension, b);
        b.others.put(a.dimension, a);
    }

    @Override
    protected ChunkColumn chunk(int x, int z) {
        if (Math.abs(x) > 8 || Math.abs(z) > 8) return null;
        return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> GEN.generate(x, z));
    }

    @Override protected void markDirty(int x, int z) {}

    @Override protected void broadcastToChunk(int x, int z, Packet p) {}

    @Override
    protected LevelServices services() {
        return new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public String dimension() { return dimension; }
            @Override public long gameTime() { return time; }
            @Override public long seed() { return seed; }
            @Override public ChunkLevel otherLevel(String d) { return others.get(d); }
            @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
        };
    }

    /** 레벨의 엔티티를 n 틱 돌린다. */
    public void tickEntities(int n) {
        for (int t = 0; t < n; t++) {
            time++;
            for (Entity e : entities().tickSnapshot()) {
                if (e.isRemoved()) continue;
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
    }
}

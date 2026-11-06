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
    /** 플러그인 이벤트 시험용(기본은 모두 허용). */
    org.domi.furnace.server.plugin.GameEvents events = org.domi.furnace.server.plugin.GameEvents.NONE;
    /** 서버 차원 이동 시험용 대역(기본은 없음 = 예전 단일 스레드 경로). */
    LevelServices.DimensionTravel travel;
    /** 차원 타입(높이·좌표 비). 기본은 오버월드. */
    org.domi.furnace.server.world.DimensionType type = org.domi.furnace.server.world.DimensionType.OVERWORLD;

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
        return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> {
            ChunkColumn c = GEN.generate(x, z);
            c.poi(org.domi.furnace.server.world.poi.PoiManager.restore(c, x, z, null));   // 지옥문 POI 를 기록한다
            return c;
        });
    }

    @Override public org.domi.furnace.server.plugin.GameEvents gameEvents() { return events; }

    @Override protected void markDirty(int x, int z) {}

    @Override protected void broadcastToChunk(int x, int z, Packet p) {}

    @Override
    protected LevelServices services() {
        return new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public String dimension() { return dimension; }
            @Override public org.domi.furnace.server.world.DimensionType dimensionType() { return type; }
            @Override public long gameTime() { return time; }
            @Override public long seed() { return seed; }
            @Override public ChunkLevel otherLevel(String d) { return others.get(d); }
            @Override public DimensionTravel dimensionTravel() { return travel; }
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

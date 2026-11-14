// 장면용 시험 레벨: 슈퍼플랫 땅(청크 ±radius), 장면이 정하는 시계, 플레이어 목록, 방송 버림, 난수열은 이름에서(RandomSeeds.key).
// region·스케줄러·네트워크 없이 단일 스레드로 틱을 돌린다 — 장면 해시의 결정성을 위해서다(여러 region 스레드 순서는 비결정, O15 3.1절).
// tick() 은 바닐라 ServerLevel.tick 순서를 따른다: 예약 틱 → (무작위 틱 없음) → 블록 이벤트 → 엔티티 → 블록 엔티티 → 빛. 무작위 틱·자연 스폰은
// 장면이 자기 방식(setRandomTickSpeed 대신 직접 tickChunk)으로 부른다.
// 난수: 레벨 난수(random)는 시험 JVM 의 씨앗 켜짐 여부와 무관하게 이름에서 파생한 값으로 고정한다(같은 JVM 모드 장면도 결정적이게).
// soundRandom(소리 씨앗, 방송은 버려진다)·randValue(무작위 칸 고르기, tickChunk 만 쓴다)는 ChunkLevel 안에 private 이라 못 고정한다 —
// 그것을 결과에 쓰는 장면(무작위 틱·소리 해시)은 새 JVM 모드(씨앗 켜짐)로 둬야 한다. 같은 JVM 장면(fluid·piston·shared-pipeline)은 쓰지 않는다.
package org.domi.furnace.server.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.RandomSeeds;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;

public class SceneLevel extends ChunkLevel {
    private static final SuperflatWorldSource GEN = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

    private final Map<Long, ChunkColumn> chunks = new HashMap<>();
    private final int radius;
    private final LevelServices services;
    public final List<Player> players = new ArrayList<>();
    public long gameTime, dayTime;

    /** name: 난수열 열쇠(같은 이름이면 같은 레벨 난수열). radius: 청크 반지름(이 밖은 로드되지 않은 칸). */
    public SceneLevel(String name, int radius) {
        this(name, radius, RandomSeeds.key("scene:" + name));
    }

    private SceneLevel(String name, int radius, long ownerKey) {
        super(1000000, ownerKey);
        ((LegacyRandomSource) random()).setSeed(RandomSeeds.derive(ownerKey, RandomSeeds.Purpose.LEVEL));
        this.radius = radius;
        this.services = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return gameTime; }
            @Override public long dayTime() { return dayTime; }
            @Override public void forEachPlayer(Consumer<Player> c) { for (Player p : players) c.accept(p); }
        };
    }

    @Override
    protected ChunkColumn chunk(int x, int z) {
        if (Math.abs(x) > radius || Math.abs(z) > radius) return null;
        return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> GEN.generate(x, z));
    }

    @Override protected void markDirty(int x, int z) {}

    @Override protected void broadcastToChunk(int x, int z, Packet p) {}

    @Override protected LevelServices services() { return services; }

    /** 바닥 위 첫 칸의 y(슈퍼플랫: 풀 위 -60). 장면 좌표를 정할 때 쓴다. */
    public static final int FLOOR_Y = -60;

    /** 틱 하나: 시계 +1, 예약 틱 → 블록 이벤트 → (활성 범위) → 엔티티 → 블록 엔티티 → 빛 갱신. 활성 범위는 설정이 꺼져 있으면 아무것도 안 한다. */
    public void tick() {
        gameTime++;
        dayTime++;
        runScheduledTicks();
        runBlockEvents();
        activateEntities(players);
        tickEntities(k -> true);
        tickBlockEntities(k -> true);
        runLightUpdates();
    }
}

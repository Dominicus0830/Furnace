// A11-02 회귀: 눈 높이 칸이 질식 블록이면 바닐라는 플레이어도 in_wall 피해 1 을 입는다(Entity.isInWall → LivingEntity.baseTick, btn.aw 오프셋 91~229).
// 관전자(noPhysics)는 아니다. in_wall 이면 같은 틱의 월드 보더 피해는 건너뛴다(else if).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class A11SuffocationTest {

    /** 몸 두 칸을 돌로 채우고 n 틱 돌린 뒤 체력. */
    private static float healthAfterBuriedTicks(GameMode mode, int ticks) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        try {
            BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                    sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
            w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));   // world.probe() 가 블록 표를 쓴다
            w.awaitChunk(new ChunkPos(0, 0));
            Player p = new Player(1, UUID.randomUUID(), "Dom", new ConcurrentEmbeddedChannel());
            p.vitals().loadGameTypes(null, mode, false);
            p.publishPosition(0.5, -60, 0.5, 0, 0, true);
            RegionPos rp = new RegionPos(0, 0);
            sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
            for (int i = 0; i < 80; i++) sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();   // 접속 무적(60틱)이 지나게
            assertEquals(20.0F, p.vitals().health(), 0.0F);
            p.vitals().setFoodLevel(17);   // 자연 회복(허기 18 이상)이 끼지 않게
            // 발·눈 두 칸을 다 막는다(모래에 묻힌 꼴). 눈 칸만 막으면 바닐라도 updatePlayerPose 가 수영 자세(높이 0.6)로 바꿔 눈이 빠진다.
            sched.region(rp).execute(() -> {
                w.regionAt(rp).level().setBlock(new Pos(0, -60, 0), B.defaultState(B.id("stone")), 3);
                w.regionAt(rp).level().setBlock(new Pos(0, -59, 0), B.defaultState(B.id("stone")), 3);
            }).get();
            for (int i = 0; i < ticks; i++) sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
            return p.vitals().health();
        } finally {
            sched.close();
        }
    }

    @Test
    void playerWithStoneAtEyeLevelTakesInWallDamage() throws Exception {
        // 첫 틱 1 피해(무적 20), 무적 시간 > 10 동안 같은 1 은 막히고 10틱마다 다시 — 41틱이면 1 + 4 = 5
        assertEquals(15.0F, healthAfterBuriedTicks(GameMode.SURVIVAL, 41), 0.0F);
    }

    @Test
    void spectatorAndCreativeDoNotSuffocate() throws Exception {
        assertEquals(20.0F, healthAfterBuriedTicks(GameMode.SPECTATOR, 41), 0.0F, "관전자는 noPhysics");
        assertEquals(20.0F, healthAfterBuriedTicks(GameMode.CREATIVE, 41), 0.0F, "창조 모드는 무적");
    }
}

// region 틱에서 엔티티가 돌고(엔티티 틱 청크), 근처 플레이어가 줍고, 부순 블록이 아이템 엔티티를 떨구는지.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityRegionTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void nearbyPlayer_picksUpItem_duringRegionTick() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(w.entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        ItemEntity[] item = new ItemEntity[1];
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.adopt(p);
            r.chunkAtBlocking(new ChunkPos(0, 0));
            var level = r.level();
            item[0] = new ItemEntity(level, level.nextEntityId(), 1.5, -59.5, 0.5,
                    org.domi.furnace.server.block.B.stack("minecraft:dirt", 3), 0, 0, 0);
            level.addFreshEntity(item[0]);
            for (int i = 0; i < 3; i++) r.tick();
        }).get();
        assertTrue(item[0].isRemoved(), "닿는 거리(몸통 + 1, 0.5, 1)의 아이템은 줍는다");
        assertEquals(3, p.inventory().get(36).count());
        io.shutdown();
        sched.close();
    }

    @Test
    void brokenBlock_dropsItemEntity_whichFallsInTickingChunk() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(w.entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        int[] count = new int[1];
        double[] y = new double[1];
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.adopt(p);
            r.chunkAtBlocking(new ChunkPos(0, 0));
            var level = r.level();
            level.dropResources(level.getBlockState(new org.domi.furnace.server.block.Pos(2, -61, 2)), new org.domi.furnace.server.block.Pos(2, -55, 2),
                    null, null, ItemStack.EMPTY);   // 전리품 표가 없는 테스트 서비스 — 직접 떨군다
            level.popResource(new org.domi.furnace.server.block.Pos(2, -55, 2), org.domi.furnace.server.block.B.stack("minecraft:dirt", 1));
            for (int i = 0; i < 60; i++) r.tick();
            count[0] = level.entities().size();
            y[0] = level.entities().all().iterator().next().y();
        }).get();
        assertEquals(1, count[0]);
        assertEquals(-60.0, y[0], 0.0, "엔티티 틱 청크라 떨어져 바닥에 선다");
        io.shutdown();
        sched.close();
    }
}

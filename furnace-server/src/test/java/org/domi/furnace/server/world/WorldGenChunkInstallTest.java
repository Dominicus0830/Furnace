// 청크 설치(Region): 월드젠 엔티티(마을 주민·골렘·고양이 템플릿 엔티티)를 실제 엔티티로, 상자·침대 블록 엔티티를 청크 패킷에.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.Test;

class WorldGenChunkInstallTest {

    private static NbtCompound entity(String id, double x, double y, double z) {
        NbtCompound t = new NbtCompound();
        t.putString("id", id);
        NbtList pos = new NbtList((byte) 6);
        pos.add(x);
        pos.add(y);
        pos.add(z);
        t.put("Pos", pos);
        return t;
    }

    @Test
    void worldGenEntitiesAndChestsAreInstalled() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var flat = new SuperflatWorldSource(reg, 0);
        WorldSource src = (cx, cz) -> {
            ChunkColumn c = flat.generate(cx, cz);
            if (cx == 1 && cz == 1) {
                c.addGeneratedEntity(entity("minecraft:villager", 20.5, -60, 20.5));
                c.addGeneratedEntity(entity("minecraft:iron_golem", 22.5, -60, 20.5));
                c.addGeneratedEntity(entity("minecraft:cat", 24.5, -60, 20.5));
                c.setBlock(2, -60, 8, B.defaultState("chest"));   // NBT 없이 놓인 상자(DUMMY)
                c.setBlock(3, -60, 8, B.defaultState("chest"));   // 전리품 상자 NBT
                NbtCompound loot = new NbtCompound();
                loot.putString("id", "minecraft:chest");
                loot.putString("LootTable", "minecraft:chests/village/village_plains_house");
                c.putGeneratedBlockEntity(3, -60, 8, loot);
            }
            return c;
        };
        var io = Executors.newSingleThreadExecutor();
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            ChunkColumn col = r.chunkAtBlocking(new ChunkPos(1, 1));
            var ents = r.level().entities().tickSnapshot();
            assertTrue(ents.stream().anyMatch(e -> e instanceof org.domi.furnace.server.entity.Villager), "주민");
            assertTrue(ents.stream().anyMatch(e -> e instanceof org.domi.furnace.server.entity.IronGolem), "철 골렘");
            assertTrue(ents.stream().anyMatch(e -> e instanceof org.domi.furnace.server.entity.Cat), "고양이");
            assertTrue(col.generatedEntities().isEmpty(), "넣은 뒤 목록을 비워 다시 넣지 않는다");
            assertNotNull(col.blockEntityAt(18, -60, 24), "빈 상자도 블록 엔티티");
            var loot = col.chestAt(19, -60, 24);
            assertNotNull(loot);
            assertEquals("minecraft:chests/village/village_plains_house", loot.lootTable);
            long chests = ChunkSnapshot.of(1, 1, col).blockEntities().stream().filter(b -> b.typeId() == ChunkSnapshot.CHEST_TYPE_ID).count();
            assertEquals(2, chests, "상자 둘 다 청크 패킷에");
        }).get();
        io.shutdown();
        sched.close();
    }
}

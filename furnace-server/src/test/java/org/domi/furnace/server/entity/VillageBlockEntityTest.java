// 사용자 테스트 D1·D2·D3: 침대·종 블록 엔티티(클라이언트가 그리려면 청크 패킷에 있어야 한다), 종소리 기억·습격자 공명.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BedBlockEntity;
import org.domi.furnace.server.block.entity.BellBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.types.BellBlock;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class VillageBlockEntityTest {

    private static boolean snapshotHas(ChunkColumn col, int lx, int y, int lz, int typeId) {
        for (var be : ChunkSnapshot.of(0, 0, col).blockEntities()) {
            if (be.localX() == lx && be.y() == y && be.localZ() == lz && be.typeId() == typeId) return true;
        }
        return false;
    }

    @Test
    void bedAndBellGetBlockEntitiesSentInChunkPacket() {
        var level = new VillagerBrainTest.Clocked();
        Pos bell = new Pos(18, -60, 19), bed = new Pos(20, -60, 19);
        assertTrue(level.setBlock(bell, B.defaultState("bell"), 3));
        assertTrue(level.setBlock(bed, B.defaultState("red_bed"), 3));
        assertInstanceOf(BellBlockEntity.class, level.getBlockEntity(bell));
        assertInstanceOf(BedBlockEntity.class, level.getBlockEntity(bed));
        ChunkColumn col = level.column(1, 1);
        assertTrue(snapshotHas(col, 2, -60, 3, BlockEntity.typeId("minecraft:bell")), "종 블록 엔티티(30)가 청크 패킷에");
        assertTrue(snapshotHas(col, 4, -60, 3, BlockEntity.typeId("minecraft:bed")), "침대 블록 엔티티(24)가 청크 패킷에");
    }

    @Test
    void worldgenBlockEntitiesArePromotedOnLoad() {
        var gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);
        ChunkColumn col = gen.generate(1, 2);
        // 템플릿이 남긴 NBT 가 있는 종, NBT 없이 놓인 침대(바닐라 WorldGenRegion 의 DUMMY)
        col.setBlock(3, -59, 4, B.defaultState("bell"));
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:bell");
        nbt.putInt("x", 19);
        nbt.putInt("y", -59);
        nbt.putInt("z", 36);
        col.putGeneratedBlockEntity(3, -59, 4, nbt);
        col.setBlock(5, -59, 4, B.defaultState("white_bed"));
        col.promotePendingBlockEntities(1, 2, true);
        assertInstanceOf(BellBlockEntity.class, col.blockEntityAt(19, -59, 36));
        assertEquals(new Pos(19, -59, 36), col.blockEntityAt(19, -59, 36).pos());
        assertInstanceOf(BedBlockEntity.class, col.blockEntityAt(21, -59, 36));
        assertNull(col.generatedBlockEntity(3, -59, 4), "NBT 는 블록 엔티티로 넘어갔다");
    }

    @Test
    void bellRingSetsHeardTimeAndMakesRaidersGlow() {
        var level = new VillagerBrainTest.Clocked();
        level.game = 100;
        Pos p = new Pos(2, -60, 2);
        level.setBlock(p, B.defaultState("bell"), 3);
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(5.5, -60, 5.5, 0, 0);
        level.addFreshEntity(v);
        var raider = new Pillager(level, level.nextEntityId());
        raider.moveTo(12.5, -60, 2.5, 0, 0);
        level.addFreshEntity(raider);

        assertTrue(((BellBlock) B.behavior(level.getBlockState(p))).attemptToRing(null, level, p, -1));
        level.runBlockEvents();
        assertEquals(100L, v.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).orElseThrow());
        var bell = (BellBlockEntity) level.getBlockEntity(p);
        assertTrue(bell.shaking);
        // 흔들림 5틱째 공명 시작(그 틱에 1), 40 이 된 다음 틱(45틱째)에 발광(60틱)
        for (int i = 0; i < 44; i++) {
            level.game++;
            level.tickBlockEntities(k -> true);
            assertFalse(raider.hasEffect(MobEffect.GLOWING), "틱 " + i + " 에는 아직");
        }
        level.game++;
        level.tickBlockEntities(k -> true);
        assertTrue(raider.hasEffect(MobEffect.GLOWING));
        assertFalse(v.hasEffect(MobEffect.GLOWING), "주민은 습격자가 아니다");
    }
}

// 전달체 블록 엔티티 시험: 물·틀 조건으로 활성화, 전달체의 힘 범위, 42개 틀에서 물속 적대 몹 공격과 Target 저장.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntities;
import org.domi.furnace.server.block.entity.ConduitBlockEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class ConduitBlockEntityTest {

    static final Pos C = new Pos(8, -50, 8);

    /** 전달체 둘레 7×7×7 을 물로 채우고 가운데에 전달체. */
    static VillagerBrainTest.Clocked pool() {
        var l = new VillagerBrainTest.Clocked();
        int water = B.defaultState("water");
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
            l.setBlock(new Pos(C.x() + x, C.y() + y, C.z() + z), water, 2);
        }
        l.setBlock(C, B.defaultState("conduit"), 3);
        return l;
    }

    /** 바닐라 updateShape 의 틀 자리(세 축 평면의 바깥 고리 칸)를 n 개까지 채운다. */
    static int frame(VillagerBrainTest.Clocked l, int n) {
        int placed = 0;
        int prismarine = B.defaultState("prismarine");
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) for (int k = -2; k <= 2; k++) {
            int a = Math.abs(i), b = Math.abs(j), c = Math.abs(k);
            if ((a > 1 || b > 1 || c > 1) && (i == 0 && (b == 2 || c == 2) || j == 0 && (a == 2 || c == 2) || k == 0 && (a == 2 || b == 2))) {
                if (placed >= n) return placed;
                l.setBlock(new Pos(C.x() + i, C.y() + j, C.z() + k), prismarine, 2);
                placed++;
            }
        }
        return placed;
    }

    static void tickTo(VillagerBrainTest.Clocked l, long t) {
        while (l.game < t) {
            l.game++;
            l.tickBlockEntities(k -> true);
        }
    }

    @Test
    void placingConduitCreatesBlockEntityAndFactoryKnowsIt() {
        var l = pool();
        assertInstanceOf(ConduitBlockEntity.class, l.getBlockEntity(C));
        assertTrue(BlockEntities.known("minecraft:conduit"));
    }

    @Test
    void activatesWithSixteenFrameBlocksOnlyWhenSurroundedByWater() {
        var l = pool();
        assertEquals(42, frame(l, 42) , "틀 자리는 42칸");
        // 15개만이면 꺼짐
        var l15 = pool();
        frame(l15, 15);
        tickTo(l15, 40);
        assertFalse(((ConduitBlockEntity) l15.getBlockEntity(C)).isActive());
        var l16 = pool();
        frame(l16, 16);
        tickTo(l16, 40);
        var be = (ConduitBlockEntity) l16.getBlockEntity(C);
        assertTrue(be.isActive());
        assertFalse(be.isHunting());
        // 둘레 물 한 칸을 돌로 막으면 다음 40틱 검사에서 꺼진다
        l16.setBlock(new Pos(C.x() + 1, C.y(), C.z()), B.defaultState("stone"), 2);
        tickTo(l16, 79);
        assertTrue(be.isActive(), "검사는 40틱마다");
        tickTo(l16, 80);
        assertFalse(be.isActive());
    }

    @Test
    void huntsWetEnemyWithMagicDamageAndSavesTarget() {
        var l = pool();
        frame(l, 42);
        var d = new Drowned(l, l.nextEntityId());
        d.moveTo(C.x() + 0.5, C.y() - 2, C.z() + 3.5, 0, 0);
        l.addFreshEntity(d);
        d.baseTick();   // 물에 닿음 갱신
        assertTrue(d.isInWaterOrRain());
        float hp = d.health();
        tickTo(l, 40);
        var be = (ConduitBlockEntity) l.getBlockEntity(C);
        assertTrue(be.isHunting());
        assertSame(d, be.destroyTarget());
        assertEquals(hp - 4.0F, d.health(), 1e-4, "마법 피해 4");
        NbtCompound tag = be.updateTag();
        assertTrue(tag.tags().get("Target") instanceof int[] a && a.length == 4, "대상 UUID 가 갱신 태그에");

        // 저장 → 다시 읽으면 UUID 로 같은 몹을 다시 찾는다
        var loaded = (ConduitBlockEntity) BlockEntities.loadStatic(C, l.getBlockState(C), be.saveWithFullMetadata());
        l.setBlockEntity(loaded);
        tickTo(l, 80);
        assertSame(d, loaded.destroyTarget());
    }

    @Test
    void dryEnemyIsIgnored() {
        var l = pool();
        frame(l, 42);
        var z = new Zombie(l, l.nextEntityId());
        z.moveTo(C.x() + 0.5, C.y() - 2, C.z() + 3.5, 0, 0);   // 물속에 있지만 baseTick 전이라 아직 젖지 않음
        l.addFreshEntity(z);
        tickTo(l, 40);
        assertNull(((ConduitBlockEntity) l.getBlockEntity(C)).destroyTarget());
    }
}

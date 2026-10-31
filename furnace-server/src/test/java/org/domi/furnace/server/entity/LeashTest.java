// 가죽끈(Leashable): 거리별 동작(6칸 초과 당김, 10칸 초과 끊김), NBT 저장/복원, set_entity_link 직렬화.
// 기대값은 바닐라 1.21.1 디컴파일(btk.b_ 와 btk.a 정적 메서드)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityLink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LeashTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 매듭은 (kx+0.5, -59.625, 0.5) 에 놓인다(칸 위 0.375). 소는 같은 높이 x=0.5. */
    private static LeashFenceKnotEntity knot(BlockPlacementTest.TestLevel level, int kx) {
        var k = new LeashFenceKnotEntity(level, level.nextEntityId(), new Pos(kx, -60, 0));
        level.addFreshEntity(k);
        return k;
    }

    private static Cow cow(BlockPlacementTest.TestLevel level) {
        Cow c = new Cow(level, level.nextEntityId());
        c.moveTo(0.5, -59.625, 0.5, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    @Test
    void elasticPullBeyondSixBlocks() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 8);          // 거리 정확히 8.0
        Cow c = cow(level);
        c.setLeashedTo(k, false);
        c.baseTick();
        // dx = (8.5-0.5)/8 = 1.0 → copySign(1*1*0.4, 1) = 0.4, dy=dz=0
        assertEquals(0.4, c.vx(), 1e-12);
        assertTrue(c.isLeashed());
    }

    @Test
    void closeRangeDoesNotPull() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 5);          // 거리 5.0 (6 이하)
        Cow c = cow(level);
        c.setLeashedTo(k, false);
        c.baseTick();
        assertEquals(0.0, c.vx(), 1e-12);
        assertTrue(c.isLeashed());
        // PathfinderMob.checkLeashDistance: 붙잡은 쪽 칸 둘레 5칸으로 제한
        assertTrue(c.hasRestriction());
        assertEquals(5.0F, c.getRestrictRadius());
    }

    @Test
    void breaksBeyondTenBlocksAndDropsLead() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 11);         // 거리 11.0 (> 10)
        Cow c = cow(level);
        c.setLeashedTo(k, false);
        c.baseTick();
        assertFalse(c.isLeashed());
        assertFalse(c.mayBeLeashed());
        assertFalse(c.hasRestriction());   // 끈 정보가 없어지면 제한 구역도 푼다
        var items = level.getEntitiesOfClass(ItemEntity.class, c.boundingBox().inflate(2, 2, 2), e -> true);
        assertEquals(1, items.size());
    }

    @Test
    void holderRemovedDropsLead() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 3);
        Cow c = cow(level);
        c.setLeashedTo(k, false);
        k.discard();
        c.baseTick();
        assertFalse(c.isLeashed());
    }

    @Test
    void enemyCannotBeLeashed() {
        var level = new BlockPlacementTest.TestLevel();
        assertTrue(cow(level).canBeLeashed());
        assertFalse(new Zombie(level, level.nextEntityId()).canBeLeashed());
    }

    @Test
    void saveKnotAsBlockPosAndEntityAsUuid() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 4);
        Cow c = cow(level);
        c.setLeashedTo(k, false);
        NbtCompound t = c.save();
        assertArrayEquals(new int[]{4, -60, 0}, (int[]) t.tags().get("leash"));
        Cow d = cow(level);
        d.setLeashedTo(c, false);   // 소를 붙잡은 쪽으로(UUID 저장 경로)
        NbtCompound u = (NbtCompound) d.save().tags().get("leash");
        assertArrayEquals(Entity.uuidToInts(c.uuid()), (int[]) u.tags().get("UUID"));
    }

    @Test
    void restoreFromUuidAndKnotPos() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 4);
        Cow a = cow(level);
        NbtCompound tag = a.save();
        NbtCompound leash = new NbtCompound();
        leash.putIntArray("UUID", Entity.uuidToInts(k.uuid()));
        tag.putCompound("leash", leash);
        Cow b = (Cow) Entities.load(level, tag);
        b.setUuid(java.util.UUID.randomUUID());
        assertTrue(b.mayBeLeashed());
        assertFalse(b.isLeashed());     // 아직 못 이었다
        level.addFreshEntity(b);
        b.baseTick();
        assertSame(k, b.getLeashHolder());

        // 울타리 좌표로 저장된 끈: 그 칸의 매듭을 찾거나 만든다
        NbtCompound t2 = a.save();
        t2.putIntArray("leash", new int[]{4, -60, 0});
        Cow e = (Cow) Entities.load(level, t2);
        e.setUuid(java.util.UUID.randomUUID());
        level.addFreshEntity(e);
        e.baseTick();
        assertSame(k, e.getLeashHolder());
    }

    @Test
    void pendingLeashSurvivesRoundTripAndDropsAfter100Ticks() {
        var level = new BlockPlacementTest.TestLevel();
        Cow a = cow(level);
        NbtCompound tag = a.save();
        NbtCompound leash = new NbtCompound();
        leash.putIntArray("UUID", new int[]{5, 6, 7, 8});
        tag.putCompound("leash", leash);
        Cow b = (Cow) Entities.load(level, tag);
        assertArrayEquals(new int[]{5, 6, 7, 8}, (int[]) ((NbtCompound) b.save().tags().get("leash")).tags().get("UUID"));
        b.tickCount = 101;
        b.baseTick();   // 붙잡은 쪽이 없고 tickCount > 100: 끈 아이템을 떨구고 끈 정보를 지운다
        assertFalse(b.mayBeLeashed());
    }

    @Test
    void setEntityLinkWritesTwoInts() {
        var buf = Unpooled.buffer();
        new SetEntityLink(5, 0).write(buf);
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        assertArrayEquals(new byte[]{0, 0, 0, 5, 0, 0, 0, 0}, b);
        assertEquals(0x59, SetEntityLink.ID);
    }
}

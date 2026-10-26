// 보트: 물 위 부력(IN_WATER), 땅 마찰(얼음·풀), 플레이어 조종이면 서버가 움직이지 않음, 두 자리 위치, 피해 누적과 부서짐,
// 몹 태우기, 낙하로 부서짐(판자 3·막대 2), 저장 왕복(종류·상자 내용).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BoatTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void fill(BlockPlacementTest.TestLevel level, int y, String block) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlock(new Pos(x, y, z), B.defaultState(block), 3);
    }

    private static void tick(Entity e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    private static int count(BlockPlacementTest.TestLevel level, String item) {
        int n = 0;
        for (Entity e : level.entities().all()) {
            if (e instanceof ItemEntity ie && !ie.isRemoved() && org.domi.furnace.server.item.ItemTools.itemName(ie.getItem()).equals(item)) n += ie.getItem().count();
        }
        return n;
    }

    @Test
    void floatsWithVanillaBuoyancy() {
        var level = new BlockPlacementTest.TestLevel();
        fill(level, -60, "minecraft:water");
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -59.5, 0.5, 0, 0);
        level.addFreshEntity(b);
        double y0 = b.y();
        tick(b);
        assertEquals(Boat.Status.IN_WATER, b.status());
        double waterTop = -60 + 0.8888889F;   // 원천 물 높이 8/9
        double buoyancy = (waterTop - y0) / 0.5625F;
        double vy = ((0.0 - 0.04) + buoyancy * (0.04 / 0.65)) * 0.75;
        assertEquals(vy, b.vy(), 1e-6, "IN_WATER: 중력 뒤 부력 × 0.75");
        assertEquals(y0 + vy, b.y(), 1e-6);
        for (int i = 0; i < 200; i++) tick(b);
        assertEquals(Boat.Status.IN_WATER, b.status());
        assertTrue(Math.abs(b.vy()) < 0.01, "물 위에 뜬다: " + b.vy());
        assertTrue(b.boundingBox().minY() < waterTop && b.boundingBox().maxY() > waterTop, "수면에 걸친다");
    }

    @Test
    void landFrictionDependsOnBlock() {
        for (String ground : new String[] {"minecraft:ice", "minecraft:stone"}) {
            var level = new BlockPlacementTest.TestLevel();
            fill(level, -61, ground);
            Boat b = new Boat(level, level.nextEntityId());
            b.moveTo(0.5, -60, 0.5, 0, 0);
            level.addFreshEntity(b);
            b.setDeltaMovement(0.5, 0.0, 0.0);
            tick(b);
            assertEquals(Boat.Status.ON_LAND, b.status());
            float f = ground.endsWith("ice") ? 0.98F : 0.6F;
            assertEquals(0.5 * f, b.vx(), 1e-6, ground + " 미끄러움");
        }
    }

    @Test
    void playerControlledBoatDoesNotMoveOnServer() {
        var level = new BlockPlacementTest.TestLevel();
        fill(level, -60, "minecraft:water");
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -59.5, 0.5, 0, 0);
        level.addFreshEntity(b);
        Player p = new Player(960, UUID.randomUUID(), "rower", new EmbeddedChannel());
        p.teleporter(t -> {});
        assertTrue(b.interact(p, 0));
        assertSame(p, b.getControllingPassenger());
        b.setDeltaMovement(0.3, 0.1, 0.3);
        double x = b.x(), y = b.y();
        tick(b);
        assertEquals(0.0, b.vx());
        assertEquals(x, b.x(), 1e-9);
        assertEquals(y, b.y(), 1e-9, "클라이언트가 move_vehicle 로 옮긴다");
        b.setPaddleState(true, false);
        assertTrue(b.getPaddleState(0));
        assertFalse(b.getPaddleState(1));
        p.setPlayerInput(0, 0, false, true);
        p.rideTick();
        assertNull(p.vehicle(), "웅크리면 내린다");
        assertFalse(b.getPaddleState(0), "조종자가 없으면 노 상태는 꺼진 것으로 본다");
    }

    @Test
    void twoSeatPositionsAndMobBoarding() {
        var level = new BlockPlacementTest.TestLevel();
        fill(level, -61, "minecraft:stone");
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(b);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 1.2, 0, 0);
        level.addFreshEntity(z);
        tick(b);
        assertSame(b, z.vehicle(), "곁의 몹을 태운다");
        Zombie z2 = new Zombie(level, level.nextEntityId());
        z2.moveTo(0.5, -60, -0.2, 0, 0);
        assertTrue(z2.startRiding(b));
        b.positionRider(z);
        b.positionRider(z2);
        double seat = -60 + 0.5625F / 3.0F - 0.7;   // 몸 높이 1/3 − 좀비 탈것점 0.7
        assertEquals(0.5 + 0.2, z.z(), 1e-6, "앞자리");
        assertEquals(0.5 - 0.6, z2.z(), 1e-6, "뒷자리");
        assertEquals(seat, z.y(), 1e-6);
        Zombie z3 = new Zombie(level, level.nextEntityId());
        assertFalse(z3.startRiding(b), "두 자리뿐");
    }

    @Test
    void damageAccumulatesAndBreaks() {
        var level = new BlockPlacementTest.TestLevel();
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -60, 0.5, 0, 0);
        b.setVariant(Boat.Type.BIRCH);
        level.addFreshEntity(b);
        var dmg = new Entity.Damage("player_attack", false, false, false);
        for (int i = 0; i < 4; i++) assertTrue(b.hurtBy(dmg, 1.0F, null));
        assertFalse(b.isRemoved(), "피해 40 은 아직");
        assertEquals(1, b.getHurtDir(), "맞을 때마다 방향이 뒤집힌다(1 → -1 → … 네 번)");
        b.hurtBy(dmg, 1.0F, null);
        assertTrue(b.isRemoved());
        assertEquals(1, count(level, "minecraft:birch_boat"));
        // 크리에이티브는 한 번에 지우고 드롭하지 않는다
        Boat c = new Boat(level, level.nextEntityId());
        c.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(c);
        Player p = new Player(961, UUID.randomUUID(), "builder", new EmbeddedChannel());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.CREATIVE, false);
        c.hurtBy(dmg, 1.0F, p);
        assertTrue(c.isRemoved());
        assertEquals(0, count(level, "minecraft:oak_boat"));
    }

    @Test
    void fallOnLandBreaksIntoPlanks() {
        var level = new BlockPlacementTest.TestLevel();
        fill(level, -61, "minecraft:stone");
        Boat b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(b);
        tick(b);
        assertEquals(Boat.Status.ON_LAND, b.status());
        b.fallDistance = 5.0F;
        b.checkFallDamage(-0.5, true, level.getBlockState(new Pos(0, -61, 0)), new Pos(0, -61, 0));
        assertTrue(b.isRemoved());
        assertEquals(3, count(level, "minecraft:oak_planks"));
        assertEquals(2, count(level, "minecraft:stick"));
    }

    @Test
    void saveRoundTripKeepsTypeAndChest() {
        var level = new BlockPlacementTest.TestLevel();
        ChestBoat cb = new ChestBoat(level, level.nextEntityId());
        cb.moveTo(0.5, -60, 0.5, 0, 0);
        cb.setVariant(Boat.Type.BAMBOO);
        cb.setItem(3, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "diamond"), 5));
        var tag = cb.save();
        assertEquals("minecraft:chest_boat", tag.getString("id"));
        assertEquals("bamboo", tag.getString("Type"));
        Entity loaded = Entities.load(level, tag);
        assertInstanceOf(ChestBoat.class, loaded);
        ChestBoat l = (ChestBoat) loaded;
        assertEquals(Boat.Type.BAMBOO, l.getVariant());
        assertEquals(5, l.getItem(3).count());
        assertEquals("minecraft:bamboo_chest_raft", l.dropItem());
        // 부서지면 내용물도 쏟는다
        level.addFreshEntity(l);
        for (int i = 0; i < 5; i++) l.hurtBy(new Entity.Damage("player_attack", false, false, false), 1.0F, null);
        assertTrue(l.isRemoved());
        assertEquals(5, count(level, "minecraft:diamond"));
        assertEquals(1, count(level, "minecraft:bamboo_chest_raft"));
    }
}

// 광산 수레: 평지 감속, 경사·곡선·전동 레일 한 틱 값(바닐라 moveAlongTrack 식을 손으로 풀어 대조), 탄 플레이어 입력, 몹 태우기,
// 활성화 레일, 감지 레일, 피해·부서짐, 저장 왕복.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinecartTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final double Y = -60;

    private static BlockPlacementTest.TestLevel floor() {
        var level = new BlockPlacementTest.TestLevel();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:stone"), 3);
        return level;
    }

    private static int rail(BlockPlacementTest.TestLevel level, Pos p, String block, String shape) {
        int s = B.prop("shape").with(B.defaultState(block), shape);
        level.setBlock(p, s, 2);
        s = B.prop("shape").with(level.getBlockState(p), shape);   // 놓일 때 모양이 바뀌었으면 되돌린다
        level.setBlock(p, s, 2);
        return s;
    }

    private static void tick(Entity e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    private static Minecart cart(BlockPlacementTest.TestLevel level, double x, double y, double z) {
        Minecart m = (Minecart) AbstractMinecart.create(level, x, y, z, AbstractMinecart.Type.RIDEABLE);
        level.addFreshEntity(m);
        return m;
    }

    private static int count(BlockPlacementTest.TestLevel level, String item) {
        int n = 0;
        for (Entity e : level.entities().all()) {
            if (e instanceof ItemEntity ie && !ie.isRemoved() && org.domi.furnace.server.item.ItemTools.itemName(ie.getItem()).equals(item)) n += ie.getItem().count();
        }
        return n;
    }

    @Test
    void flatRailSlowsByVanillaFactor() {
        var level = floor();
        for (int x = -6; x <= 6; x++) rail(level, new Pos(x, -60, 0), "minecraft:rail", "east_west");
        Minecart m = cart(level, -4.5, Y + 0.0625, 0.5);
        m.setDeltaMovement(0.3, 0.0, 0.0);
        // 바닐라: 레일 높이(+0.0625)에 붙고, 한 틱에 x += v(최대 0.4), 그 뒤 v × 0.96(빈 수레)
        double x = -4.5, v = 0.3;
        for (int i = 0; i < 20; i++) {
            tick(m);
            x += Math.min(v, 0.4);
            v *= 0.96;
            assertEquals(x, m.x(), 1e-9, "틱 " + i);
            assertEquals(v, m.vx(), 1e-9, "틱 " + i);
            assertEquals(Y + 0.0625, m.y(), 1e-9);
            assertEquals(0.5, m.z(), 1e-12);
            assertTrue(m.isOnRails());
        }
    }

    @Test
    void ascendingRailOneTick() {
        var level = floor();
        rail(level, new Pos(0, -60, 0), "minecraft:rail", "ascending_east");
        Minecart m = cart(level, 0.5, Y + 0.0625 + 0.5, 0.5);   // MinecartItem: 경사면 +0.5
        tick(m);
        // 손으로 푼 moveAlongTrack: 경사 가속 −1/128 → 출구 방향 (−1, 0) 로 맞춤 → 레일 선(x 0.5)·한 칸 위(y −59)에 놓고 move →
        // 0.96 감속 → 높이 보정 ((앞 y − 뒤 y) × 0.05 를 속도에 더함) → 레일 높이로.
        double v0 = -0.0078125;
        double x = 0.5 + v0;
        double before = -59.4375, after = -60 + 0.0625 - 0.5 + x + 0.5;
        double v = v0 * 0.96;
        double hd = Math.abs(v);
        v = v * ((hd + (before - after) * 0.05) / hd);
        assertEquals(x, m.x(), 1e-12);
        assertEquals(after, m.y(), 1e-12);
        assertEquals(-59.4453125, m.y(), 1e-12);
        assertEquals(-0.007890625, m.vx(), 1e-12);
        assertEquals(v, m.vx(), 1e-12);
        assertEquals(0.0, m.vy(), 1e-12, "레일 위 세로 속도는 감속에서 0");
    }

    @Test
    void curvedRailTurnsAlongDiagonal() {
        var level = floor();
        rail(level, new Pos(0, -60, 0), "minecraft:rail", "south_east");
        Minecart m = cart(level, 0.5, Y + 0.0625, 0.5);
        m.setDeltaMovement(-0.1, 0.0, 0.0);
        tick(m);
        // 남·동 출구의 대각선(0.5, 1.0)→(1.0, 0.5) 위 (0.75, 0.75) 에 놓고, 속도 0.1 을 (−1, 1)/√2 로 돌려 움직인 뒤 0.96
        double s = 0.1 / Math.sqrt(2.0);
        assertEquals(0.75 - s, m.x(), 1e-12);
        assertEquals(0.75 + s, m.z(), 1e-12);
        assertEquals(-s * 0.96, m.vx(), 1e-12);
        assertEquals(s * 0.96, m.vz(), 1e-12);
        assertEquals(Y + 0.0625, m.y(), 1e-12);
    }

    @Test
    void poweredRailAcceleratesBrakesAndKicksOff() {
        var level = floor();
        level.setBlock(new Pos(0, -61, 0), B.defaultState("minecraft:redstone_block"), 3);
        int on = rail(level, new Pos(0, -60, 0), "minecraft:powered_rail", "east_west");
        level.setBlock(new Pos(0, -60, 0), B.prop("powered").with(on, true), 2);
        Minecart m = cart(level, 0.5, Y + 0.0625, 0.5);
        m.setDeltaMovement(0.1, 0.0, 0.0);
        tick(m);
        assertEquals(0.6, m.x(), 1e-12);
        assertEquals(0.1 * 0.96 + 0.06, m.vx(), 1e-12, "켜진 전동 레일: 단위 방향 × 0.06");
        // 꺼진 전동 레일은 절반으로 제동
        var level2 = floor();
        rail(level2, new Pos(0, -60, 0), "minecraft:powered_rail", "east_west");
        Minecart b = cart(level2, 0.5, Y + 0.0625, 0.5);
        b.setDeltaMovement(0.1, 0.0, 0.0);
        tick(b);
        assertEquals(0.55, b.x(), 1e-12);
        assertEquals(0.05 * 0.96, b.vx(), 1e-12);
        b.setDeltaMovement(0.02, 0.0, 0.0);
        b.setPos(0.5, Y + 0.0625, 0.5);
        tick(b);
        assertEquals(0.0, b.vx(), 1e-12, "0.03 보다 느리면 멈춘다");
        // 멈춘 수레가 켜진 전동 레일 위에 있고 서쪽이 단단하면 동쪽으로 0.02
        var level3 = floor();
        level3.setBlock(new Pos(0, -61, 0), B.defaultState("minecraft:redstone_block"), 3);
        level3.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:stone"), 3);
        int on3 = rail(level3, new Pos(0, -60, 0), "minecraft:powered_rail", "east_west");
        level3.setBlock(new Pos(0, -60, 0), B.prop("powered").with(on3, true), 2);
        Minecart k = cart(level3, 0.5, Y + 0.0625, 0.5);
        tick(k);
        assertEquals(0.02, k.vx(), 1e-12);
    }

    @Test
    void riderInputPushesStoppedCart() {
        var level = floor();
        for (int z = -6; z <= 6; z++) rail(level, new Pos(0, -60, z), "minecraft:rail", "north_south");
        Minecart m = cart(level, 0.5, Y + 0.0625, 0.5);
        Player p = new Player(970, UUID.randomUUID(), "rider", new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        assertTrue(m.interact(p, 0));
        assertSame(m, p.vehicle());
        p.setPlayerInput(0.0F, 1.0F, false, false);
        tick(m);
        // 가정: 서버 플레이어 속도 = 앞 입력 0.98 × 공중 속도 0.02 × 0.91 (yaw 0 → +z). 수레가 거의 멈췄으면 그 0.1 배를 더한다.
        double pz = 1.0F * 0.98F * 0.02F * 0.91F;
        double v = pz * 0.1;
        assertEquals(0.5 + v * 0.75, m.z(), 1e-9, "탄 수레는 0.75 배로 움직인다");
        assertEquals(v * 0.997, m.vz(), 1e-9, "탄 수레 감속 0.997");
        assertFalse(m.interact(new Player(971, UUID.randomUUID(), "other", new ConcurrentEmbeddedChannel()), 0), "이미 누가 탔다");
    }

    @Test
    void movingCartPicksUpMobsAndActivatorEjects() {
        var level = floor();
        for (int x = -6; x <= 6; x++) rail(level, new Pos(x, -60, 0), "minecraft:rail", "east_west");
        Minecart m = cart(level, -2.5, Y + 0.0625, 0.5);   // 오른쪽 면 -2.01, 한 틱에 0.2 → -1.81
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(-1.4, -60, 0.5, 0, 0);                     // 왼쪽 면 -1.7: 부딪히지 않고 0.2 넓힌 줍기 상자 안
        level.addFreshEntity(z);
        m.setDeltaMovement(0.2, 0.0, 0.0);
        tick(m);
        assertSame(m, z.vehicle(), "가로 속도² > 0.01 이면 닿은 몹을 태운다");
        // 수레는 밀 수 있는 몹과 부딪힌다(canVehicleCollide): 이번 이동 안에 몸이 닿으면 move 가 가로 속도를 0 으로 만들어 그 틱에는 못 태운다
        var levelB = floor();
        for (int x = -6; x <= 6; x++) rail(levelB, new Pos(x, -60, 0), "minecraft:rail", "east_west");
        Minecart mb = cart(levelB, -2.5, Y + 0.0625, 0.5);
        Zombie zb = new Zombie(levelB, levelB.nextEntityId());
        zb.moveTo(-1.6, -60, 0.5, 0, 0);                    // 왼쪽 면 -1.9 < -1.81
        levelB.addFreshEntity(zb);
        mb.setDeltaMovement(0.2, 0.0, 0.0);
        tick(mb);
        assertEquals(-1.9 - 0.49, mb.x(), 1.0E-6, "좀비 왼쪽 면에서 멈춘다");
        assertNull(zb.vehicle());
        // 켜진 활성화 레일: 승객을 내리고 흔들린다
        var level2 = floor();
        level2.setBlock(new Pos(0, -61, 0), B.defaultState("minecraft:redstone_block"), 3);
        int s = rail(level2, new Pos(0, -60, 0), "minecraft:activator_rail", "east_west");
        level2.setBlock(new Pos(0, -60, 0), B.prop("powered").with(s, true), 2);
        Minecart a = cart(level2, 0.5, Y + 0.0625, 0.5);
        Zombie z2 = new Zombie(level2, level2.nextEntityId());
        z2.moveTo(0.5, -60, 0.5, 0, 0);
        level2.addFreshEntity(z2);
        assertTrue(z2.startRiding(a));
        tick(a);
        assertNull(z2.vehicle());
        assertEquals(10, a.getHurtTime());
        assertEquals(50.0F, a.getDamage());
        assertEquals(-1, a.getHurtDir());
    }

    @Test
    void detectorRailSeesCart() {
        var level = floor();
        rail(level, new Pos(0, -60, 0), "minecraft:detector_rail", "east_west");
        Minecart m = cart(level, 0.5, Y + 0.0625, 0.5);
        tick(m);
        int s = level.getBlockState(new Pos(0, -60, 0));
        assertTrue(B.prop("powered").is(s), "수레가 칸 안에 있으면 켜진다");
        // 상자 수레: 비교기 출력 = 찬 정도
        var level2 = floor();
        rail(level2, new Pos(0, -60, 0), "minecraft:detector_rail", "east_west");
        MinecartChest c = (MinecartChest) AbstractMinecart.create(level2, 0.5, Y + 0.0625, 0.5, AbstractMinecart.Type.CHEST);
        level2.addFreshEntity(c);
        c.setItem(0, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "stone"), 64));
        tick(c);
        int s2 = level2.getBlockState(new Pos(0, -60, 0));
        assertEquals(1, B.behavior(s2).getAnalogOutputSignal(s2, level2, new Pos(0, -60, 0)), "27 칸 중 한 칸 가득 → 1");
    }

    @Test
    void damageBreaksAndDrops() {
        var level = floor();
        Minecart m = cart(level, 0.5, Y, 0.5);
        var dmg = new Entity.Damage("player_attack", false, false, false);
        for (int i = 0; i < 4; i++) m.hurtBy(dmg, 1.0F, null);
        assertFalse(m.isRemoved(), "피해 40 은 아직");
        m.hurtBy(dmg, 1.0F, null);
        assertTrue(m.isRemoved());
        assertEquals(1, count(level, "minecraft:minecart"));
        MinecartChest c = (MinecartChest) AbstractMinecart.create(level, 2.5, Y, 0.5, AbstractMinecart.Type.CHEST);
        level.addFreshEntity(c);
        c.setItem(5, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "diamond"), 3));
        c.hurtBy(dmg, 5.0F, null);
        assertTrue(c.isRemoved());
        assertEquals(3, count(level, "minecraft:diamond"));
        assertEquals(1, count(level, "minecraft:chest_minecart"));
        // TNT 수레: 느리게 맞으면 아이템, 불이면 도화선
        MinecartTNT t = (MinecartTNT) AbstractMinecart.create(level, 4.5, Y, 0.5, AbstractMinecart.Type.TNT);
        level.addFreshEntity(t);
        t.hurt(Entity.Damage.IN_FIRE, 1.0F);
        assertFalse(t.isRemoved());
        assertTrue(t.isPrimed());
        assertTrue(t.getFuse() >= 0 && t.getFuse() < 39, "도화선 난수 0~38: " + t.getFuse());
    }

    @Test
    void tntPrimedByActivatorRail() {
        var level = floor();
        level.setBlock(new Pos(0, -61, 0), B.defaultState("minecraft:redstone_block"), 3);
        int s = rail(level, new Pos(0, -60, 0), "minecraft:activator_rail", "east_west");
        level.setBlock(new Pos(0, -60, 0), B.prop("powered").with(s, true), 2);
        MinecartTNT t = (MinecartTNT) AbstractMinecart.create(level, 0.5, Y + 0.0625, 0.5, AbstractMinecart.Type.TNT);
        level.addFreshEntity(t);
        tick(t);
        assertEquals(79, t.getFuse(), "super.tick 안에서 80 으로 붙고 같은 틱에 하나 탄다");
        assertEquals(0.0F, t.blockExplosionResistance(level, new Pos(0, -60, 0), level.getBlockState(new Pos(0, -60, 0)), 0.7F), "탄 동안 레일은 저항 0");
        assertFalse(t.shouldBlockExplode(level, new Pos(0, -61, 0), level.getBlockState(new Pos(0, -61, 0)), 1.0F), "위가 레일인 칸도 지킨다");
    }

    @Test
    void furnaceCartFuelAndPush() {
        var level = floor();
        for (int x = -6; x <= 6; x++) rail(level, new Pos(x, -60, 0), "minecraft:rail", "east_west");
        MinecartFurnace f = (MinecartFurnace) AbstractMinecart.create(level, 0.5, Y + 0.0625, 0.5, AbstractMinecart.Type.FURNACE);
        level.addFreshEntity(f);
        Player p = new Player(972, UUID.randomUUID(), "stoker", new ConcurrentEmbeddedChannel());
        p.teleporter(t -> {});
        p.inventory().set(p.inventory().heldWindowSlot(), new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "coal"), 2));
        f.interact(p, 0);
        assertEquals(3600, f.fuel());
        assertEquals(1, p.inventory().get(p.inventory().heldWindowSlot()).count());
        assertEquals(f.x() - p.x(), f.xPush, 1e-12);
        f.xPush = 1.0;
        f.zPush = 0.0;
        tick(f);
        // 미는 중: (0 × 0.8 + 1) → 기본 감속 0.96
        assertEquals(1.0 * 0.96, f.vx(), 1e-12);
        assertEquals(3599, f.fuel());
        assertTrue((Boolean) f.entityData().get(MinecartFurnace.DATA_ID_FUEL));
    }

    @Test
    void saveRoundTrip() {
        var level = floor();
        MinecartChest c = (MinecartChest) AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.CHEST);
        c.setItem(3, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "diamond"), 5));
        c.setDisplayBlockState(B.defaultState("minecraft:gold_block"));
        c.setDisplayOffset(4);
        var tag = c.save();
        assertEquals("minecraft:chest_minecart", tag.getString("id"));
        var l = (MinecartChest) Entities.load(level, tag);
        assertEquals(5, l.getItem(3).count());
        assertTrue(l.hasCustomDisplay());
        assertEquals(B.defaultState("minecraft:gold_block"), l.getDisplayBlockState());
        assertEquals(4, l.getDisplayOffset());

        MinecartFurnace f = (MinecartFurnace) AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.FURNACE);
        f.setFuel(1234);
        f.xPush = 0.25;
        f.zPush = -0.5;
        var lf = (MinecartFurnace) Entities.load(level, f.save());
        assertEquals(1234, lf.fuel());
        assertEquals(0.25, lf.xPush);
        assertEquals(-0.5, lf.zPush);
        assertFalse(lf.hasCustomDisplay());

        MinecartTNT t = (MinecartTNT) AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.TNT);
        t.primeFuse();
        var lt = (MinecartTNT) Entities.load(level, t.save());
        assertEquals(80, lt.getFuse());

        MinecartHopper h = (MinecartHopper) AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.HOPPER);
        h.setEnabled(false);
        var lh = (MinecartHopper) Entities.load(level, h.save());
        assertFalse(lh.isEnabled());

        var tagS = AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.SPAWNER).save();
        tagS.putShort("Delay", (short) 17);
        var ls = Entities.load(level, tagS);
        assertInstanceOf(MinecartSpawner.class, ls);
        assertEquals((short) 17, ls.save().getShort("Delay"), "스포너 값 보존");

        // 승객과 함께 저장
        Minecart m = (Minecart) AbstractMinecart.create(level, 0.5, Y, 0.5, AbstractMinecart.Type.RIDEABLE);
        level.addFreshEntity(m);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, Y, 0.5, 0, 0);
        level.addFreshEntity(z);
        assertTrue(z.startRiding(m));
        var lm = Entities.load(level, m.save());
        assertInstanceOf(Minecart.class, lm);
        assertEquals(1, lm.passengers().size());
        assertInstanceOf(Zombie.class, lm.passengers().get(0));
    }

    @Test
    void minecartItemPlacesOnRail() {
        var level = floor();
        rail(level, new Pos(0, -60, 0), "minecraft:rail", "ascending_east");
        Minecart m = (Minecart) AbstractMinecart.create(level, 0.5, -60 + 0.0625 + 0.5, 0.5, AbstractMinecart.Type.RIDEABLE);
        assertEquals(-59.4375, m.y(), 1e-12);
        assertEquals(m.x(), m.xo);
        assertEquals(0.98F, m.bbWidth());
        assertEquals(0.7F, m.bbHeight());
        assertEquals(8, EntityType.MINECART.clientTrackingRange());
    }
}

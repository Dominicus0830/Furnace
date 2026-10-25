// 윈드 차지: 블록에 맞으면 피해 없이 문을 열고, 몹을 밀어 올린다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindChargeTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void burstOpensWoodenTrapdoorAndPushesWithoutDamage() {
        var level = new BlockPlacementTest.TestLevel();
        int closed = B.defaultState(B.id("minecraft:oak_trapdoor"));
        Pos pos = new Pos(5, -60, 5);
        level.setBlock(pos, closed, 3);
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(5.5, -60.0, 6.5, 0, 0);
        level.addFreshEntity(cow);
        float hp = cow.health();
        WindCharge charge = new WindCharge(level, level.nextEntityId());
        charge.moveTo(5.5, -59.0, 5.5, 0, 0);
        level.addFreshEntity(charge);
        charge.explodeAt(5.5, -59.5, 5.5);
        assertNotEquals(closed, level.getBlockState(pos), "트랩도어가 열렸다");
        assertEquals(hp, cow.health(), 1.0E-6F, "피해 없음");
        assertTrue(cow.vy() > 0.0 || cow.vx() != 0.0 || cow.vz() != 0.0, "밀렸다");
    }

    @Test
    void dragonFireballLeavesBreathCloudThatHarms() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(5.5, -60.0, 5.5, 0, 0);
        level.addFreshEntity(cow);
        float hp = cow.health();
        DragonFireball ball = new DragonFireball(level, level.nextEntityId());
        ball.moveTo(5.5, -59.5, 5.5, 0, 0);
        level.addFreshEntity(ball);
        ball.onHitForTest();
        assertTrue(ball.isRemoved(), "화염구 소멸");
        for (int t = 0; t < 40; t++) {
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
        assertTrue(cow.health() < hp, "즉시 피해를 받았다");
    }

    @Test
    void newDataEntitiesSerializeTheirMetadata() {
        var level = new BlockPlacementTest.TestLevel();
        for (String id : new String[] {"area_effect_cloud", "dragon_fireball", "wither_skull", "wind_charge", "breeze_wind_charge", "marker", "interaction",
                "block_display", "item_display", "text_display"}) {
            Entity e = Entities.create(level, "minecraft:" + id);
            assertNotNull(e, id);
            if (e instanceof Display d) {
                d.setTransformation(new float[] {1, 2, 3}, new float[] {0, 0, 0, 1}, new float[] {2, 2, 2}, new float[] {0, 0, 0, 1});
                d.setBillboard(3);
            }
            if (e instanceof Display.TextDisplay t) t.setText(org.domi.furnace.api.text.Text.text("hi"));
            if (e instanceof AreaEffectCloud c) c.setRadius(2.0F);
            var items = e.entityData().nonDefaultValues();
            var buf = io.netty.buffer.Unpooled.buffer();
            new org.domi.furnace.server.net.protocol.play.SetEntityData(e.id(), items == null ? java.util.List.of() : items).write(buf);
            assertTrue(buf.readableBytes() >= 1, id);
            buf.release();
        }
    }

    @Test
    void armorStandBreaksOnSecondHitAndDropsEquipment() {
        var level = new BlockPlacementTest.TestLevel();
        ArmorStand stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(5.5, -60.0, 5.5, 0, 0);
        level.addFreshEntity(stand);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 1));
        Entity.Damage hit = new Entity.Damage("player_attack", false, false, false);
        assertFalse(stand.isRemoved());
        stand.hurtBy(hit, 1.0F, null);
        assertFalse(stand.isRemoved(), "첫 타격은 흔들림만");
        stand.hurtBy(hit, 1.0F, null);
        assertTrue(stand.isRemoved(), "5틱 안 두 번째 타격에 부서진다");
        assertTrue(level.entities().tickSnapshot().stream().anyMatch(e -> e instanceof ItemEntity), "장비가 떨어졌다");
    }

    @Test
    void fallingSandLandsOnGroundAndAnvilHurtsMob() {
        var level = new BlockPlacementTest.TestLevel();
        Pos from = new Pos(8, -52, 8);
        level.setBlock(from, B.defaultState("sand"), 3);
        FallingBlockEntity sand = FallingBlockEntity.fall(level, from, B.defaultState("sand"));
        assertTrue(B.isAir(level.getBlockState(from)), "원래 자리는 비었다");
        for (int t = 0; t < 100 && !sand.isRemoved(); t++) sand.tick();
        assertTrue(sand.isRemoved());
        assertTrue(B.is(level.getBlockState(new Pos(8, -60, 8)), "sand"), "땅 위에 내려앉았다");

        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(10.5, -60.0, 10.5, 0, 0);
        level.addFreshEntity(cow);
        float hp = cow.health();
        FallingBlockEntity anvil = FallingBlockEntity.fall(level, new Pos(10, -50, 10), B.defaultState("anvil"));
        anvil.setHurtsEntities(2.0F, 40);
        for (int t = 0; t < 100 && !anvil.isRemoved(); t++) {
            anvil.tick();
            if (anvil.onGround() && !anvil.isRemoved()) break;
        }
        assertTrue(cow.health() < hp, "모루가 소를 다치게 했다");
    }

    @Test
    void lightningIgnitesPigsIntoZombifiedPiglinsAndFlashesOut() {
        var level = new BlockPlacementTest.TestLevel();
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(6.5, -60.0, 6.5, 0, 0);
        level.addFreshEntity(pig);
        Creeper creeper = new Creeper(level, level.nextEntityId());
        creeper.moveTo(7.5, -60.0, 6.5, 0, 0);
        level.addFreshEntity(creeper);
        LightningBolt bolt = new LightningBolt(level, level.nextEntityId());
        bolt.moveTo(6.5, -60.0, 6.5, 0, 0);
        level.addFreshEntity(bolt);
        for (int t = 0; t < 60 && !bolt.isRemoved(); t++) bolt.tick();
        assertTrue(bolt.isRemoved(), "번개는 곧 사라진다");
        assertTrue(creeper.isPowered(), "크리퍼가 충전됐다");
        if (level.difficulty() != 0) {
            assertTrue(level.entities().tickSnapshot().stream().anyMatch(e -> e instanceof ZombifiedPiglin), "돼지가 좀비 피글린이 됐다");
        }
        assertNull(bolt.save(), "번개는 저장되지 않는다");
    }
}

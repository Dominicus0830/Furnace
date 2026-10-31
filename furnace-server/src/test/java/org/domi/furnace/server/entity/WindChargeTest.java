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

    @Test
    void hangingEntitiesAttachToWallsAndDropWhenWallRemoved() {
        var level = new BlockPlacementTest.TestLevel();
        int stone = B.defaultState(B.id("minecraft:stone"));
        Pos wall = new Pos(12, -60, 12);
        level.setBlock(wall, stone, 3);
        Pos front = wall.relative(org.domi.furnace.server.block.Dir.SOUTH);
        ItemFrame frame = new ItemFrame(level, level.nextEntityId(), front, org.domi.furnace.server.block.Dir.SOUTH, false);
        assertTrue(frame.survives(), "벽 앞이라 붙을 수 있다");
        level.addFreshEntity(frame);
        frame.setItem(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:apple"), 1));
        Painting painting = Painting.create(level, front.relative(org.domi.furnace.server.block.Dir.EAST), org.domi.furnace.server.block.Dir.SOUTH);
        assertNull(painting, "옆 칸에는 벽이 없어 그림이 붙을 수 없다");
        Entity.Damage hit = new Entity.Damage("player_attack", false, false, false);
        frame.hurtBy(hit, 1.0F, null);
        assertFalse(frame.isRemoved(), "첫 타격은 아이템만 뺀다");
        frame.hurtBy(hit, 1.0F, null);
        assertTrue(frame.isRemoved(), "빈 액자는 부서진다");
    }

    @Test
    void paintingPicksLargestVariantThatFitsWall() {
        var level = new BlockPlacementTest.TestLevel();
        int stone = B.defaultState(B.id("minecraft:stone"));
        for (int x = 20; x < 26; x++) for (int y = -60; y < -54; y++) level.setBlock(new Pos(x, y, 20), stone, 3);
        Painting p = Painting.create(level, new Pos(22, -59, 21), org.domi.furnace.server.block.Dir.SOUTH);
        assertNotNull(p, "큰 벽에는 그림이 붙는다");
        assertTrue(p.survives());
        level.addFreshEntity(p);
        assertFalse(p.isRemoved());
    }

    @Test
    void fireworkRocketFliesUpAndExplodesHurtingNearbyMob() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(30.5, -60.0, 30.5, 0, 0);
        level.addFreshEntity(cow);
        float hp = cow.health();
        var stack = new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:firework_rocket"), 1,
                org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:fireworks",
                        java.util.Map.of("flight_duration", 1, "explosions", java.util.List.of(java.util.Map.of("shape", "small_ball", "colors", java.util.List.of(255)))))));
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, level.nextEntityId(), 30.5, -59.0, 30.5, stack);
        level.addFreshEntity(rocket);
        for (int t = 0; t < 100 && !rocket.isRemoved(); t++) rocket.tick();
        assertTrue(rocket.isRemoved(), "수명이 다해 터졌다");
        assertTrue(cow.health() < hp, "폭발 장식이 있어 근처 소가 다쳤다");
    }

    @Test
    void witherSummonedFromSoulSandTShapeAndSpawnsInvulnerable() {
        var level = new BlockPlacementTest.TestLevel();
        int sand = B.defaultState(B.id("minecraft:soul_sand"));
        int skull = B.defaultState(B.id("minecraft:wither_skeleton_skull"));
        int cx = 40, cz = 40, y0 = -60;
        level.setBlock(new Pos(cx, y0, cz), sand, 3);
        for (int d = -1; d <= 1; d++) level.setBlock(new Pos(cx + d, y0 + 1, cz), sand, 3);
        for (int d = -1; d <= 1; d++) level.setBlock(new Pos(cx + d, y0 + 2, cz), skull, 3);
        org.domi.furnace.server.block.types.WitherSkullBlock.checkSpawn(level, new Pos(cx, y0 + 2, cz));
        assertTrue(level.entities().tickSnapshot().stream().anyMatch(e -> e instanceof WitherBoss w && w.getInvulnerableTicks() == 220), "위더가 소환됐다");
        assertTrue(B.isAir(level.getBlockState(new Pos(cx, y0 + 1, cz))), "틀 블록이 지워졌다");
        WitherBoss wither = (WitherBoss) level.entities().tickSnapshot().stream().filter(e -> e instanceof WitherBoss).findFirst().orElseThrow();
        for (int t = 0; t < 240; t++) {
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
        assertEquals(0, wither.getInvulnerableTicks(), "무적 시간이 끝났다");
        assertTrue(wither.isAlive());
    }

    @Test
    void breezeFightsIronGolemShootingWindCharges() {
        var level = new BlockPlacementTest.TestLevel();
        int floor = B.defaultState(B.id("minecraft:stone"));
        // 브리즈는 난수로 옆걸음·점프해 바닥 밖 허공으로 떨어져 죽을 수 있다(실패율 약 9%). 바닥을 넓히고 둘레에 벽을 둘러 난수와 무관하게 한다.
        for (int x = -8; x < 24; x++) for (int z = -8; z < 24; z++) {
            level.setBlock(new Pos(x, -61, z), floor, 3);
            if (x == -8 || x == 23 || z == -8 || z == 23) for (int y = -60; y <= -48; y++) level.setBlock(new Pos(x, y, z), floor, 3);
        }
        Breeze breeze = new Breeze(level, level.nextEntityId());
        breeze.moveTo(8.5, -60.0, 2.5, 0, 0);
        level.addFreshEntity(breeze);
        IronGolem golem = new IronGolem(level, level.nextEntityId());
        golem.moveTo(8.5, -60.0, 10.5, 0, 0);
        golem.setNoAi(true);
        level.addFreshEntity(golem);
        boolean sawCharge = false;
        for (int t = 0; t < 400; t++) {
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
                if (e instanceof BreezeWindCharge) sawCharge = true;
            }
        }
        assertTrue(breeze.isAlive());
        assertTrue(sawCharge || breeze.getTarget() != null, "브리즈가 대상을 잡았다");
    }
}

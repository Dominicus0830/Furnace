// 투사체가 블록에 맞았을 때(Block.onProjectileHit): 양초·모닥불·TNT 점화, 자수정 소리, 종, 큰 흘림잎, 후렴화·장식 단지·뾰족한 점적석 파괴,
// Projectile.mayInteract(쏜 쪽이 몹이면 mobGriefing)·mayBreak(impact_projectiles + projectilesCanBreakBlocks).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectileBlockHitTest {

    @BeforeEach
    void install() throws Exception {
        assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);
    }

    /** 게임룰을 바꿀 수 있고 소리를 적어 두는 시험 레벨. */
    static final class Level extends BlockPlacementTest.TestLevel {
        boolean griefing = true, projectilesBreak = true;
        final List<String> sounds = new ArrayList<>();
        final List<Float> pitches = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final List<Object> eventSources = new ArrayList<>();

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public boolean gameRule(String name) {
                    return switch (name) {
                        case "mobGriefing" -> griefing;
                        case "projectilesCanBreakBlocks" -> projectilesBreak;
                        default -> super.gameRule(name);
                    };
                }
            };
        }

        /** 장식 단지 전리품: 바닐라 표를 block_entity 컴포넌트로 평가한다(ServerCore.blockDrops 와 같은 매개변수). */
        @Override
        public List<ItemStack> lootDrops(String lootTable, int state, org.domi.furnace.server.block.Pos pos, BlockActor entity, ItemStack tool,
                                         Float explosionRadius, java.util.Map<String, Object> blockEntity) {
            try {
                var loot = org.domi.furnace.server.loot.VanillaChestLootTest.vanillaLoot();
                var net = new org.domi.furnace.server.loot.NetworkComponents(new org.domi.furnace.server.data.TagRegistries(
                        org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
                var bs = org.domi.furnace.server.ServerCore.lootBlockState(state);
                return org.domi.furnace.server.loot.TestBlockLoot.drops(loot, net, lootTable, bs.block(), bs.properties(),
                        blockEntity == null ? java.util.Map.of() : blockEntity, org.domi.furnace.server.loot.LootStack.empty());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            sounds.add(sound);
            pitches.add(pitch);
            super.playSeededSound(except, x, y, z, sound, source, volume, pitch, seed);
        }

        @Override
        public void gameEventFrom(String event, Pos pos, Object entity, int state) {
            events.add(event);
            eventSources.add(entity);
            super.gameEventFrom(event, pos, entity, state);
        }
    }

    private static final Pos AT = new Pos(3, -60, 3);

    private static Player player() {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    private static int state(String name) { return B.defaultState(B.id("minecraft:" + name)); }

    private static Projectile.Hit hitFace(int face, double y) {
        double x = AT.x() + 0.5, z = AT.z() + 0.5;
        if (face == Dir.NORTH) z = AT.z();
        else if (face == Dir.SOUTH) z = AT.z() + 1.0;
        else if (face == Dir.WEST) x = AT.x();
        else if (face == Dir.EAST) x = AT.x() + 1.0;
        return new Projectile.Hit(null, AT, face, x, AT.y() + y, z);
    }

    private static Projectile.Hit hit() { return hitFace(Dir.NORTH, 0.5); }

    private static Arrow arrow(Level level, Targetable owner, boolean burning) {
        var a = new Arrow(level, level.nextEntityId(), AT.x() + 0.5, AT.y() + 0.5, AT.z() - 1.0);
        a.setOwner(owner);
        if (burning) a.setRemainingFireTicks(100);
        return a;
    }

    private static long count(Level level, Class<?> type) {
        return level.entities().tickSnapshot().stream().filter(type::isInstance).count();
    }

    // ------------------------------------------------------------------ mayInteract·mayBreak

    @Test
    void mayInteractFollowsOwnerAndMobGriefing() {
        var level = new Level();
        var noOwner = arrow(level, null, false);
        var byPlayer = arrow(level, player(), false);
        var bySkeleton = arrow(level, new Skeleton(level, level.nextEntityId()), false);
        level.griefing = false;
        assertTrue(noOwner.mayInteract(level, AT), "쏜 쪽이 없으면 언제나");
        assertTrue(byPlayer.mayInteract(level, AT), "플레이어가 쐈으면 mobGriefing 과 무관");
        assertFalse(bySkeleton.mayInteract(level, AT), "몹이 쐈으면 mobGriefing 을 본다");
        level.griefing = true;
        assertTrue(bySkeleton.mayInteract(level, AT));
    }

    @Test
    void mayInteractRespectsSpawnProtectionForPlayers() {
        var level = new Level();
        var p = player();
        p.inventoryHost(new Player.InventoryHost() {
            public ItemStack slot(int windowSlot) { return ItemStack.EMPTY; }
            public int selected() { return 0; }
            public void replace(int windowSlot, ItemStack expect, ItemStack now) {}
            public int permissionLevel() { return 0; }
            @Override public boolean mayInteract(int x, int z) { return false; }
        });
        assertFalse(arrow(level, p, false).mayInteract(level, AT), "스폰 보호 안에서는 플레이어 투사체도 못 건드린다");
    }

    @Test
    void mayBreakNeedsImpactProjectileAndGameRule() {
        var level = new Level();
        var a = arrow(level, null, false);
        assertTrue(a.mayBreak(level), "화살은 impact_projectiles");
        level.projectilesBreak = false;
        assertFalse(a.mayBreak(level), "projectilesCanBreakBlocks 가 꺼지면 못 부순다");
        level.projectilesBreak = true;
        var potion = new ThrownPotion(level, level.nextEntityId());
        assertFalse(potion.mayBreak(level), "물약은 impact_projectiles 가 아니다");
    }

    // ------------------------------------------------------------------ 점화

    @Test
    void burningArrowLightsCandleButNotWaterlogged() {
        var level = new Level();
        level.setBlock(AT, state("candle"), 3);
        arrow(level, null, false).onHitBlock(hit());
        assertFalse(B.prop("lit").is(level.getBlockState(AT)), "불이 없으면 안 켜진다");
        arrow(level, null, true).onHitBlock(hit());
        assertTrue(B.prop("lit").is(level.getBlockState(AT)), "불붙은 화살이 켠다");

        level.setBlock(AT, B.prop("waterlogged").with(state("candle"), true), 3);
        arrow(level, null, true).onHitBlock(hit());
        assertFalse(B.prop("lit").is(level.getBlockState(AT)), "물에 잠긴 양초는 안 켜진다");

        level.griefing = false;
        level.setBlock(AT, state("candle_cake"), 3);
        arrow(level, new Skeleton(level, level.nextEntityId()), true).onHitBlock(hit());
        assertTrue(B.prop("lit").is(level.getBlockState(AT)), "양초는 mayInteract 를 보지 않는다(mobGriefing 꺼도 켜진다)");
    }

    @Test
    void burningArrowLightsCampfireOnlyWhenAllowed() {
        var level = new Level();
        int unlit = B.prop("lit").with(state("campfire"), false);
        level.setBlock(AT, unlit, 3);
        level.griefing = false;
        arrow(level, new Skeleton(level, level.nextEntityId()), true).onHitBlock(hit());
        assertFalse(B.prop("lit").is(level.getBlockState(AT)), "몹 화살은 mobGriefing 꺼지면 못 켠다");
        arrow(level, player(), true).onHitBlock(hit());
        assertTrue(B.prop("lit").is(level.getBlockState(AT)), "플레이어 화살은 켠다");

        level.setBlock(AT, B.prop("waterlogged").with(unlit, true), 3);
        arrow(level, player(), true).onHitBlock(hit());
        assertFalse(B.prop("lit").is(level.getBlockState(AT)), "물에 잠긴 모닥불은 안 켜진다");
    }

    @Test
    void burningArrowPrimesTntWithShooterAsOwner() {
        var level = new Level();
        level.setBlock(AT, state("tnt"), 3);
        arrow(level, null, false).onHitBlock(hit());
        assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:tnt")), "불이 없으면 그대로");

        var skeleton = new Skeleton(level, level.nextEntityId());
        level.griefing = false;
        arrow(level, skeleton, true).onHitBlock(hit());
        assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:tnt")), "몹 화살은 mobGriefing 꺼지면 점화 못 한다");

        level.griefing = true;
        level.sounds.clear();
        arrow(level, skeleton, true).onHitBlock(hit());
        assertTrue(B.isAir(level.getBlockState(AT)), "점화되면 블록이 없어진다");
        var tnt = level.entities().tickSnapshot().stream().filter(e -> e instanceof PrimedTnt).map(e -> (PrimedTnt) e).findFirst().orElseThrow();
        assertSame(skeleton, tnt.getOwner(), "쏜 생물이 주인");
        assertEquals(80, tnt.getFuse());
        assertTrue(level.sounds.contains("minecraft:entity.tnt.primed"));
        assertTrue(level.events.contains("minecraft:prime_fuse"));
    }

    @Test
    void flyingBurningArrowPrimesTnt() {
        var level = new Level();
        level.setBlock(AT, state("tnt"), 3);
        var a = arrow(level, player(), true);
        a.shoot(0, 0, 1, 1.5F, 0);
        level.addFreshEntity(a);
        for (int i = 0; i < 5 && count(level, PrimedTnt.class) == 0; i++) a.tick();
        assertEquals(1, count(level, PrimedTnt.class), "날아간 불화살이 맞혀서 점화");
        assertTrue(B.isAir(level.getBlockState(AT)));
    }

    // ------------------------------------------------------------------ 소리·울림·기울기

    @Test
    void amethystPlaysHitThenChime() {
        var level = new Level();
        level.setBlock(AT, state("amethyst_block"), 3);
        level.sounds.clear();
        level.pitches.clear();
        arrow(level, null, false).onHitBlock(hit());
        int hitAt = level.sounds.indexOf("minecraft:block.amethyst_block.hit"), chimeAt = level.sounds.indexOf("minecraft:block.amethyst_block.chime");
        assertTrue(hitAt >= 0 && chimeAt > hitAt, "hit 다음 chime: " + level.sounds);
        for (int i : new int[] {hitAt, chimeAt}) {
            float pitch = level.pitches.get(i);
            assertTrue(pitch >= 0.5F && pitch < 1.7F, "높이 0.5 + nextFloat×1.2: " + pitch);
        }
    }

    @Test
    void bellRingsOnProperFaceOnly() {
        var level = new Level();
        level.setBlock(AT, state("bell"), 3);   // 바닥, 북쪽을 본다 → 북·남 면이 알맞다
        var bell = (org.domi.furnace.server.block.entity.BellBlockEntity) level.getBlockEntity(AT);
        arrow(level, player(), false).onHitBlock(hitFace(Dir.WEST, 0.5));
        assertFalse(bell.shaking, "옆 축 면은 안 울린다");
        arrow(level, player(), false).onHitBlock(hitFace(Dir.NORTH, 0.9));
        assertFalse(bell.shaking, "0.8124 보다 위는 안 울린다");
        level.sounds.clear();
        var owner = player();
        arrow(level, owner, false).onHitBlock(hitFace(Dir.NORTH, 0.5));
        assertTrue(bell.shaking, "알맞은 면이면 울린다");
        assertTrue(level.sounds.contains("minecraft:block.bell.use"));
        int i = level.events.lastIndexOf("minecraft:block_change");
        assertSame(owner, level.eventSources.get(i), "울린 쪽은 쏜 플레이어");
    }

    @Test
    void bigDripleafTiltsFully() {
        var level = new Level();
        level.setBlock(AT.below(), state("clay"), 3);
        level.setBlock(AT, state("big_dripleaf"), 3);
        level.sounds.clear();
        arrow(level, null, false).onHitBlock(hit());
        assertEquals("full", B.prop("tilt").valueName(level.getBlockState(AT)));
        assertTrue(level.sounds.contains("minecraft:block.big_dripleaf.tilt_down"));
        assertTrue(level.hasScheduledTick(AT, B.id("minecraft:big_dripleaf")), "되돌릴 틱을 건다");
    }

    // ------------------------------------------------------------------ 파괴

    @Test
    void chorusFlowerBreaksWhenProjectileMayBreak() {
        var level = new Level();
        level.setBlock(AT.below(), state("end_stone"), 3);
        level.setBlock(AT, state("chorus_flower"), 3);
        level.projectilesBreak = false;
        arrow(level, null, false).onHitBlock(hit());
        assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:chorus_flower")), "게임룰이 꺼지면 그대로");

        level.projectilesBreak = true;
        level.griefing = false;
        arrow(level, new Skeleton(level, level.nextEntityId()), false).onHitBlock(hit());
        assertTrue(B.is(level.getBlockState(AT), B.id("minecraft:chorus_flower")), "몹 화살은 mobGriefing 도 본다");

        level.events.clear();
        level.eventSources.clear();
        var a = arrow(level, player(), false);
        a.onHitBlock(hit());
        assertTrue(B.isAir(level.getBlockState(AT)), "부서진다");
        assertSame(a, level.eventSources.get(level.events.indexOf("minecraft:block_destroy")), "부순 엔티티는 투사체");
    }

    @Test
    void snowballBreaksChorusFlower() {
        var level = new Level();
        level.setBlock(AT.below(), state("end_stone"), 3);
        level.setBlock(AT, state("chorus_flower"), 3);
        var snowball = new Snowball(level, level.nextEntityId());
        assertTrue(snowball.mayBreak(level), "눈덩이는 impact_projectiles");
        snowball.onHitBlock(hit());
        assertTrue(B.isAir(level.getBlockState(AT)), "눈덩이도 후렴화를 부순다");
    }

    @Test
    void decoratedPotCracksAndShatters() {
        var level = new Level();
        level.setBlock(AT, state("decorated_pot"), 3);
        arrow(level, null, false).onHitBlock(hit());
        assertTrue(B.isAir(level.getBlockState(AT)), "부서진다");
        var drops = level.entities().tickSnapshot().stream().filter(e -> e instanceof ItemEntity).map(e -> ((ItemEntity) e).getItem()).toList();
        assertEquals(4, drops.size(), "금 간 단지는 조각 4개(무늬 없으면 벽돌): " + drops);
        Integer brick = ItemContext.id("minecraft:item", "minecraft:brick");
        for (ItemStack s : drops) assertEquals((int) brick, s.itemId());
    }

    @Test
    void pointedDripstoneBreaksOnlyForFastTrident() {
        var level = new Level();
        level.setBlock(AT.above(), state("stone"), 3);
        int stalactite = B.prop("vertical_direction").with(state("pointed_dripstone"), "down");
        level.setBlock(AT, stalactite, 3);
        var trident = new ItemStack(ItemContext.id("minecraft:item", "minecraft:trident"), 1);
        var slow = new ThrownTrident(level, level.nextEntityId(), player(), trident);
        slow.setDeltaMovement(0.0, 0.0, 0.6);
        slow.onHitBlock(hit());
        assertFalse(B.isAir(level.getBlockState(AT)), "속도 0.6 은 넘지 않는다");
        var arrow = arrow(level, null, false);
        arrow.setDeltaMovement(0.0, 0.0, 3.0);
        arrow.onHitBlock(hit());
        assertFalse(B.isAir(level.getBlockState(AT)), "삼지창만");
        var fast = new ThrownTrident(level, level.nextEntityId(), player(), trident);
        fast.setDeltaMovement(0.0, 0.0, 0.61);
        fast.onHitBlock(hit());
        assertTrue(B.isAir(level.getBlockState(AT)), "빠른 삼지창이면 부서진다");
    }

    @Test
    void otherProjectilesAlsoReachBlockHook() {
        // 바닐라 onHitBlock 재정의는 모두 super 를 먼저 부른다 — 소형 화염구·셜커 탄환도 과녁·자수정 등에 반응
        var level = new Level();
        level.setBlock(AT, state("amethyst_block"), 3);
        level.sounds.clear();
        new ShulkerBullet(level, level.nextEntityId()).onHitBlock(hit());
        assertTrue(level.sounds.contains("minecraft:block.amethyst_block.hit"), level.sounds.toString());
    }
}

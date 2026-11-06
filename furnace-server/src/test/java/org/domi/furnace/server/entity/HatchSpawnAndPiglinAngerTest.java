// 개구리알 부화(올챙이)·스니퍼 알 부화(아기 스니퍼) 생성과 피글린 분노(금 블록 캐기·상자 열기·상자 탈것) 연결.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.behavior.BehaviorUtils;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HatchSpawnAndPiglinAngerTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    private static SkeletonCombatTest.ArenaLevel level() { return new SkeletonCombatTest.ArenaLevel(); }

    // ------------------------------------------------------------------ 개구리알 → 올챙이

    @Test
    void frogspawnHatchSpawnsTadpolesInVanillaRandomOrder() {
        var level = level();
        Pos pos = new Pos(4, -59, 4);
        level.setBlock(pos.below(), B.defaultState("minecraft:water"), 2);
        int spawn = B.defaultState("minecraft:frogspawn");
        level.setBlock(pos, spawn, 2);

        // 같은 씨의 난수로 바닐라 순서(마리 수 → 마리마다 x 오프셋·z 오프셋·방향)를 따로 계산해 둔다
        RandomSource expect = RandomSource.create(12345L);
        int n = expect.nextIntBetweenInclusive(2, 6);
        double[][] want = new double[n][3];
        for (int i = 0; i < n; i++) {
            want[i][0] = Math.clamp(expect.nextDouble(), (double) 0.2F, (double) 0.8F);
            want[i][1] = Math.clamp(expect.nextDouble(), (double) 0.2F, (double) 0.8F);
            want[i][2] = expect.nextIntBetweenInclusive(1, 361);
        }

        B.behavior(spawn).tick(spawn, level, pos, RandomSource.create(12345L));

        assertTrue(B.isAir(level.getBlockState(pos)), "부화하면 개구리알은 사라진다");
        var tadpoles = level.entities().tickSnapshot().stream().filter(e -> e instanceof Tadpole).map(e -> (Tadpole) e).toList();
        assertTrue(n >= 2 && n <= 6, "마리 수 2~6: " + n);
        assertEquals(n, tadpoles.size(), "마리 수가 난수와 같다");
        for (int i = 0; i < n; i++) {
            Tadpole t = tadpoles.get(i);
            assertEquals(pos.x() + want[i][0], t.x(), 1.0E-9, "x 오프셋");
            assertEquals(pos.z() + want[i][1], t.z(), 1.0E-9, "z 오프셋");
            assertEquals(pos.y() - 0.5, t.y(), 1.0E-9, "y 는 칸 − 0.5");
            assertEquals((float) want[i][2], t.yRot(), 1.0E-4F, "몸 방향 1~361");
            assertTrue(t.isPersistenceRequired(), "올챙이는 persistenceRequired");
        }
    }

    @Test
    void frogspawnWithoutWaterBreaksWithoutTadpoles() {
        var level = level();
        Pos pos = new Pos(6, -59, 6);
        int spawn = B.defaultState("minecraft:frogspawn");
        level.setBlock(pos, spawn, 2);   // 아래가 물이 아니다
        B.behavior(spawn).tick(spawn, level, pos, RandomSource.create(1L));
        assertTrue(B.isAir(level.getBlockState(pos)));
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof Tadpole), "올챙이는 없다");
    }

    // ------------------------------------------------------------------ 스니퍼 알 → 아기 스니퍼

    @Test
    void snifferEggHatchSpawnsBabySnifferAtCenter() {
        var level = level();
        Pos pos = new Pos(8, -59, 8);
        int ready = B.prop("hatch").withInt(B.defaultState("minecraft:sniffer_egg"), 2);
        level.setBlock(pos, ready, 2);
        B.behavior(ready).tick(ready, level, pos, RandomSource.create(7L));

        assertTrue(B.isAir(level.getBlockState(pos)), "다 자란 알은 부화하며 사라진다");
        var sniffers = level.entities().tickSnapshot().stream().filter(e -> e instanceof Sniffer).map(e -> (Sniffer) e).toList();
        assertEquals(1, sniffers.size());
        Sniffer s = sniffers.get(0);
        assertTrue(s.isBaby(), "아기 스니퍼");
        assertEquals(pos.x() + 0.5, s.x(), 1.0E-9);
        assertEquals(pos.y() + 0.5, s.y(), 1.0E-9, "칸 가운데(getCenter) — y 도 +0.5");
        assertEquals(pos.z() + 0.5, s.z(), 1.0E-9);
        assertTrue(s.yRot() >= -180.0F && s.yRot() < 180.0F, "wrapDegrees 범위: " + s.yRot());
    }

    @Test
    void snifferEggCracksBeforeHatching() {
        var level = level();
        Pos pos = new Pos(10, -59, 10);
        int egg = B.defaultState("minecraft:sniffer_egg");
        level.setBlock(pos, egg, 2);
        B.behavior(egg).tick(egg, level, pos, RandomSource.create(7L));
        assertEquals(1, B.prop("hatch").intValue(level.getBlockState(pos)), "아직 금만 간다");
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof Sniffer));
    }

    // ------------------------------------------------------------------ 피글린 분노

    private static Piglin idlePiglin(SkeletonCombatTest.ArenaLevel level, double x, double z) {
        var p = new Piglin(level, level.nextEntityId());
        p.moveTo(x, -60, z, 0, 0);
        p.setOnGround(true);
        p.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(p.blockPosition()));
        p.setBaby(false);
        level.addFreshEntity(p);
        return p;
    }

    private static void tick(SkeletonCombatTest.ArenaLevel level, int n) {
        for (int t = 0; t < n; t++) {
            level.time++;
            for (Entity e : level.entities().tickSnapshot()) {
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }
    }

    /** 감각기가 플레이어를 보고 또 한가한(IDLE) 순간까지 틱을 돌린다 — 뇌 활동은 난수라 고정 틱 수로는 못 맞춘다. */
    private static void tickUntilIdleAndSeeing(SkeletonCombatTest.ArenaLevel level, Piglin piglin, Player player) {
        for (int t = 0; t < 200 && !(PiglinAi.isIdle(piglin) && BehaviorUtils.canSee(piglin, player)); t++) tick(level, 1);
        assertTrue(PiglinAi.isIdle(piglin), "한가한 피글린");
        assertTrue(BehaviorUtils.canSee(piglin, player), "피글린이 플레이어를 본다");
    }

    private static boolean angryAt(Piglin p, Player at) {
        return p.getBrain().getMemory(MemoryModuleType.ANGRY_AT).map(u -> u.equals(at.uuid())).orElse(false);
    }

    /** 금 투구를 쓴 플레이어 — 금을 걸치지 않은 플레이어는 피글린이 바로 공격해(FIGHT) 한가하지 않아 분노가 의미 없다(바닐라도 같다). */
    private static Player player(SkeletonCombatTest.ArenaLevel level, double x, double z) {
        var p = new Player(level.nextEntityId(), UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.applyMove(x, -60, z, 0, 0, true);
        p.inventory().set(5, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "golden_helmet"), 1));
        level.player = p;
        return p;
    }

    @Test
    void miningGuardedBlockAngersNearbyPiglinsWithoutLineOfSight() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 6.5, 0.5);
        Piglin far = idlePiglin(level, 0.5, 40.5);   // 16칸 밖
        for (int t = 0; t < 200 && !(PiglinAi.isIdle(near) && PiglinAi.isIdle(far)); t++) tick(level, 1);
        assertTrue(PiglinAi.isIdle(near) && PiglinAi.isIdle(far), "한가한 피글린들");

        Pos gold = new Pos(2, -60, 0);
        int state = B.defaultState("minecraft:gold_block");
        level.setBlock(gold, state, 2);
        B.behavior(state).playerWillDestroy(level, gold, state, new PlayerActor(p));

        assertTrue(angryAt(near, p), "금 블록을 캐면 16칸 안 피글린이 화낸다(시야 불필요)");
        assertFalse(angryAt(far, p), "16칸 밖은 그대로");
    }

    @Test
    void miningUnguardedBlockDoesNotAngerPiglins() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 6.5, 0.5);
        for (int t = 0; t < 200 && !PiglinAi.isIdle(near); t++) tick(level, 1);
        Pos stone = new Pos(2, -60, 0);
        int state = B.defaultState("minecraft:stone");
        level.setBlock(stone, state, 2);
        B.behavior(state).playerWillDestroy(level, stone, state, new PlayerActor(p));
        assertFalse(angryAt(near, p), "#guarded_by_piglins 가 아니면 화내지 않는다");
    }

    @Test
    void openingBarrelAngersPiglinsThatSeeThePlayer() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 4.5, 0.5);
        tickUntilIdleAndSeeing(level, near, p);

        Pos barrel = new Pos(1, -60, 0);
        int state = B.defaultState("minecraft:barrel");
        level.setBlock(barrel, state, 2);
        B.behavior(state).useWithoutItem(state, level, barrel, new PlayerActor(p), null);
        assertTrue(angryAt(near, p), "통을 열면 보고 있던 피글린이 화낸다");
    }

    @Test
    void openingChestMinecartAngersPiglins() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 4.5, 0.5);
        tickUntilIdleAndSeeing(level, near, p);
        var cart = new MinecartChest(level, level.nextEntityId());
        cart.moveTo(0.5, -60, 2.5, 0, 0);
        level.addFreshEntity(cart);
        cart.interact(p, BlockActor.MAIN_HAND);
        assertTrue(angryAt(near, p), "상자 수레를 열면 피글린이 화낸다");
    }

    @Test
    void breakingChestMinecartAngersPiglins() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 4.5, 0.5);
        tickUntilIdleAndSeeing(level, near, p);
        var cart = new MinecartChest(level, level.nextEntityId());
        cart.moveTo(0.5, -60, 2.5, 0, 0);
        level.addFreshEntity(cart);
        cart.hurtBy(new Entity.Damage("minecraft:player_attack", false, false, false), 100.0F, p);
        assertTrue(cart.isRemoved(), "수레가 부서졌다");
        assertTrue(angryAt(near, p), "상자 수레를 부수면 피글린이 화낸다");
    }

    @Test
    void breakingChestBoatAngersPiglins() {
        var level = level();
        var p = player(level, 0.5, 0.5);
        Piglin near = idlePiglin(level, 4.5, 0.5);
        tickUntilIdleAndSeeing(level, near, p);
        var boat = new ChestBoat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 2.5, 0, 0);
        level.addFreshEntity(boat);
        boat.hurtBy(new Entity.Damage("minecraft:player_attack", false, false, false), 100.0F, p);
        assertTrue(angryAt(near, p), "상자 배를 부수면 피글린이 화낸다");
    }
}

// 엔더의 눈 비행: 속도 점화식, 물 밖 틱마다 엔티티 난수 두 번(바닐라 입자 인자), 80틱 뒤 소리·드롭/깨짐.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EyeOfEnderTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static void tickOnce(EyeOfEnder e) {
        e.setOldPosAndRot();
        e.tickCount++;
        e.tick();
    }

    private static EyeOfEnder launch(BlockPlacementTest.TestLevel level, long seed, Pos target) {
        var eye = new EyeOfEnder(level, level.nextEntityId(), 0.5, -50, 0.0);
        ((LegacyRandomSource) eye.random()).setSeed(seed);
        level.addFreshEntity(eye);
        eye.signalTo(target);
        return eye;
    }

    /** signalTo 의 nextInt(5) > 0 인가를 같은 시드로 미리 본다. */
    private static boolean survives(long seed) { return new LegacyRandomSource(seed).nextInt(5) > 0; }

    private static long seedWhere(boolean survive) {
        for (long s = 1; ; s++) if (survives(s) == survive) return s;
    }

    @Test
    void firstTickFollowsTheVanillaRecurrence() {
        var level = new BlockPlacementTest.TestLevel();
        var eye = launch(level, 5L, new Pos(100, 0, 0));
        assertEquals(0.5 + 12.0, eye.targetX(), 1e-9);
        assertEquals(-50 + 8.0, eye.targetY(), 1e-9);
        tickOnce(eye);
        // 처음엔 속도 0: 거리 12 → 속도 = 0 + (12 - 0) * 0.0025, 위로 향하니 vy = 0 + (1 - 0) * 0.015F
        assertEquals(0.03, eye.vx(), 1e-12);
        assertEquals(0.015F, eye.vy(), 1e-12);
        assertEquals(0.0, eye.vz(), 1e-12);
        assertEquals(1, eye.life());
        // 위치는 그 틱 처음의 속도(0)만큼 — 아직 안 움직였다
        assertEquals(0.5, eye.x(), 1e-12);
        tickOnce(eye);
        assertEquals(0.5 + 0.03, eye.x(), 1e-12, "둘째 틱엔 이전 틱 속도만큼");
        assertEquals(-50 + (double) 0.015F, eye.y(), 1e-6);
    }

    @Test
    void closeTargetSlowsDownBelowOneBlock() {
        var level = new BlockPlacementTest.TestLevel();
        var eye = launch(level, 5L, new Pos(0, -50, 0));
        for (int i = 0; i < 5; i++) tickOnce(eye);
        // 목표 바로 위 — 거리 < 1 이면 속도·수직 속도에 0.8 을 곱한다. 속도는 점점 줄어들어야지 늘면 안 된다
        assertTrue(Math.abs(eye.vx()) < 0.01 && Math.abs(eye.vz()) < 0.01);
    }

    @Test
    void airTicksConsumeTwoEntityRandomDoublesEach() {
        for (int n : new int[] {1, 7, 30}) {
            var level = new BlockPlacementTest.TestLevel();
            var eye = launch(level, 4242L, new Pos(100, 0, 0));
            for (int i = 0; i < n; i++) tickOnce(eye);
            var ref = new LegacyRandomSource(4242L);
            ref.nextInt(5);
            for (int i = 0; i < 2 * n; i++) ref.nextDouble();
            assertEquals(ref.nextLong(), eye.random().nextLong(), "n=" + n);
        }
    }

    @Test
    void waterTicksConsumeNoRandom() {
        var level = new BlockPlacementTest.TestLevel();
        int water = B.defaultState(B.id("minecraft:water"));
        for (int x = -3; x <= 6; x++) for (int y = -53; y <= -40; y++) for (int z = -3; z <= 3; z++) level.setBlock(new Pos(x, y, z), water, 2);
        var eye = launch(level, 99L, new Pos(100, 0, 0));
        for (int i = 0; i < 10; i++) tickOnce(eye);
        assertTrue(eye.isInWater());
        var ref = new LegacyRandomSource(99L);
        ref.nextInt(5);
        assertEquals(ref.nextLong(), eye.random().nextLong(), "물속에선 입자 인자 난수가 없다");
    }

    @Test
    void afterEightyTicksItDropsAnItemWhenSurviving() {
        var level = new BlockPlacementTest.TestLevel();
        var eye = launch(level, seedWhere(true), new Pos(100, 0, 0));
        for (int i = 0; i < 80; i++) tickOnce(eye);
        assertFalse(eye.isRemoved(), "80틱까지는 산다");
        tickOnce(eye);
        assertTrue(eye.isRemoved());
        var items = level.entities().tickSnapshot().stream().filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity) e).toList();
        assertEquals(1, items.size());
        assertEquals("minecraft:ender_eye", ItemTools.itemName(items.get(0).getItem()));
        assertEquals(1, items.get(0).getItem().count());
        assertEquals(eye.x(), items.get(0).x(), 1e-9);
    }

    @Test
    void afterEightyTicksItShattersWhenNotSurviving() {
        var level = new BlockPlacementTest.TestLevel();
        var eye = launch(level, seedWhere(false), new Pos(100, 0, 0));
        for (int i = 0; i < 81; i++) tickOnce(eye);
        assertTrue(eye.isRemoved());
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof ItemEntity), "깨지면 아이템이 없다");
    }

    @Test
    void savedItemRoundTrips() {
        var level = new BlockPlacementTest.TestLevel();
        var eye = launch(level, 3L, new Pos(100, 0, 0));
        var copy = new EyeOfEnder(level, level.nextEntityId());
        copy.load(eye.saveAsPassenger());
        assertEquals("minecraft:ender_eye", ItemTools.itemName(copy.getItem()));
    }

    @Test
    void isNotFireImmuneLikeVanilla() {
        var level = new BlockPlacementTest.TestLevel();
        assertFalse(new EyeOfEnder(level, level.nextEntityId()).fireImmune());
    }
}

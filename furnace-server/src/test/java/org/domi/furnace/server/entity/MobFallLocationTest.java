// 몹 낙하 위치(FallLocation): 사다리·덩굴에서 떨어져 죽은 몹의 사망 메시지가 death.fell.accident.<위치> 로 나오는가.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobFallLocationTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static int state(String name) { return B.defaultState(name); }

    /** 사다리 칸에 매달렸다가(onClimbable) 높은 곳에서 떨어져 바닥에 닿아 죽인다. */
    private static Wolf fallFrom(SkeletonCombatTest.ArenaLevel level, String climbBlock, Runnable beforeLanding) {
        level.setBlock(new Pos(5, -50, 5), state("minecraft:stone"), 2);
        level.setBlock(new Pos(5, -49, 5), state(climbBlock), 2);
        var wolf = (Wolf) Entities.create(level, "minecraft:wolf");
        wolf.moveTo(5.5, -49, 5.5, 0, 0);
        level.addFreshEntity(wolf);
        assertTrue(wolf.onClimbable(), climbBlock);
        assertEquals(new Pos(5, -49, 5), wolf.lastClimbablePos());
        // 매달린 칸을 떠나 바닥 바로 위에서 떨어진다(낙하 거리 30 — 죽기에 충분).
        wolf.moveTo(0.5, -60, 0.5, 0, 0);
        beforeLanding.run();
        wolf.fallDistance = 30.0F;
        wolf.setDeltaMovement(0, -0.5, 0);
        for (int i = 0; i < 5 && wolf.isAlive(); i++) wolf.tick();
        assertTrue(wolf.isDeadOrDying(), climbBlock);
        return wolf;
    }

    @Test
    void ladderFallDeathMessage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var wolf = fallFrom(level, "minecraft:ladder", () -> {});
        assertEquals("death.fell.accident.ladder", wolf.getCombatTracker().getDeathMessage(wolf.displayName()).translationKey());
        assertNull(wolf.lastClimbablePos(), "땅에 닿으면 지워진다");
    }

    @Test
    void vineFallDeathMessage() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var wolf = fallFrom(level, "minecraft:vine", () -> {});
        assertEquals("death.fell.accident.vines", wolf.getCombatTracker().getDeathMessage(wolf.displayName()).translationKey());
    }

    /** 바닐라는 기록 순간 그 칸의 지금 블록을 본다: 사다리가 부서져 공기면 other_climbable. */
    @Test
    void brokenLadderBecomesOtherClimbable() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var wolf = fallFrom(level, "minecraft:ladder", () -> level.setBlock(new Pos(5, -49, 5), state("minecraft:air"), 2));
        assertEquals("death.fell.accident.other_climbable", wolf.getCombatTracker().getDeathMessage(wolf.displayName()).translationKey());
    }

    @Test
    void plainFallIsGeneric() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var wolf = (Wolf) Entities.create(level, "minecraft:wolf");
        wolf.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(wolf);
        wolf.fallDistance = 30.0F;
        wolf.setDeltaMovement(0, -0.5, 0);
        for (int i = 0; i < 5 && wolf.isAlive(); i++) wolf.tick();
        assertTrue(wolf.isDeadOrDying());
        assertNull(wolf.lastClimbablePos());
        assertEquals("death.fell.accident.generic", wolf.getCombatTracker().getDeathMessage(wolf.displayName()).translationKey());
    }

    /** FallLocation.blockToFallLocation 표(다락문은 사다리로 친다). */
    @Test
    void blockToFallLocation() {
        assertEquals("ladder", LivingEntity.fallLocationOf(state("minecraft:ladder")));
        assertEquals("ladder", LivingEntity.fallLocationOf(state("minecraft:oak_trapdoor")));
        assertEquals("vines", LivingEntity.fallLocationOf(state("minecraft:vine")));
        assertEquals("weeping_vines", LivingEntity.fallLocationOf(state("minecraft:weeping_vines_plant")));
        assertEquals("twisting_vines", LivingEntity.fallLocationOf(state("minecraft:twisting_vines")));
        assertEquals("scaffolding", LivingEntity.fallLocationOf(state("minecraft:scaffolding")));
        assertEquals("other_climbable", LivingEntity.fallLocationOf(state("minecraft:cave_vines")));
    }

    private static int with(int s, String prop, String value) { return B.prop(prop).with(s, value); }

    private static void rehome(Entity e) {
        double x = e.x(), y = e.y(), z = e.z();
        e.moveTo(x, y + 1, z, 0, 0);
        e.moveTo(x, y, z, 0, 0);
    }

    /** LivingEntity.trapdoorUsableAsLadder: 열린 다락문 바로 아래가 같은 방향 사다리면 몹도 매달린다. */
    @Test
    void openTrapdoorAboveLadderIsClimbable() {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.setBlock(new Pos(5, -50, 5), state("minecraft:stone"), 2);
        level.setBlock(new Pos(5, -49, 5), with(state("minecraft:ladder"), "facing", "north"), 2);
        Pos trap = new Pos(5, -48, 5);
        int open = with(with(state("minecraft:spruce_trapdoor"), "open", "true"), "facing", "north");
        level.setBlock(trap, open, 2);
        var wolf = (Wolf) Entities.create(level, "minecraft:wolf");
        wolf.moveTo(5.5, -48, 5.5, 0, 0);
        level.addFreshEntity(wolf);
        assertTrue(wolf.onClimbable(), "열린 다락문 + 같은 방향 사다리");
        assertEquals(trap, wolf.lastClimbablePos());
        assertEquals("ladder", wolf.currentFallLocation());

        // 바닐라처럼 발 칸 블록(getInBlockState)은 틱 시작·칸 이동 때 다시 읽는다 — 블록을 바꾼 뒤 한 칸 옮겼다 돌아와 캐시를 비운다
        level.setBlock(trap, with(open, "open", "false"), 2);
        rehome(wolf);
        assertFalse(wolf.onClimbable(), "닫힌 다락문");
        level.setBlock(trap, with(open, "facing", "south"), 2);
        rehome(wolf);
        assertFalse(wolf.onClimbable(), "방향이 다름");
        level.setBlock(trap, open, 2);
        level.setBlock(new Pos(5, -49, 5), state("minecraft:stone"), 2);
        assertFalse(wolf.onClimbable(), "아래가 사다리가 아님");
    }
}

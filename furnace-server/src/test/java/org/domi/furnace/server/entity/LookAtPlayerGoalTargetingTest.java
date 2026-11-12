// O06 C2: LookAtPlayerGoal 은 바닐라처럼 TargetingConditions(비전투·사거리·투명 보정·시야·이 몹을 탄 플레이어 제외)로 대상을 고른다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.goal.LookAtPlayerGoal;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LookAtPlayerGoalTargetingTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private static Player player(NearestPlayerTargetingParityTest.PlayersLevel level, int id, GameMode mode, double x, double z) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, -60, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    private static Cow cow(NearestPlayerTargetingParityTest.PlayersLevel level) {
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0, -60, 0, 0, 0);
        level.addFreshEntity(cow);
        return cow;
    }

    @Test
    void looksAtCreativePlayerButNotSpectator() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        var spec = player(level, 100, GameMode.SPECTATOR, 2, 0);
        var goal = new LookAtPlayerGoal(cow, 6.0F, 1.0F);
        assertFalse(goal.canUse(), "관전자만 있으면 안 본다");
        var creative = player(level, 101, GameMode.CREATIVE, 4, 0);
        assertTrue(goal.canUse(), "크리에이티브(무적)도 바라본다 — 바닐라 비전투 조건은 canBeSeenByAnyone 만 본다");
        assertSame(creative, lookAt(goal));
        assertNotNull(spec);
    }

    @Test
    void sneakingPlayerIsSeenOnlyWithinEightyPercentOfRange() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        var p = player(level, 100, GameMode.SURVIVAL, 5.0, 0);   // 사거리 6 의 80% = 4.8
        var goal = new LookAtPlayerGoal(cow, 6.0F, 1.0F);
        assertTrue(goal.canUse());
        p.sneaking(true);
        assertFalse(goal.canUse(), "웅크리면 사거리가 ×0.8 로 줄어 5칸은 못 본다");
        p.sneaking(false);
        assertTrue(goal.canUse());
    }

    @Test
    void playerRidingThisMobIsNotLookedAt() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        var p = player(level, 100, GameMode.SURVIVAL, 1.0, 0);
        var goal = new LookAtPlayerGoal(cow, 6.0F, 1.0F);
        assertTrue(goal.canUse());
        assertTrue(p.startRiding(cow, true));
        assertFalse(goal.canUse(), "이 몹을 탄 플레이어는 notRiding 선택자가 거른다");
    }

    @Test
    void playerRidingAnotherMobIsStillLookedAt() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        Cow other = new Cow(level, level.nextEntityId());
        other.moveTo(1, -60, 0, 0, 0);
        level.addFreshEntity(other);
        var p = player(level, 100, GameMode.SURVIVAL, 1.0, 0);
        assertTrue(p.startRiding(other, true));
        assertTrue(new LookAtPlayerGoal(cow, 6.0F, 1.0F).canUse());
    }

    @Test
    void doesNotLookThroughWalls() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        player(level, 100, GameMode.SURVIVAL, 3.0, 0);
        var goal = new LookAtPlayerGoal(cow, 6.0F, 1.0F);
        assertTrue(goal.canUse());
        int stone = org.domi.furnace.server.block.B.defaultState(org.domi.furnace.server.block.B.id("minecraft:stone"));
        for (int y = -61; y <= -57; y++) for (int z = -2; z <= 2; z++) level.setBlock(new org.domi.furnace.server.block.Pos(1, y, z), stone, 2);
        cow.tick();   // 시야 캐시는 틱마다 비운다
        assertFalse(goal.canUse(), "벽 뒤 플레이어는 시야 검사(TargetingConditions)로 걸러진다");
    }

    @Test
    void keepsLookingAtPlayerWhoBecomesSpectatorUntilLookTimeEnds() {
        var level = new NearestPlayerTargetingParityTest.PlayersLevel();
        Cow cow = cow(level);
        var p = player(level, 100, GameMode.SURVIVAL, 2.0, 0);
        var goal = new LookAtPlayerGoal(cow, 6.0F, 1.0F);
        assertTrue(goal.canUse());
        goal.start();
        p.vitals().loadGameTypes(null, GameMode.SPECTATOR, false);
        assertTrue(goal.canContinueToUse(), "바닐라 canContinueToUse 는 isAlive·거리·남은 시간만 본다");
    }

    private static Player lookAt(LookAtPlayerGoal goal) {
        try {
            var f = LookAtPlayerGoal.class.getDeclaredField("lookAt");
            f.setAccessible(true);
            return (Player) f.get(goal);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

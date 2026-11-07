// X1: 가까운 플레이어 찾기(getNearestPlayer(조건, …))가 사거리 밖 플레이어의 조건 검사를 건너뛰어도 예전(모두 검사)과 같은 플레이어를 고르는지.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NearestPlayerTargetingParityTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final class PlayersLevel extends BlockPlacementTest.TestLevel {
        final List<Player> players = new ArrayList<>();

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public void forEachPlayer(Consumer<Player> c) { for (Player p : players) c.accept(p); }
            };
        }
    }

    /** 변경 전 구현: 연결이 끊기지 않은 모든 플레이어에 조건을 건다. */
    private static Player reference(TargetingConditions conditions, Mob source, double x, double y, double z, List<Player> players) {
        Player best = null;
        double bestD = -1.0;
        for (Player p : players) {
            if (p.isDisconnected() || !conditions.test(source, p)) continue;
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (bestD == -1.0 || d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    @Test
    void sameChoiceAsCheckingEveryPlayer() {
        Random rnd = new Random(3);
        long nonNull = 0, nulls = 0;
        for (int round = 0; round < 40; round++) {
            var level = new PlayersLevel();
            GameMode[] modes = {GameMode.SURVIVAL, GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.SPECTATOR};
            for (int i = 0; i < 14; i++) {
                var p = new Player(100 + i, UUID.randomUUID(), "P" + i, new ConcurrentEmbeddedChannel());
                p.vitals().env(new org.domi.furnace.server.entity.PlayerVitalsTest.TestEnv());
                p.vitals().loadGameTypes(null, modes[rnd.nextInt(modes.length)], false);
                p.applyMove(rnd.nextDouble() * 80 - 40, -60, rnd.nextDouble() * 80 - 40, 0, 0, true);
                p.sneaking(rnd.nextInt(3) == 0);
                level.players.add(p);
            }
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(rnd.nextDouble() * 40 - 20, -60, rnd.nextDouble() * 40 - 20, 0, 0);
            level.addFreshEntity(cow);
            Predicate<Targetable> odd = t -> t.id() % 2 == 1;
            TargetingConditions[] conds = {
                    TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight(),
                    TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight().selector(odd),
                    TargetingConditions.forNonCombat().range(2.0).ignoreLineOfSight(),
                    TargetingConditions.forNonCombat().range(1.5).ignoreLineOfSight(),
                    TargetingConditions.forNonCombat().range(24.0).ignoreLineOfSight().ignoreInvisibilityTesting(),
                    TargetingConditions.forCombat().range(16.0).ignoreLineOfSight().selector(odd),
                    TargetingConditions.forNonCombat().ignoreLineOfSight(),
            };
            for (TargetingConditions c : conds) {
                Player expected = reference(c, cow, cow.x(), cow.y(), cow.z(), level.players);
                Player got = level.getNearestPlayer(c, cow, cow.x(), cow.y(), cow.z());
                assertSame(expected, got, "round " + round);
                if (expected == null) nulls++; else nonNull++;
                // 문맥 위치가 출처 위치와 다른 호출(예: 눈높이 기준)도 같다
                Player expected2 = reference(c, cow, cow.x(), cow.y() + 1.0, cow.z(), level.players);
                assertSame(expected2, level.getNearestPlayer(c, cow, cow.x(), cow.y() + 1.0, cow.z()), "round " + round + " 눈높이");
            }
            // 출처 없음(드래곤 수정 파괴 등)은 거리 제한 없이 같은 선택
            TargetingConditions none = TargetingConditions.forCombat().range(5.0).ignoreLineOfSight();
            assertSame(reference(none, null, 0, -60, 0, level.players), level.getNearestPlayer(none, null, 0, -60, 0));
        }
        assertTrue(nonNull > 50 && nulls > 20, "고른 경우/못 고른 경우가 모두 나와야 한다: " + nonNull + "/" + nulls);
    }

    /** 사거리가 2 보다 작아도 실제 상한은 2 이므로, 거리 1.8 의 플레이어는 range 1.5 에서도 골라져야 한다(경계 고정). */
    @Test
    void rangeBelowTwoStillReachesTwoBlocks() {
        var level = new PlayersLevel();
        var p = new Player(100, UUID.randomUUID(), "Near", new ConcurrentEmbeddedChannel());
        p.vitals().env(new org.domi.furnace.server.entity.PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(1.8, -60, 0, 0, 0, true);
        level.players.add(p);
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0, -60, 0, 0, 0);
        level.addFreshEntity(cow);
        for (double range : new double[] {0.5, 1.0, 1.5, 1.99, 2.0}) {
            TargetingConditions c = TargetingConditions.forNonCombat().range(range).ignoreLineOfSight();
            Player expected = reference(c, cow, cow.x(), cow.y(), cow.z(), level.players);
            assertSame(expected, level.getNearestPlayer(c, cow, cow.x(), cow.y(), cow.z()), "range " + range);
        }
        TargetingConditions c15 = TargetingConditions.forNonCombat().range(1.5).ignoreLineOfSight();
        assertSame(p, level.getNearestPlayer(c15, cow, cow.x(), cow.y(), cow.z()));
    }
}

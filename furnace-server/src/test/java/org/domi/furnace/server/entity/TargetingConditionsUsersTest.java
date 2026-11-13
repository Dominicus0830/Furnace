// 비전투 대상 조건의 첫 검사가 canBeSeenByAnyone(크리에이티브 포함)이 된 뒤, 크리에이티브를 따로 빼야 하는 사용처가 바닐라와 같은지(PR #274 독립 검토 재현 R1·R2·R3·R5).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.sensing.NearestLivingEntitySensor;
import org.domi.furnace.server.entity.ai.sensing.PiglinSpecificSensor;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TargetingConditionsUsersTest {
    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    private static Player player(LookAtPlayerConditionsTest.PlayersLevel level, int id, GameMode mode, double x, double z) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, -60, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    /** R2: 바닐라 Pufferfish.SCARY_MOB 은 크리에이티브 플레이어를 뺀다(isCreative → false). */
    @Test
    void pufferfishIgnoresCreative() {
        var level = new LookAtPlayerConditionsTest.PlayersLevel();
        var fish = new Pufferfish(level, level.nextEntityId());
        fish.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(fish);
        var creative = player(level, 100, GameMode.CREATIVE, 1.5, 0.5);
        assertFalse(Pufferfish.TARGETING_CONDITIONS.test(fish, creative), "바닐라: 크리에이티브는 복어를 부풀리지 않는다");
    }

    /** R1: 바닐라 PiglinSpecificSensor 는 piglin.canAttack(player)(= canBeSeenAsEnemy) — 가까운 크리에이티브를 건너뛰고 서바이벌을 고른다. */
    @Test
    void piglinSensorSkipsCreativeForNotWearingGold() throws Exception {
        var level = new LookAtPlayerConditionsTest.PlayersLevel();
        var piglin = new Piglin(level, level.nextEntityId());
        piglin.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(piglin);
        player(level, 100, GameMode.CREATIVE, 2.5, 0.5);
        var survival = player(level, 101, GameMode.SURVIVAL, 5.5, 0.5);
        var near = new NearestLivingEntitySensor<Mob>();
        var spec = new PiglinSpecificSensor();
        var m1 = org.domi.furnace.server.entity.ai.brain.Sensor.class.getDeclaredMethod("doTick", org.domi.furnace.server.block.ChunkLevel.class, Mob.class);
        m1.setAccessible(true);
        m1.invoke(near, level, piglin);
        m1.invoke(spec, level, piglin);
        var got = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD);
        assertTrue(got.isPresent(), "서바이벌 플레이어가 있다");
        assertSame(survival, got.get(), "바닐라: 크리에이티브는 canAttack 거짓이라 건너뛴다");
    }

    /** R5: 바닐라 HoldGroundAttackGoal.shoutTargeting 은 forNonCombat — 동맹 일러저에게도 외친다. */
    @Test
    void holdGroundShoutsToAlliedIllagers() throws Exception {
        var level = new LookAtPlayerConditionsTest.PlayersLevel();
        var a = new Pillager(level, level.nextEntityId());
        a.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(a);
        var b = new Pillager(level, level.nextEntityId());
        b.moveTo(3.5, -60, 0.5, 0, 0);
        level.addFreshEntity(b);
        var goal = new Raider.HoldGroundAttackGoal(a, 10.0F);
        var m = Raider.HoldGroundAttackGoal.class.getDeclaredMethod("nearbyRaiders");
        m.setAccessible(true);
        @SuppressWarnings("unchecked") var list = (java.util.List<Raider>) m.invoke(goal);
        assertTrue(list.contains(b), "바닐라: 근처 약탈자(동맹)도 외침 대상");
    }

    /** R3: 바닐라 ArmorStand.canBeSeenByAnyone = !투명 && !마커 — 대상 조건 첫 검사에서 빠진다. */
    @Test
    void invisibleOrMarkerArmorStandIsNotSeen() {
        var level = new LookAtPlayerConditionsTest.PlayersLevel();
        var stand = new ArmorStand(level, level.nextEntityId());
        stand.moveTo(1.5, -60, 0.5, 0, 0);
        level.addFreshEntity(stand);
        assertTrue(stand.canBeSeenByAnyone());
        stand.setInvisible(true);
        assertFalse(stand.canBeSeenByAnyone(), "투명 거치대");
        assertFalse(stand.canBeSeenAsEnemy(), "전투 조건도 canBeSeenByAnyone 을 거친다");
    }
}

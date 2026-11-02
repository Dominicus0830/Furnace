// 습격자 목표(Raider 내부 목표): 깃발 줍기·마을로 향하기·축하의 시작 조건과 동작. 기대값은 바닐라 Raider 내부 클래스 손계산.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.raid.Raid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RaiderGoalsTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    static final class RaidLevel extends PatrolSpawnerTest.PatrolLevel {
        boolean village;

        RaidLevel() { super(5L); }

        @Override public boolean isVillage(Pos pos) { return village; }
    }

    private static Raid raid(RaidLevel level, String status) {
        NbtCompound t = new NbtCompound();
        t.putInt("Id", 3);
        t.putBoolean("Started", true);
        t.putBoolean("Active", true);
        t.putInt("GroupsSpawned", 1);
        t.putInt("NumGroups", 3);
        t.putString("Status", status);
        t.putInt("CX", 8);
        t.putInt("CY", -60);
        t.putInt("CZ", 8);
        return new Raid(level, t);
    }

    private static Pillager pillager(RaidLevel level, Raid raid) {
        var p = new Pillager(level, level.nextEntityId());
        p.moveTo(8.5, -60, 8.5, 0, 0);
        p.setCurrentRaid(raid);
        p.setCanJoinRaid(true);
        level.addFreshEntity(p);
        return p;
    }

    @Test
    void celebrationOnlyAfterRaidersWon() {
        var level = new RaidLevel();
        var ongoing = pillager(level, raid(level, "ongoing"));
        var celebrate = new Raider.RaiderCelebration(ongoing);
        assertFalse(celebrate.canUse(), "진행 중에는 축하하지 않는다");

        var loss = pillager(level, raid(level, "loss"));
        var g = new Raider.RaiderCelebration(loss);
        assertTrue(g.canUse(), "습격자가 이기면(마을 패배) 축하");
        g.start();
        assertTrue(loss.isCelebrating());
        g.stop();
        assertFalse(loss.isCelebrating());

        var victory = pillager(level, raid(level, "victory"));
        assertFalse(new Raider.RaiderCelebration(victory).canUse(), "마을이 이기면 습격자는 축하하지 않는다");
    }

    @Test
    void pathfindToRaidOnlyOutsideVillageAndRecruitsNearbyRaiders() {
        var level = new RaidLevel();
        Raid raid = raid(level, "ongoing");
        var p = pillager(level, raid);
        var goal = new Raider.PathfindToRaidGoal(p);
        assertTrue(goal.canUse(), "마을 밖이면 습격 중심으로");
        assertTrue(goal.canContinueToUse());
        level.village = true;
        assertFalse(goal.canUse(), "이미 마을 안이면 필요 없다");
        assertFalse(goal.canContinueToUse());
        level.village = false;

        // 첫 tick 에서 tickCount(0) > recruitmentTick(0) 은 거짓 — 이후 틱에서 근처 16칸의 비참여 습격자를 끌어들인다
        var other = new Pillager(level, level.nextEntityId());
        other.moveTo(10.5, -60, 8.5, 0, 0);
        other.setCanJoinRaid(true);
        level.addFreshEntity(other);
        assertNull(other.getCurrentRaid());
        p.tickCount = 5;   // 엔티티 틱이 올리는 값 — recruitmentTick(0) 보다 커야 한다
        goal.tick();
        assertEquals(raid, other.getCurrentRaid(), "근처 습격자를 습격에 참여시킨다");
    }

    @Test
    void obtainBannerWalksToDroppedOminousBannerWhenWaveHasNoLeader() {
        var level = new RaidLevel();
        Raid raid = raid(level, "ongoing");
        var p = pillager(level, raid);
        p.setOnGround(true);
        var goal = new Raider.ObtainRaidLeaderBannerGoal(p);
        assertFalse(goal.canUse(), "깃발이 없으면 쓸 수 없다");
        var banner = new ItemEntity(level, level.nextEntityId(), 12.5, -60, 8.5, Raider.leaderBanner());
        banner.setNoPickUpDelay();
        level.addFreshEntity(banner);
        assertTrue(goal.canUse(), "파도 대장이 없고 근처에 불길한 깃발이 있다");
        // 대장이 있으면 줍지 않는다
        raid.setLeader(p.getWave(), p);
        assertFalse(goal.canUse(), "이미 대장이 있다");
    }

    @Test
    void pickUpBannerMakesRaiderWaveLeader() {
        var level = new RaidLevel();
        Raid raid = raid(level, "ongoing");
        var p = pillager(level, raid);
        var banner = new ItemEntity(level, level.nextEntityId(), 8.5, -60, 8.5, Raider.leaderBanner());
        banner.setNoPickUpDelay();
        level.addFreshEntity(banner);
        assertNull(raid.getLeader(p.getWave()));
        p.pickUpItem(banner);
        assertSame(p, raid.getLeader(p.getWave()));
        assertTrue(p.isPatrolLeader());
        assertTrue(p.isCaptain(), "머리에 불길한 깃발을 쓴 순찰 지도자");
    }

    @Test
    void witchJoinsRaidUnlessNaturallySpawned() {
        // Raider.finalizeSpawn: canJoinRaid = type != WITCH || reason != NATURAL (생성기·이벤트 마녀는 참여)
        var level = new RaidLevel();
        var natural = new Witch(level, level.nextEntityId());
        natural.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(natural.blockPosition()));
        assertFalse(natural.canJoinRaid());
        var spawner = new Witch(level, level.nextEntityId());
        spawner.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.SPAWNER);
        spawner.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(spawner.blockPosition()));
        assertTrue(spawner.canJoinRaid());
        var pillager = new Pillager(level, level.nextEntityId());
        pillager.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pillager.blockPosition()));
        assertTrue(pillager.canJoinRaid(), "마녀가 아니면 자연 스폰도 참여");
    }
}

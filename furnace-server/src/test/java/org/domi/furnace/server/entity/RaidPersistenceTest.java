// 습격 저장(raids.dat 왕복·중심 청크 분배·보류), 습격자 RaidId 복귀, 습격 징조 위치 저장, 습격자 목표 선택 조건.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.raid.Raid;
import org.domi.furnace.server.world.raid.RaidStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaidPersistenceTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    /** 습격 저장소를 바꿔 끼울 수 있는 순찰 시험 레벨. */
    static final class StoreLevel extends PatrolSpawnerTest.PatrolLevel {
        final RaidStore store;

        StoreLevel(RaidStore store) {
            super(1L);
            this.store = store;
        }

        @Override public RaidStore raidStore() { return store; }
    }

    private static NbtCompound raidTag(int id, String status) {
        NbtCompound t = new NbtCompound();
        t.putInt("Id", id);
        t.putBoolean("Started", true);
        t.putBoolean("Active", true);
        t.putLong("TicksActive", 1234L);
        t.putInt("BadOmenLevel", 3);
        t.putInt("GroupsSpawned", 2);
        t.putInt("PreRaidTicks", 77);
        t.putInt("PostRaidTicks", 5);
        t.putFloat("TotalHealth", 96.5F);
        t.putInt("NumGroups", 5);
        t.putString("Status", status);
        t.putInt("CX", 40);
        t.putInt("CY", -60);
        t.putInt("CZ", -20);
        NbtList heroes = new NbtList(NbtIo.TAG_INT_ARRAY);
        heroes.add(new int[] {1, 2, 3, 4});
        t.putList("HeroesOfTheVillage", heroes);
        return t;
    }

    @Test
    void raidNbtRoundTrip() {
        var level = new StoreLevel(RaidStore.detached());
        Raid r = new Raid(level, raidTag(7, "ONGOING"));
        assertEquals(7, r.id());
        assertEquals(new Pos(40, -60, -20), r.center());
        assertEquals(3, r.raidOmenLevel());
        assertEquals(2, r.groupsSpawned());
        assertEquals(77, r.raidCooldownTicks());
        assertEquals(5, r.numGroups());
        assertTrue(r.isStarted());
        assertEquals(Raid.Status.ONGOING, r.status(), "대소문자 무시");
        assertEquals(new UUID(1L << 32 | 2, 3L << 32 | 4), r.heroesOfTheVillage().iterator().next());

        NbtCompound back = r.save();
        assertEquals("ongoing", back.getString("Status"), "저장은 소문자");
        assertEquals(1234L, back.getLong("TicksActive"));
        assertEquals(96.5F, back.getFloat("TotalHealth"));
        assertArrayEquals(new int[] {1, 2, 3, 4}, (int[]) back.getList("HeroesOfTheVillage").get(0));
        Raid again = new Raid(level, back);
        assertEquals(r.center(), again.center());
        assertEquals(r.groupsSpawned(), again.groupsSpawned());
        assertEquals(Raid.Status.ONGOING, new Raid(level, raidTag(1, "bogus")).status(), "모르는 값은 ONGOING");
    }

    @Test
    void storeWritesFileAndDistributesByCenterChunk(@TempDir Path dir) throws Exception {
        RaidStore store = RaidStore.open(dir);
        assertEquals(2, store.nextId(), "바닐라 NextAvailableID 는 1 에서 ++ — 첫 번호 2");
        var level = new StoreLevel(store);
        Raid r = new Raid(level, raidTag(2, "ongoing"));
        level.raids().all().put(r.id(), r);
        level.raids().publishSnapshots();
        store.saveIfDirty();

        Path f = dir.resolve("data").resolve("raids.dat");
        assertTrue(Files.isRegularFile(f));
        NbtCompound root;
        try (var in = Files.newInputStream(f)) { root = NbtIo.readGzip(in); }
        NbtCompound data = root.getCompound("data");
        assertEquals(2, data.getIntOr0("NextAvailableID"));
        assertEquals(1, data.getList("Raids").size());
        assertTrue(root.containsNumber("DataVersion"));

        RaidStore reopened = RaidStore.open(dir);
        assertEquals(1, reopened.pendingCount());
        assertEquals(3, reopened.nextId(), "번호는 이어진다");
        var level2 = new StoreLevel(reopened);
        level2.raids().claim(0, 0);
        assertNull(level2.raids().get(2), "중심 청크가 아니면 넘기지 않는다");
        level2.raids().claim(40 >> 4, -20 >> 4);
        Raid loaded = level2.raids().get(2);
        assertNotNull(loaded);
        assertEquals(new Pos(40, -60, -20), loaded.center());
        assertEquals(0, reopened.pendingCount());
    }

    @Test
    void raiderRejoinsByRaidIdAfterPark() {
        RaidStore store = RaidStore.detached();
        var level = new StoreLevel(store);
        Raid raid = new Raid(level, raidTag(9, "ongoing"));
        level.raids().all().put(9, raid);
        level.raids().publishSnapshots();

        var p = new Pillager(level, level.nextEntityId());
        p.setPatrolLeader(true);
        raid.joinRaid(2, p, null, true);
        assertSame(raid, p.getCurrentRaid());
        assertEquals(2, p.getWave());
        NbtCompound tag = p.saveWithoutId(new NbtCompound());
        assertEquals(9, tag.getIntOr0("RaidId"));
        assertEquals(2, tag.getIntOr0("Wave"));

        // 중심 청크가 내려가면 보류 — 습격자는 번호만 기억하고 그대로 저장한다
        level.raids().park(40 >> 4, -20 >> 4);
        assertNull(level.raids().get(9));
        assertNull(p.getCurrentRaid());
        assertEquals(9, p.saveWithoutId(new NbtCompound()).getIntOr0("RaidId"));
        assertFalse(p.removeWhenFarAway(1.0E9), "습격 소속(보류 포함)은 사라지지 않는다");

        level.raids().claim(40 >> 4, -20 >> 4);
        Raid back = level.raids().get(9);
        assertNotNull(back);
        var loaded = new Pillager(level, level.nextEntityId());
        loaded.load(tag);
        assertSame(back, loaded.getCurrentRaid(), "RaidId 로 다시 들어간다");
        assertEquals(1, back.getTotalRaidersAlive());
        assertSame(loaded, back.getLeader(2), "지도자는 파도 대장으로");
    }

    @Test
    void raidOmenPositionSaved() {
        var p = new Player(9, UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.setRaidOmenPosition(new Pos(5, 64, -7));
        NbtCompound tag = new NbtCompound();
        p.vitals().save(tag);
        assertArrayEquals(new int[] {5, 64, -7}, (int[]) tag.tags().get("raid_omen_position"));

        var q = new Player(10, UUID.randomUUID(), "Steve", new io.netty.channel.embedded.EmbeddedChannel());
        q.vitals().env(new PlayerVitalsTest.TestEnv());
        q.vitals().load(tag);
        assertEquals(new Pos(5, 64, -7), q.raidOmenPosition());
        q.setRaidOmenPosition(null);
        q.vitals().save(tag);
        assertFalse(tag.contains("raid_omen_position"), "없으면 지운다");
    }

    @Test
    void goalConditions() {
        var level = new StoreLevel(RaidStore.detached());
        var p = new Pillager(level, level.nextEntityId());
        var pathfind = new Raider.PathfindToRaidGoal(p);
        var village = new Raider.RaiderMoveThroughVillageGoal(p, 1.05F, 1);
        var banner = new Raider.ObtainRaidLeaderBannerGoal(p);
        var door = new AbstractIllager.RaiderOpenDoorGoal(p);
        assertFalse(pathfind.canUse(), "습격 없음");
        assertFalse(banner.canUse());
        assertFalse(door.canUse());

        Raid ongoing = new Raid(level, raidTag(3, "ongoing"));
        level.raids().all().put(3, ongoing);
        ongoing.joinRaid(1, p, null, true);
        assertTrue(p.hasActiveRaid());
        assertTrue(pathfind.canUse(), "습격 중이고 마을 밖이면 중심으로");
        assertFalse(village.canUse(), "집 POI 가 없으면 마을 돌기 없음");
        assertFalse(banner.canUse(), "주울 깃발이 없다");
        var hold = new Raider.HoldGroundAttackGoal(p, 10.0F);
        p.setPatrolling(true);
        assertFalse(hold.canUse(), "습격 소속이면 버티기 없음");
        assertFalse(p.canJoinPatrol(), "습격 중이면 순찰 합류 없음");

        // 패배(습격자 승리)한 습격: 축하
        var q = new Pillager(level, level.nextEntityId());
        Raid lost = new Raid(level, raidTag(4, "loss"));
        level.raids().all().put(4, lost);
        lost.joinRaid(1, q, null, true);
        assertTrue(lost.isOver());
        assertFalse(new Raider.PathfindToRaidGoal(q).canUse(), "끝난 습격은 길찾기 없음");
        Goalish celebrate = new Goalish(q);
        assertTrue(celebrate.canUse());
        assertFalse(new Goalish(p).canUse(), "진행 중이면 축하 없음");
    }

    /** RaiderCelebration 은 패키지 전용 생성자 — 같은 패키지에서 감싼다. */
    private static final class Goalish {
        private final Raider.RaiderCelebration g;

        Goalish(Raider r) { g = new Raider.RaiderCelebration(r); }

        boolean canUse() { return g.canUse(); }
    }
}

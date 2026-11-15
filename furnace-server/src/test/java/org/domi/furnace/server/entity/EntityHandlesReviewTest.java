// T-122 엔티티 손잡이 검토 보강 시험(PR #395 독립 검토 R1·R3, 검토자 시험을 옮김). 기대값 출처: Paper 1.21.1 패치(33ff3dff) Cap-Entity-Collisions·Tracking-Range-Improvements,
// Paper ver/1.21.4 TrackingRange.java·ItemEntity.java.patch·CraftEventFactory.doEntityAddEventCalling, 바닐라 AABB 생성자(min/max 정렬).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.config.EntityHandleSettings;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityHandlesReviewTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void back() { EntityHandleSettings.install(EntityHandleSettings.VANILLA); }

    private static EntityHandleSettings handles(double item, double exp, int max, int pl, int an, int mo, int mi, int di, int ot) {
        return new EntityHandleSettings(item, exp, max, true, pl, an, mo, mi, di, ot, false, false, true);
    }

    // ---------------- 추적 거리: 기본값이 96/96/96 이라 종류 → 묶음 배정이 섞여도 기존 시험은 못 잡는다. 서로 다른 값으로 본다.
    private static int range(ChunkLevel level, String type, EntityHandleSettings s) {
        Entity e = Entities.create(level, type);
        return EntityTrackingRange.of(e, e.type().clientTrackingRange() * 16, s);
    }

    @Test
    void trackingGroupsWithDistinctValues() {
        var level = new BlockPlacementTest.TestLevel();
        var s = handles(0.5, -1, 8, 90, 40, 50, 60, 70, 80);
        for (String t : new String[]{"minecraft:cow", "minecraft:cod", "minecraft:squid", "minecraft:villager", "minecraft:bat", "minecraft:axolotl"})
            assertEquals(40, range(level, t, s), t);
        for (String t : new String[]{"minecraft:zombie", "minecraft:pillager", "minecraft:ghast", "minecraft:phantom", "minecraft:slime", "minecraft:ender_dragon"})
            assertEquals(50, range(level, t, s), t);
        for (String t : new String[]{"minecraft:item", "minecraft:experience_orb", "minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame"})
            assertEquals(60, range(level, t, s), t);
        for (String t : new String[]{"minecraft:block_display", "minecraft:item_display", "minecraft:text_display"})
            assertEquals(70, range(level, t, s), t);
        for (String t : new String[]{"minecraft:arrow", "minecraft:armor_stand", "minecraft:boat", "minecraft:tnt", "minecraft:minecart"})
            assertEquals(80, range(level, t, s), t);
        assertEquals(90, s.playerRange());
    }

    // ---------------- 밀기 상한: 플레이어가 미는 쪽(PushScratch.pushCappedBy(Player))
    private static Cow cow(PushEntitiesTest.Arena level, double x) {
        Cow c = new Cow(level, level.nextEntityId());
        c.moveTo(x, -60, 0.5, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    private static Player player(GameMode mode) {
        var p = new Player(7, java.util.UUID.randomUUID(), "Steve", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        return p;
    }

    @Test
    void playerPusherIsCappedAndCountsBothSides() {
        EntityHandleSettings.install(handles(0.5, -1, 8, 128, 96, 96, 96, 128, 64));
        var level = new PushEntitiesTest.Arena();
        List<Cow> cows = new ArrayList<>();
        for (int i = 0; i < 12; i++) cows.add(cow(level, 0.3 + i * 0.03));
        Player p = player(GameMode.SURVIVAL);
        p.numCollisions = 10;   // 이번 틱에 몹에게 열 번 밀렸다 → max(0, 10-8)=2 에서 시작
        level.pushScratch.pushFor(level, p);
        assertEquals(8, p.numCollisions);
        int moved = 0, counted = 0;
        for (Cow c : cows) {
            if (c.vx() != 0) moved++;
            counted += c.numCollisions;
        }
        assertEquals(6, moved, "8 - 2 = 6 마리만 밀린다");
        assertEquals(6, counted);
    }

    @Test
    void spectatorPlayerDoesNotTouchCounter() {
        EntityHandleSettings.install(handles(0.5, -1, 8, 128, 96, 96, 96, 128, 64));
        var level = new PushEntitiesTest.Arena();
        Cow c = cow(level, 0.6);
        Player p = player(GameMode.SPECTATOR);
        p.numCollisions = 5;
        level.pushScratch.pushFor(level, p);
        assertEquals(5, p.numCollisions);
        assertEquals(0, c.numCollisions);
    }

    @Test
    void emptyCandidateListKeepsCounter() {
        // Paper: numCollisions 줄이기는 if (!list.isEmpty()) 안에 있다
        EntityHandleSettings.install(handles(0.5, -1, 8, 128, 96, 96, 96, 128, 64));
        var level = new PushEntitiesTest.Arena();
        Cow a = cow(level, 0.5);
        a.numCollisions = 13;
        a.pushEntities();
        assertEquals(13, a.numCollisions);
    }

    // ---------------- 아이템 합치기: 반경 < 0.375 이면 Spigot 상자 inflate(r, r-0.5, r) 의 y 가 뒤집힌다.
    // 바닐라 AABB 생성자는 min/max 를 정렬하므로 Paper 에서는 겹친 아이템이 합쳐진다(r = 0 이면 높이 0.75 상자).
    private static void tick(ChunkLevel level, int n) {
        for (int i = 0; i < n; i++) {
            level.runScheduledTicks();
            level.tickEntities(k -> true);
        }
    }

    @Test
    void itemMergeRadiusZeroStillMergesOverlappingLikeSpigot() {
        EntityHandleSettings.install(handles(0.0, -1, -1, 128, 96, 96, 96, 128, 64));
        var level = new BlockPlacementTest.TestLevel();
        for (int i = 0; i < 2; i++) {
            assertTrue(level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, B.stack("minecraft:dirt", 1), 0, 0, 0)));
        }
        tick(level, 40);
        assertEquals(1, level.entities().size(), "Spigot·Paper(merge-radius.item=0): 같은 자리 아이템은 정렬된 상자로 합쳐진다");
    }

    // ---------------- 경험치 합치기: count 가 다르면·합이 int 를 넘으면 건너뛴다
    private static void setCount(ExperienceOrb o, int count) throws Exception {
        var f = ExperienceOrb.class.getDeclaredField("count");
        f.setAccessible(true);
        f.setInt(o, count);
    }

    @Test
    void expMergeSkipsOtherCountAndOverflow() throws Exception {
        EntityHandleSettings.install(handles(0.5, 3.0, 8, 128, 96, 96, 96, 128, 64));
        var level = new BlockPlacementTest.TestLevel();
        ExperienceOrb big = new ExperienceOrb(level, level.nextEntityId(), 0.5, -60, 0.5, Integer.MAX_VALUE - 1);
        assertTrue(level.addFreshEntity(big));
        ExperienceOrb two = new ExperienceOrb(level, level.nextEntityId(), 1.0, -60, 0.5, 4);
        setCount(two, 2);
        assertTrue(level.addFreshEntity(two));
        ExperienceOrb fresh = new ExperienceOrb(level, level.nextEntityId(), 1.5, -60, 0.5, 5);
        assertTrue(level.addFreshEntity(fresh));
        assertFalse(big.isRemoved(), "합이 int 를 넘으면 건너뛴다");
        assertFalse(two.isRemoved(), "count 가 다르면 건너뛴다");
        assertEquals(5, fresh.value());
        assertEquals(4, two.value());
    }

    @Test
    void expMergeRadiusZeroIsOff() {
        // Paper: radius > 0 일 때만 합친다
        EntityHandleSettings.install(handles(0.5, 0.0, 8, 128, 96, 96, 96, 128, 64));
        var level = new BlockPlacementTest.TestLevel();
        ExperienceOrb a = new ExperienceOrb(level, level.nextEntityId(), 0.5, -60, 0.5, 3);
        assertTrue(level.addFreshEntity(a));
        ExperienceOrb b = new ExperienceOrb(level, level.nextEntityId(), 0.5, -60, 0.5, 4);
        assertTrue(level.addFreshEntity(b));
        assertFalse(a.isRemoved());
        assertEquals(4, b.value());
    }

    @Test
    void expMergeNotOnCancelledOrRemovedSpawn() {
        EntityHandleSettings.install(handles(0.5, 3.0, 8, 128, 96, 96, 96, 128, 64));
        var level = new BlockPlacementTest.TestLevel();
        ExperienceOrb a = new ExperienceOrb(level, level.nextEntityId(), 0.5, -60, 0.5, 3);
        assertTrue(level.addFreshEntity(a));
        ExperienceOrb b = new ExperienceOrb(level, level.nextEntityId(), 1.0, -60, 0.5, 4);
        b.discard();
        assertFalse(level.addFreshEntity(b));
        assertFalse(a.isRemoved(), "지워진 채 들어온 구슬은 흡수하지 않는다");
        assertEquals(3, a.value());
    }

    // ---------------- T122-1(3bfe2dd54): collisions.skip-unpushable 플레이어 쪽 — Paper 는 isPushable 거짓이면 cramming 굴림 전에 돌아간다
    @Test
    void skipUnpushableSpectatorPlayerDoesNotRoll() {
        for (boolean skip : new boolean[]{false, true}) {
            EntityHandleSettings.install(new EntityHandleSettings(0.5, -1, -1, false, 128, 96, 96, 96, 128, 64, false, skip, true));
            var level = new PushEntitiesTest.Arena();
            level.cramming = 2;
            cow(level, 0.6);
            cow(level, 0.7);
            Player p = player(GameMode.SPECTATOR);
            var rnd = (org.domi.furnace.server.block.LegacyRandomSource) p.random();
            rnd.setSeed(42);
            level.pushScratch.pushFor(level, p);
            long after = rnd.nextLong();
            rnd.setSeed(42);
            long fresh = rnd.nextLong();
            if (skip) assertEquals(fresh, after, "Paper: 밀 수 없는 플레이어는 굴리지 않는다");
            else assertNotEquals(fresh, after, "바닐라: 관전자도 후보가 넘치면 굴린다");
        }
    }
    // ---------------- Paper collisions.allow-player-cramming-damage(기본 false): 굴림은 그대로, 플레이어 피해만 없음
    @Test
    void playerCrammingDamageFollowsPaperKey() {
        for (boolean allow : new boolean[]{true, false}) {
            EntityHandleSettings.install(new EntityHandleSettings(0.5, -1, -1, false, 128, 96, 96, 96, 128, 64, false, false, allow));
            var level = new PushEntitiesTest.Arena();
            level.cramming = 2;
            cow(level, 0.6);
            cow(level, 0.7);
            Player p = player(GameMode.SURVIVAL);
            var dry = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
            for (int i = 0; i < 60; i++) p.vitals().tick(dry);   // 접속 무적 지나기
            var rnd = (org.domi.furnace.server.block.LegacyRandomSource) p.random();
            long seed = 0;
            while (true) {   // 첫 nextInt(4) 가 0 인 씨앗(= cramming 피해 판정에 들어감)
                rnd.setSeed(seed);
                if (rnd.nextInt(4) == 0) break;
                seed++;
            }
            rnd.setSeed(seed);
            float before = p.vitals().health();
            level.pushScratch.pushFor(level, p);
            long after = rnd.nextLong();
            rnd.setSeed(seed);
            rnd.nextInt(4);
            assertEquals(rnd.nextLong(), after, "굴림은 한 번 그대로(Paper 도 isInvulnerableTo 에서만 막는다)");
            if (allow) assertTrue(p.vitals().health() < before, "바닐라: 플레이어도 cramming 피해");
            else assertEquals(before, p.vitals().health(), "Paper 기본: 플레이어는 cramming 피해 없음");
        }
    }
}

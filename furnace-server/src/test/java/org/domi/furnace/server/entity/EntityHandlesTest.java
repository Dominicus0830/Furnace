// 엔티티 손잡이(T-122): Paper 1.21.1 기본값으로 켠 동작과, 바닐라 값(시험 JVM 기본)에서 바닐라와 같음을 확인한다.
// 기대값 출처: Paper 1.21.1 패치(Cap-Entity-Collisions, Tracking-Range-Improvements, Paper-config-files 의 SpigotWorldConfig 기본값)와
// Paper ver/1.21.4 소스(TrackingRange·ItemEntity·CraftEventFactory 경험치 합치기 — 1.21.1 패치와 같은 줄).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.config.EntityHandleSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityHandlesTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void back() { EntityHandleSettings.install(EntityHandleSettings.VANILLA); }

    private static EntityHandleSettings with(double item, double exp, int max) { return with(item, exp, max, false, false); }

    /** 손잡이 값 + Paper 고정 변경 둘(T122-1). 둘을 따로 보는 시험이 아니면 끈다(바닐라). */
    private static EntityHandleSettings with(double item, double exp, int max, boolean intoTicking, boolean skipUnpushable) {
        EntityHandleSettings d = EntityHandleSettings.DEFAULT;
        return new EntityHandleSettings(item, exp, max, d.trackingEnabled(), d.trackPlayers(), d.trackAnimals(), d.trackMonsters(), d.trackMisc(),
                d.trackDisplay(), d.trackOther(), intoTicking, skipUnpushable, true);
    }

    // ------------------------------------------------------------------ 추적 거리

    private static int range(ChunkLevel level, String type, EntityHandleSettings s) {
        Entity e = Entities.create(level, type);
        return EntityTrackingRange.of(e, e.type().clientTrackingRange() * 16, s);
    }

    @Test
    void trackingRangeByKindFollowsPaper() {
        var level = new BlockPlacementTest.TestLevel();
        var p = EntityHandleSettings.DEFAULT;
        // 동물·물 동물·주민 = animals 96, 적대·습격·나는 적대 = monsters 96
        for (String t : new String[]{"minecraft:cow", "minecraft:cod", "minecraft:villager"}) assertEquals(96, range(level, t, p), t);
        for (String t : new String[]{"minecraft:zombie", "minecraft:pillager", "minecraft:ghast"}) assertEquals(96, range(level, t, p), t);
        // 엔더 드래곤은 적대 몹(Enemy)이라 Paper 의 "서버 시야" 예외에 닿기 전에 monsters 로 끝난다
        assertEquals(96, range(level, "minecraft:ender_dragon", p));
        assertEquals(96, range(level, "minecraft:item", p));
        assertEquals(96, range(level, "minecraft:experience_orb", p));
        assertEquals(96, range(level, "minecraft:painting", p));
        assertEquals(128, range(level, "minecraft:block_display", p));
        assertEquals(64, range(level, "minecraft:arrow", p));
        assertEquals(64, range(level, "minecraft:armor_stand", p));
        assertEquals(128, p.playerRange());
    }

    @Test
    void trackingRangeOffIsVanilla() {
        var level = new BlockPlacementTest.TestLevel();
        var v = EntityHandleSettings.VANILLA;
        assertEquals(160, range(level, "minecraft:cow", v));
        assertEquals(64, range(level, "minecraft:cod", v));
        assertEquals(128, range(level, "minecraft:zombie", v));
        assertEquals(160, range(level, "minecraft:ender_dragon", v));
        assertEquals(96, range(level, "minecraft:item", v));
        assertEquals(64, range(level, "minecraft:arrow", v));
        assertEquals(512, v.playerRange());
        // 바닐라 거리 0(추적 안 함)은 켜져 있어도 0
        Entity e = Entities.create(level, "minecraft:cow");
        assertEquals(0, EntityTrackingRange.of(e, 0, EntityHandleSettings.DEFAULT));
    }

    private static Player viewer(double x, double z) {
        Player p = new Player(910_000 + (int) (Math.random() * 1000), UUID.randomUUID(), "V", new org.domi.furnace.server.InlineEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(x, -60, z, 0, 0, true);
        p.viewDistance(32);
        return p;
    }

    private static boolean seen(EntityHandleSettings s, String type, double x) {
        EntityHandleSettings.install(s);
        Player p = viewer(0.5, 0.5);
        var level = new BlockPlacementTest.TestLevel() {
            @Override protected Iterable<Player> trackingPlayers() { return List.of(p); }
        };
        Entity e = Entities.create(level, type);
        e.moveTo(x, -60, 0.5, 0, 0);
        assertTrue(level.addFreshEntity(e));
        return e.tracker != null && e.tracker.isSeenBy(p);
    }

    @Test
    void trackerUsesTheRangeAtTheBoundary() {
        // 좀비: Paper 96, 바닐라 128 블록(시야 32 청크라 시야 제한은 닿지 않는다)
        assertTrue(seen(EntityHandleSettings.DEFAULT, "minecraft:zombie", 96.5));
        assertFalse(seen(EntityHandleSettings.DEFAULT, "minecraft:zombie", 97.5));
        assertTrue(seen(EntityHandleSettings.VANILLA, "minecraft:zombie", 128.5));
        assertFalse(seen(EntityHandleSettings.VANILLA, "minecraft:zombie", 129.5));
        // 물고기: 바닐라 64 → Paper 는 animals 96 이라 더 멀리 보인다
        assertFalse(seen(EntityHandleSettings.VANILLA, "minecraft:cod", 80.5));
        assertTrue(seen(EntityHandleSettings.DEFAULT, "minecraft:cod", 80.5));
    }

    // ------------------------------------------------------------------ 밀기 상한

    private static Cow cow(PushEntitiesTest.Arena level, double x) {
        Cow c = new Cow(level, level.nextEntityId());
        c.moveTo(x, -60, 0.5, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    private static List<Cow> pen(PushEntitiesTest.Arena level, int n) {
        List<Cow> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(cow(level, 0.5 + i * 0.02));
        return l;
    }

    @Test
    void vanillaPushesEveryCandidate() {
        var level = new PushEntitiesTest.Arena();
        List<Cow> cows = pen(level, 20);
        cows.get(0).pushEntities();
        int moved = 0;
        for (Cow c : cows.subList(1, 20)) if (c.vx() != 0) moved++;
        assertEquals(19, moved);
        assertEquals(0, cows.get(0).numCollisions, "상한이 없으면 횟수를 세지 않는다");
    }

    @Test
    void capStopsAfterMaxAndCountsBothSides() {
        EntityHandleSettings.install(with(0.5, -1, 8));
        var level = new PushEntitiesTest.Arena();
        List<Cow> cows = pen(level, 20);
        Cow a = cows.get(0);
        a.pushEntities();
        assertEquals(8, a.numCollisions);
        int moved = 0, counted = 0;
        for (Cow c : cows.subList(1, 20)) {
            if (c.vx() != 0) moved++;
            counted += c.numCollisions;
        }
        assertEquals(8, moved, "상한 8: 후보 여덟만 민다");
        assertEquals(8, counted, "민 상대의 횟수도 하나씩 오른다");
    }

    @Test
    void pushedCountCarriesIntoOwnBudget() {
        EntityHandleSettings.install(with(0.5, -1, 8));
        var level = new PushEntitiesTest.Arena();
        List<Cow> cows = pen(level, 20);
        Cow b = cows.get(19);
        b.numCollisions = 9;   // 이번 틱에 남에게 아홉 번 밀렸다
        b.pushEntities();
        // max(0, 9 - 8) = 1 에서 시작해 8 에 닿을 때까지 일곱만 민다
        assertEquals(8, b.numCollisions);
        int moved = 0;
        for (Cow c : cows.subList(0, 19)) if (c.vx() != 0) moved++;
        assertEquals(7, moved);
    }

    @Test
    void zeroMeansNoPushingButCrammingStays() {
        EntityHandleSettings.install(with(0.5, -1, 0));
        var level = new PushEntitiesTest.Arena();
        List<Cow> cows = pen(level, 5);
        cows.get(0).pushEntities();
        for (Cow c : cows) assertEquals(0.0, c.vx(), 0.0);
    }

    @Test
    void unpushableMobDoesNotPushWhenSkipping() {
        for (boolean skip : new boolean[]{false, true}) {
            EntityHandleSettings.install(with(0.5, -1, -1, false, skip));
            var level = new PushEntitiesTest.Arena();
            List<Cow> cows = pen(level, 3);
            cows.get(0).collides = false;   // isPushable 거짓(사다리에 매달린 것과 같은 갈래)
            cows.get(0).pushEntities();
            int moved = 0;
            for (Cow c : cows.subList(1, 3)) if (c.vx() != 0) moved++;
            assertEquals(skip ? 0 : 2, moved, skip ? "Paper: 밀 수 없으면 밀지 않는다" : "바닐라: 밀 수 없어도 남은 민다");
        }
    }

    // ------------------------------------------------------------------ 합치기 반경

    private static void tick(ChunkLevel level, int n) {
        for (int i = 0; i < n; i++) {
            level.runScheduledTicks();
            level.tickEntities(k -> true);
        }
    }

    private static int itemsAfterMerge(EntityHandleSettings s) {
        EntityHandleSettings.install(s);
        var level = new BlockPlacementTest.TestLevel();
        for (double x : new double[]{0.5, 2.5}) {
            assertTrue(level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), x, -60, 0.5, B.stack("minecraft:dirt", 1), 0, 0, 0)));
        }
        tick(level, 40);
        return level.entities().size();
    }

    @Test
    void itemMergeRadius() {
        assertEquals(2, itemsAfterMerge(EntityHandleSettings.VANILLA), "바닐라: 2칸 떨어진 아이템은 합치지 않는다");
        assertEquals(2, itemsAfterMerge(EntityHandleSettings.DEFAULT), "Paper 기본 0.5 = 바닐라");
        assertEquals(1, itemsAfterMerge(with(2.5, -1, 8)), "Spigot 2.5: 합친다");
    }

    /** 1개(먼저 틱)와 5개(나중)를 겹쳐 둔다: 바닐라는 많은 쪽(나중)에, Spigot·Paper 는 늘 틱 도는 쪽(먼저)에 모은다. */
    @Test
    void itemMergeDirection() {
        for (boolean intoTicking : new boolean[]{false, true}) {
            EntityHandleSettings.install(with(0.5, -1, -1, intoTicking, false));
            var level = new BlockPlacementTest.TestLevel();
            ItemEntity a = new ItemEntity(level, level.nextEntityId(), 0.5, -60, 0.5, B.stack("minecraft:dirt", 1), 0, 0, 0);
            ItemEntity b = new ItemEntity(level, level.nextEntityId(), 0.6, -60, 0.5, B.stack("minecraft:dirt", 5), 0, 0, 0);
            assertTrue(level.addFreshEntity(a));
            assertTrue(level.addFreshEntity(b));
            tick(level, 40);
            ItemEntity kept = intoTicking ? a : b, gone = intoTicking ? b : a;
            assertFalse(kept.isRemoved(), "intoTicking=" + intoTicking);
            assertTrue(gone.isRemoved(), "intoTicking=" + intoTicking);
            assertEquals(6, kept.getItem().count());
        }
    }

    private static List<ExperienceOrb> orbs(EntityHandleSettings s, int... values) {
        EntityHandleSettings.install(s);
        var level = new BlockPlacementTest.TestLevel();
        List<ExperienceOrb> l = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            ExperienceOrb o = new ExperienceOrb(level, level.nextEntityId(), 0.5 + i * 2, -60, 0.5, values[i]);
            assertTrue(level.addFreshEntity(o));
            l.add(o);
        }
        return l;
    }

    @Test
    void expMergeOffByDefault() {
        for (EntityHandleSettings s : new EntityHandleSettings[]{EntityHandleSettings.VANILLA, EntityHandleSettings.DEFAULT}) {
            List<ExperienceOrb> l = orbs(s, 3, 5, 7);
            for (ExperienceOrb o : l) assertFalse(o.isRemoved());
            assertEquals(7, l.get(2).value());
        }
    }

    @Test
    void expMergeAbsorbsIntoTheNewOrb() {
        List<ExperienceOrb> l = orbs(with(0.5, 3.0, 8), 3, 5, 7);
        // 0.5·2.5·4.5 칸: 둘째가 첫째를(2칸 ≤ 3), 셋째가 둘째를 흡수한다
        assertTrue(l.get(0).isRemoved());
        assertTrue(l.get(1).isRemoved());
        assertEquals(15, l.get(2).value());
    }
}

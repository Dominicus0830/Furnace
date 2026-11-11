// A12-01: player 술어 looking_at(바닐라 PlayerPredicate.LookingAtPredicate)과 stats 의 실제 플레이어 값.
// 눈에서 시선 방향 100칸(상자 1.0 키움)에서 관전자 아닌 엔티티 중 가장 가까이 만나는 것, 그리고 hasLineOfSight.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.stats.PlayerStats;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerLookingAtStatsTest {

    @TempDir Path dir;

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(DimensionTestLevel level, double x, double y, double z, float yaw, float pitch) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, yaw, pitch, true);
        level.player = p;
        p.currentLevel = level;
        return p;
    }

    private static <T extends Entity> T put(DimensionTestLevel level, T e, double x, double y, double z) {
        e.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    private static String looked(Player p) {
        return LootEntities.of(p).typeSpecific("looking_at") instanceof LootEntity t ? t.type() : null;
    }

    @Test
    void picksTheNearestEntityOnTheLine() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);   // yaw 0 = +z
        put(level, new Skeleton(level, level.nextEntityId()), 0.5, 100, 10.5);
        put(level, new Zombie(level, level.nextEntityId()), 0.5, 100, 5.5);
        assertEquals("minecraft:zombie", looked(p), "눈높이 선분이 좀비와 스켈레톤을 다 지나면 가까운 좀비");
    }

    @Test
    void missesWhenTheLineMissesAndBeyondOneHundredBlocks() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        put(level, new Zombie(level, level.nextEntityId()), 5.5, 100, 10.5);   // 옆
        put(level, new Skeleton(level, level.nextEntityId()), 0.5, 100, 110.5);   // 100칸 밖
        assertNull(looked(p));
    }

    @Test
    void lookingStraightUpIgnoresEntityBelow() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, -90);   // pitch -90 = 위
        put(level, new Zombie(level, level.nextEntityId()), 0.5, 90, 0.5);
        assertNull(looked(p));
        put(level, new Skeleton(level, level.nextEntityId()), 0.5, 110, 0.5);
        assertEquals("minecraft:skeleton", looked(p));
    }

    @Test
    void entityBehindAWallIsNotSeen() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        put(level, new Zombie(level, level.nextEntityId()), 0.5, 100, 10.5);
        assertEquals("minecraft:zombie", looked(p));
        for (int y = 99; y <= 103; y++) for (int x = -2; x <= 2; x++) level.setBlock(new Pos(x, y, 5), B.defaultState(B.id("minecraft:stone")), 3);
        assertNull(looked(p), "hasLineOfSight: 눈에서 눈까지 충돌 모양에 막히면 맞은 엔티티라도 아니다");
    }

    @Test
    void selfIsExcludedAndSpectatorsAreSkipped() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        assertNull(looked(p), "자기 몸은 안 맞는다");
    }

    @Test
    void dragonPartCountsAsTheDragon() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = new EnderDragon(level, level.nextEntityIds(9));
        d.moveTo(0.5, 100, 8.5, 0.0F, 0.0F);
        d.setNoAi(false);
        level.addFreshEntity(d);
        level.tickEntities(1);
        var p = player(level, 0.5, 100, 40.5, 0, 0);
        EnderDragonPart head = d.subEntities[0];
        double dx = head.x() - p.x(), dy = head.y() + head.height / 2 - p.eyeY(), dz = head.z() - p.z();
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        p.applyMove(0.5, 100, 40.5, yaw, pitch, true);
        var t = LootEntities.of(p).typeSpecific("looking_at");
        assertInstanceOf(LootEntity.class, t, "드래곤(본체나 파트)을 보면 종류는 ender_dragon");
        assertEquals("minecraft:ender_dragon", ((LootEntity) t).type());
    }

    @Test
    void snapshotComputesLookingAtOnceAndKeepsAFrozenCopy() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        var zombie = put(level, new Zombie(level, level.nextEntityId()), 0.5, 100, 5.5);
        boolean before = org.domi.furnace.server.loot.EntityPredicate.lookingAtUsed;
        org.domi.furnace.server.loot.EntityPredicate.lookingAtUsed = true;
        try {
            var snap = KillCredit.snapshot(LootEntities.of(p));
            zombie.moveTo(0.5, 100, 9.5, 0, 0);   // 스냅샷을 처음 묻기 전에 움직여도, 처음 물을 때 한 번 센 값을 계속 돌려준다
            var first = (LootEntity) snap.typeSpecific("looking_at");
            assertEquals(9.5, first.position()[2], 1.0E-9);
            zombie.moveTo(0.5, 100, 6.5, 0, 0);
            assertSame(first, snap.typeSpecific("looking_at"), "한 번만 센다");
            assertEquals(9.5, ((LootEntity) snap.typeSpecific("looking_at")).position()[2], 1.0E-9, "떠 둔 사본이라 살아 있는 엔티티를 안 따라간다");
        } finally {
            org.domi.furnace.server.loot.EntityPredicate.lookingAtUsed = before;
        }
    }

    @Test
    void statValueReadsTheRealCounterAndDefaultsToZero() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        assertEquals(0, LootEntities.of(p).typeSpecific("stat:minecraft:custom|minecraft:jump"), "통계가 안 붙은 플레이어는 0");
        p.stats(new PlayerStats(dir, p.uuid()));
        p.stats().set("minecraft:jump", 3);
        p.stats().increment("minecraft:mined:minecraft:stone", 5);
        assertEquals(3, LootEntities.of(p).typeSpecific("stat:minecraft:custom|minecraft:jump"));
        assertEquals(5, LootEntities.of(p).typeSpecific("stat:minecraft:mined|minecraft:stone"));
        assertEquals(0, LootEntities.of(p).typeSpecific("stat:minecraft:mined|minecraft:dirt"));
    }

    @Test
    void creativeFlyingCountsForIsFlying() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5, 0, 0);
        assertFalse(LootEntities.of(p).isFlying());
        p.setFallFlying(true);
        assertTrue(LootEntities.of(p).isFlying());
    }
}

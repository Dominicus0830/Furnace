// 던지는 아이템(눈덩이·알·엔더 진주·경험치 병·물약·바람 구슬): 시선 방향으로 쏘고, 소모·재사용 대기·크리에이티브 규칙을 따른다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ItemUse;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThrowItemsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);   // 두 번째 이동으로 알려진 이동량을 0 으로
        return p;
    }

    private static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    private static PlayerActor use(BlockPlacementTest.TestLevel level, Player p, ItemStack held, Object[] result) {
        var actor = new PlayerActor(p, held, ItemStack.EMPTY, 0);
        result[0] = ItemUse.useItem(level, actor, 0);
        return actor;
    }

    @Test
    void snowballFliesAlongTheLookDirectionAndConsumesOne() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var out = new Object[1];
        var actor = use(level, p, stack("snowball", 16), out);
        var balls = level.entities().tickSnapshot().stream().filter(e -> e instanceof Snowball).toList();
        assertEquals(1, balls.size());
        Entity b = balls.get(0);
        assertEquals(1.5, Math.sqrt(b.vx() * b.vx() + b.vy() * b.vy() + b.vz() * b.vz()), 0.1, "속도 1.5(흩어짐 약간)");
        assertTrue(b.vz() > 1.3, "yaw 0 은 +z");
        assertEquals(15, actor.itemInHand(0).count());
        assertEquals(p.y() + 1.62F - 0.1F, b.y(), 0.01, "눈높이 - 0.1 에서 시작");
    }

    @Test
    void creativeKeepsTheStack() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.CREATIVE);
        var actor = use(level, p, stack("egg", 16), new Object[1]);
        assertEquals(1L, level.entities().tickSnapshot().stream().filter(e -> e instanceof ThrownEgg).count());
        assertEquals(16, actor.itemInHand(0).count());
    }

    @Test
    void enderPearlHasTwentyTickCooldown() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var held = stack("ender_pearl", 4);
        var actor = use(level, p, held, new Object[1]);
        assertTrue(p.isOnCooldown(held.itemId()));
        long before = level.entities().tickSnapshot().stream().filter(e -> e instanceof ThrownEnderpearl).count();
        assertEquals(1L, before);
        var again = new PlayerActor(p, actor.itemInHand(0), ItemStack.EMPTY, 0);
        ItemUse.useItem(level, again, 0);
        assertEquals(1L, level.entities().tickSnapshot().stream().filter(e -> e instanceof ThrownEnderpearl).count(), "대기 중에는 못 던진다");
        for (int i = 0; i < 20; i++) p.tickCooldowns();
        assertFalse(p.isOnCooldown(held.itemId()), "20틱 뒤 풀린다");
    }

    @Test
    void experienceBottlePotionAndWindChargeAreThrown() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        use(level, p, stack("experience_bottle", 2), new Object[1]);
        use(level, p, stack("splash_potion", 2), new Object[1]);
        use(level, p, stack("wind_charge", 2), new Object[1]);
        var types = level.entities().tickSnapshot().stream().map(e -> e.getClass().getSimpleName()).sorted().toList();
        assertEquals(java.util.List.of("ThrownExperienceBottle", "ThrownPotion", "WindCharge"), types);
        assertTrue(p.isOnCooldown(stack("wind_charge", 1).itemId()), "바람 구슬 10틱 대기");
    }

    @Test
    void movingShooterAddsItsMovementWhenAirborne() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.applyMove(0.5, -59.5, 0.5, 0, 0, false);   // 공중에서 위로 0.5 움직임
        use(level, p, stack("snowball", 2), new Object[1]);
        Entity b = level.entities().tickSnapshot().get(0);
        assertTrue(b.vy() > 0.3, "공중이면 쏜 쪽의 y 속도가 더해진다: " + b.vy());
    }

    @Test
    void enderPearlTeleportsPlayerAndHurtsByFive() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        double[][] target = new double[1][];
        p.teleporter(t -> target[0] = t);
        float before = p.vitals().health();
        use(level, p, stack("ender_pearl", 2), new Object[1]);
        for (int i = 0; i < 200 && target[0] == null; i++) level.tickEntities(k -> true);
        assertNotNull(target[0], "진주가 땅에 맞아 플레이어를 옮긴다");
        assertTrue(target[0][2] > 3.0, "앞쪽(+z)으로 날아갔다: " + target[0][2]);
        assertEquals(before - 5.0F, p.vitals().health(), 0.001F, "낙하 피해 5");
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof ThrownEnderpearl), "진주는 사라진다");
    }

    @Test
    void enderPearlVanishesWhenOwnerIsDead() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        double[][] target = new double[1][];
        p.teleporter(t -> target[0] = t);
        use(level, p, stack("ender_pearl", 2), new Object[1]);
        assertTrue(p.vitals().kill());
        level.tickEntities(k -> true);
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof ThrownEnderpearl), "죽은 주인의 진주는 사라진다(enderPearlsVanishOnDeath)");
        assertNull(target[0]);
    }
}

// M1: 스폰 알을 물에 쓰기(SpawnEggItem.use) — 물 원천을 맞췄을 때만 몹이 나오고 하나 줄며, 마지막에 entity_place 진동(출처 플레이어, 자리는 몹 위치).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnEggWaterTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    record Ev(GameEvent event, Vec3 pos, GameEventContext ctx) {}

    static final class RecLevel extends BlockPlacementTest.TestLevel {
        final List<Ev> events = new ArrayList<>();

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, GameEventContext ctx) {
            events.add(new Ev(event, pos, ctx));
            super.gameEvent(event, pos, ctx);
        }
    }

    private static ItemStack egg(int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:cow_spawn_egg"), n); }

    /** 바로 아래(0,-60,0)의 물 원천을 보는 플레이어(y=-59). */
    private static ItemUseTest.User user() {
        var u = new ItemUseTest.User();
        u.y = -59;
        return u;
    }

    @Test
    void usingEggOnWaterSourceSpawnsMobAndEmitsEntityPlace() {
        var level = new RecLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:water"), 3);
        var u = user();
        u.hands[0] = egg(2);
        level.events.clear();
        var r = ItemUse.useItem(level, u, 0);
        assertEquals(InteractionResult.CONSUME, r);
        assertEquals(1, u.hands[0].count());
        assertTrue(u.stats.contains("minecraft:used:minecraft:cow_spawn_egg"), u.stats.toString());
        var mobs = level.getEntities(null, new org.domi.furnace.server.entity.AABB(-1, -61, -1, 2, -58, 2), e -> true);
        assertEquals(1, mobs.size());
        assertEquals("minecraft:cow", mobs.get(0).type().name());
        var ev = level.events.stream().filter(e -> e.event() == GameEvent.ENTITY_PLACE).toList();
        assertEquals(1, ev.size());
        assertSame(u, ev.get(0).ctx().sourceEntity());
        assertEquals(0.5, ev.get(0).pos().x());
        assertEquals(-60.0, ev.get(0).pos().y());
        assertEquals(0.5, ev.get(0).pos().z());
    }

    @Test
    void creativeKeepsEgg() {
        var level = new RecLevel();
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:water"), 3);
        var u = user();
        u.creative = true;
        u.hands[0] = egg(1);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals(1, u.hands[0].count());
    }

    @Test
    void solidBlockIsPassAndSpawnsNothing() {
        var level = new RecLevel();   // 아래는 풀 블록 — use 는 액체만 받는다(블록에 쓰기는 useOn 이 한다)
        var u = user();
        u.hands[0] = egg(1);
        level.events.clear();
        assertEquals(InteractionResult.PASS, ItemUse.useItem(level, u, 0));
        assertEquals(1, u.hands[0].count());
        assertTrue(level.events.stream().noneMatch(e -> e.event() == GameEvent.ENTITY_PLACE));
    }
}

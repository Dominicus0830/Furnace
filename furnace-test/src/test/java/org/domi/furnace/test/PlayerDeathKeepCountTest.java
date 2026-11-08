// PR #166 검토: 같은 값 스택 두 개 중 하나만 itemsToKeep 으로 옮길 때 개수가 맞는지(복제·손실 없음), 취소 뒤 다시 죽으면 평소대로인지.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.event.player.PlayerDeathEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class PlayerDeathKeepCountTest {

    private static Map<String, Integer> floor(FurnaceTestServer s) {
        var p = s.internals().playerByName("Dana");
        return s.await(s.internals().world().submitAtChunk(p.chunkPos(), r -> {
            Map<String, Integer> out = new TreeMap<>();
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var st = ((ItemEntity) e).getItem();
                out.merge(s.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
            }
            return out;
        }));
    }

    private static Map<String, Integer> inv(FurnaceTestServer s) {
        var p = s.internals().playerByName("Dana");
        Map<String, Integer> out = new TreeMap<>();
        for (int i = 0; i <= 40; i++) {
            var st = p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i));
            if (!st.isEmpty()) out.merge(s.internals().world().itemRegistry().nameOf(st.itemId()) + "@" + i, st.count(), Integer::sum);
        }
        return out;
    }

    private static TestPlayer join(FurnaceTestServer server) {
        TestPlayer tp = server.join("Dana");
        server.tick(2);
        tp.sync();
        var inv = tp.player().inventory();
        var writes = List.of(inv.set(0, new ItemStack("minecraft:dirt", 4)), inv.set(5, new ItemStack("minecraft:dirt", 4)),
                inv.set(39, new ItemStack("minecraft:iron_helmet", 1)));
        for (int i = 0; i < 40 && !writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone); i++) { server.tick(1); tp.sync(); }
        assertTrue(writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone));
        return tp;
    }

    private static void die(FurnaceTestServer s, TestPlayer tp) {
        tp.player().setHealth(0f);
        s.tick(3);
        tp.sync();
        s.tick(3);
    }

    @Test
    void keepOneOfTwoEqualStacksAndHelmet() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, e -> {
                var dirt = e.drops().stream().filter(d -> d.type().equals("minecraft:dirt")).findFirst().orElseThrow();
                e.drops().remove(dirt);
                e.itemsToKeep().add(dirt);
                var helm = e.drops().stream().filter(d -> d.type().equals("minecraft:iron_helmet")).findFirst().orElseThrow();
                e.drops().remove(helm);
                e.itemsToKeep().add(helm);
            });
            var tp = join(server);
            die(server, tp);
            assertEquals(Map.of("minecraft:dirt", 4), floor(server), "남기지 않은 흙 하나만 떨어진다");
            assertEquals(Map.of("minecraft:dirt@0", 4, "minecraft:iron_helmet@39", 1), inv(server), "흙 한 칸·투구는 제자리");
        }
    }

    @Test
    void cancelOnceThenDieNormally() {
        AtomicInteger calls = new AtomicInteger();
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, e -> {
                if (calls.incrementAndGet() == 1) e.setCancelled(true);
            });
            var tp = join(server);
            die(server, tp);
            assertEquals(Map.of(), floor(server));
            assertEquals(3, inv(server).size());
            die(server, tp);
            assertEquals(2, calls.get());
            assertEquals(Map.of("minecraft:dirt", 8, "minecraft:iron_helmet", 1), floor(server));
            assertEquals(Map.of(), inv(server));
        }
    }
}

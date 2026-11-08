// 플레이어 사망 이벤트의 드롭 목록·경험치·유지 여부가 실제 드롭·인벤토리·리스폰에 반영된다(Paper 1.21.1 CraftEventFactory.callPlayerDeathEvent 의 관측 동작).
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.event.player.PlayerDeathEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class PlayerDeathDropsTest {

    private static final ItemStack CURSED = new ItemStack("minecraft:iron_sword", 1,
            Map.of("minecraft:enchantments", "{levels:{\"minecraft:vanishing_curse\":1}}"), java.util.Set.of());

    private record Setup(FurnaceTestServer server, TestPlayer tp) {}

    /** 인벤토리: 0=흙 4, 1=돌 2, 2=소멸 저주 칼, 36=머리 칸(철 투구), 40=왼손(방패). */
    private static Setup join(FurnaceTestServer server) {
        TestPlayer tp = server.join("Dana");
        server.tick(2);
        tp.sync();
        var inv = tp.player().inventory();
        var writes = List.of(inv.set(0, new ItemStack("minecraft:dirt", 4)), inv.set(1, new ItemStack("minecraft:stone", 2)), inv.set(2, CURSED),
                inv.set(36, new ItemStack("minecraft:iron_helmet", 1)), inv.set(40, new ItemStack("minecraft:shield", 1)));
        server.tick(3);   // 쓰기는 소유 region 틱에서 끝난다
        for (int i = 0; i < 40 && !writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone); i++) { server.tick(1); tp.sync(); }
        assertTrue(writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone), "쓰기가 끝나지 않았다: " + writes);
        return new Setup(server, tp);
    }

    private static void die(Setup s) {
        s.tp.player().setHealth(0f);
        s.server.tick(3);
        s.tp.sync();
        s.server.tick(3);
    }

    /** 플레이어 주변 바닥 아이템: 종류 → 개수. */
    private static Map<String, Integer> floor(Setup s) {
        var p = s.server.internals().playerByName("Dana");
        return s.server.await(s.server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            Map<String, Integer> out = new TreeMap<>();
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var st = ((ItemEntity) e).getItem();
                out.merge(s.server.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
            }
            return out;
        }));
    }

    private static int orbs(Setup s) {
        var p = s.server.internals().playerByName("Dana");
        return s.server.await(s.server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            int sum = 0;
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ExperienceOrb)) sum += ((ExperienceOrb) e).value() * Math.max(1, ((ExperienceOrb) e).count());
            return sum;
        }));
    }

    private static Map<String, Integer> invCounts(Setup s) {
        var p = s.server.internals().playerByName("Dana");
        Map<String, Integer> out = new TreeMap<>();
        for (int i = 0; i <= 40; i++) {
            var st = p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i));
            if (!st.isEmpty()) out.merge(s.server.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
        }
        return out;
    }

    private static FurnaceTestServer server(Consumer<FurnaceTestServer> afterStart, Consumer<PlayerDeathEvent> handler) {
        var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start();
        if (handler != null) server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, handler);
        afterStart.accept(server);
        return server;
    }

    @Test
    void withoutListenerEverythingDropsExceptVanishing() {
        try (var server = server(x -> {}, null)) {
            var s = join(server);
            die(s);
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(s));
            assertEquals(Map.of(), invCounts(s), "죽으면 인벤토리를 비운다(소멸 저주 아이템은 사라진다)");
        }
    }

    @Test
    void listenerSeesInventoryDropsInOrderWithoutVanishingItems() {
        List<String> seen = new CopyOnWriteArrayList<>();
        try (var server = server(x -> {}, e -> e.drops().forEach(d -> seen.add(d.type() + "x" + d.amount())))) {
            var s = join(server);
            die(s);
            // Inventory.getContents 순서: 주 인벤토리 → 갑옷 → 왼손
            assertEquals(List.of("minecraft:dirtx4", "minecraft:stonex2", "minecraft:iron_helmetx1", "minecraft:shieldx1"), seen);
        }
    }

    @Test
    void editedDropListIsWhatActuallyDrops() {
        try (var server = server(x -> {}, e -> {
            e.drops().removeIf(d -> d.type().equals("minecraft:stone"));
            e.drops().replaceAll(d -> d.type().equals("minecraft:dirt") ? d.withAmount(1) : d);
            e.drops().add(new ItemStack("minecraft:diamond", 3));
        })) {
            var s = join(server);
            die(s);
            assertEquals(Map.of("minecraft:dirt", 1, "minecraft:diamond", 3, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(s));
            assertEquals(Map.of(), invCounts(s), "keepInventory 가 false 면 목록과 상관없이 인벤토리를 비운다(돌은 사라진다)");
        }
    }

    @Test
    void keepInventoryWithClearedDropsKeepsEverythingAndDropsNothing() {
        try (var server = server(x -> {}, e -> {
            e.keepInventory(true);
            e.drops().clear();
        })) {
            var s = join(server);
            die(s);
            assertEquals(Map.of(), floor(s));
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:stone", 2, "minecraft:iron_sword", 1, "minecraft:iron_helmet", 1, "minecraft:shield", 1), invCounts(s));
        }
    }

    @Test
    void keepInventoryAloneStillDropsTheListLikePaper() {
        // Paper: 드롭 목록과 keepInventory 는 독립이다 — keepInventory(true) 만 켜고 목록을 안 비우면 떨어지고 인벤토리에도 남는다
        try (var server = server(x -> {}, e -> e.keepInventory(true))) {
            var s = join(server);
            die(s);
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(s));
            assertEquals(5, invCounts(s).size(), "인벤토리도 그대로");
        }
    }

    @Test
    void gameRuleKeepInventoryStartsWithEmptyDropsAndKeepFlags() {
        List<String> seen = new CopyOnWriteArrayList<>();
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).gameRule("keepInventory", "true").start()) {
            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class,
                    e -> seen.add(e.drops().size() + "," + e.keepInventory() + "," + e.keepLevel() + "," + e.droppedExp()));
            var s = join(server);
            die(s);
            assertEquals(List.of("0,true,true,0"), seen);
            assertEquals(Map.of(), floor(s));
            assertEquals(5, invCounts(s).size());
        }
    }

    @Test
    void droppedExpDefaultsToLevelTimesSevenAndCanBeChanged() {
        List<Integer> defaults = new CopyOnWriteArrayList<>();
        try (var server = server(x -> {}, e -> defaults.add(e.droppedExp()))) {
            var s = join(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(s);
            assertEquals(List.of(35), defaults);
            assertEquals(35, orbs(s));
        }
        try (var server = server(x -> {}, e -> e.droppedExp(0))) {
            var s = join(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(s);
            assertEquals(0, orbs(s), "droppedExp 0 → 구슬 없음");
        }
        try (var server = server(x -> {}, e -> e.droppedExp(17))) {
            var s = join(server);
            die(s);
            assertEquals(17, orbs(s), "레벨 0 이어도 플러그인이 정한 양만큼");
        }
    }

    @Test
    void newLevelAndNewExpApplyOnRespawnWhenKeepLevelIsOff() {
        try (var server = server(x -> {}, e -> {
            e.newLevel(3);
            e.newTotalExp(30);
            e.newExp(4);
        })) {
            var s = join(server);
            server.execute("xp set Dana 10 levels");
            server.tick(2);
            die(s);
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(3, p.vitals().xpLevel());
            assertEquals(34, p.vitals().xpTotal(), "총량 30 에 newExp 4 를 더한다");
        }
    }

    @Test
    void keepLevelKeepsExperienceThroughRespawn() {
        try (var server = server(x -> {}, e -> {
            e.keepLevel(true);
            e.newLevel(1);
        })) {
            var s = join(server);
            server.execute("xp set Dana 10 levels");
            server.tick(2);
            die(s);
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(10, p.vitals().xpLevel(), "keepLevel 이면 newLevel 은 쓰지 않는다");
        }
    }

    @Test
    void deathDoesNotFirePlayerDropItemEvent() {
        List<String> drops = new CopyOnWriteArrayList<>();
        try (var server = server(x -> {}, null)) {
            server.api().events().subscribe(server.plugin(CounterPlugin.class), org.domi.furnace.api.event.player.PlayerDropItemEvent.class, e -> {
                drops.add(e.item().type());
                e.setCancelled(true);
            });
            var s = join(server);
            die(s);
            assertTrue(drops.isEmpty(), "사망 드롭은 PlayerDropItemEvent 를 부르지 않는다(Paper): " + drops);
            assertEquals(4, floor(s).size());
        }
    }

    @Test
    void unknownItemInListIsSkippedWithoutBreakingDeath() {
        try (var server = server(x -> {}, e -> {
            e.drops().clear();
            e.drops().add(new ItemStack("nope:thing", 1));
            e.drops().add(new ItemStack("minecraft:emerald", 2));
            e.drops().add(ItemStack.EMPTY);
        })) {
            var s = join(server);
            die(s);
            assertEquals(Map.of("minecraft:emerald", 2), floor(s));
        }
    }

    /** 칸 번호(Inventory 번호) → 종류x개수. */
    private static String slot(Setup s, int i) {
        var p = s.server.internals().playerByName("Dana");
        var st = p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i));
        return st.isEmpty() ? "" : s.server.internals().world().itemRegistry().nameOf(st.itemId()) + "x" + st.count();
    }

    @Test
    void itemsToKeepStaysInItsSlotAndUnmatchedOnesAreAdded() {
        // Paper getItemsToKeep(소울바운드 예): drops 에서 빼서 itemsToKeep 에 넣은 아이템은 그 칸에 남고, 새로 준 아이템은 빈 칸에 들어간다
        try (var server = server(x -> {}, e -> {
            var dirt = e.drops().stream().filter(d -> d.type().equals("minecraft:dirt")).findFirst().orElseThrow();
            e.drops().remove(dirt);
            e.itemsToKeep().add(dirt);
            e.itemsToKeep().add(new ItemStack("minecraft:paper", 1));   // 죽은 자리 쪽지
            e.itemsToKeep().add(new ItemStack("minecraft:stone", 1));   // 개수가 다르면 칸과 맞지 않는다 → 새로 넣는다
        })) {
            var s = join(server);
            die(s);
            assertEquals(Map.of("minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(s));
            assertEquals("minecraft:dirtx4", slot(s, 0), "같은 칸에 남는다");
            assertEquals("minecraft:paperx1", slot(s, 1), "맞는 칸이 없던 것은 첫 빈 칸(주 인벤토리 0..35)에");
            assertEquals("minecraft:stonex1", slot(s, 2));
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:paper", 1, "minecraft:stone", 1), invCounts(s), "나머지(돌 2·투구·방패·소멸 저주 칼)는 비운다");
        }
    }

    @Test
    void itemsToKeepAddsToPartialStackFirstAndIsIgnoredWithKeepInventory() {
        try (var server = server(x -> {}, e -> {
            var dirt = e.drops().stream().filter(d -> d.type().equals("minecraft:dirt")).findFirst().orElseThrow();
            e.drops().remove(dirt);
            e.itemsToKeep().add(dirt);
            e.itemsToKeep().add(new ItemStack("minecraft:dirt", 3));   // 남긴 흙 4 칸에 먼저 채운다(Bukkit addItem)
        })) {
            var s = join(server);
            die(s);
            assertEquals("minecraft:dirtx7", slot(s, 0));
            assertEquals(Map.of("minecraft:dirt", 7), invCounts(s));
        }
        try (var server = server(x -> {}, e -> {
            e.keepInventory(true);
            e.drops().clear();
            e.itemsToKeep().add(new ItemStack("minecraft:paper", 1));
        })) {
            var s = join(server);
            die(s);
            assertEquals(5, invCounts(s).size(), "keepInventory 면 itemsToKeep 는 보지 않는다(Paper: 비우지 않을 때는 processKeep 이 없다)");
            assertTrue(!invCounts(s).containsKey("minecraft:paper"));
        }
    }

    @Test
    void cancelledDeathKeepsPlayerAliveWithInventoryAndNoDrops() {
        try (var server = server(x -> {}, e -> {
            e.setCancelled(true);
            e.reviveHealth(5f);
        })) {
            var s = join(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(s);
            var p = server.internals().playerByName("Dana");
            assertTrue(!p.vitals().isDeadOrDying(), "취소 → 죽지 않는다");
            assertEquals(5f, p.vitals().health(), "reviveHealth");
            assertEquals(Map.of(), floor(s), "드롭 없음");
            assertEquals(0, orbs(s), "경험치 구슬 없음");
            assertEquals(5, invCounts(s).size(), "인벤토리 그대로(소멸 저주 칼 포함)");
            assertEquals(5, p.vitals().xpLevel());
        }
    }

    @Test
    void shouldDropExperienceFalseDropsNoOrbs() {
        try (var server = server(x -> {}, e -> e.shouldDropExperience(false))) {
            var s = join(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(s);
            assertEquals(0, orbs(s));
            assertEquals(4, floor(s).size(), "아이템은 그대로 떨어진다");
        }
    }
}

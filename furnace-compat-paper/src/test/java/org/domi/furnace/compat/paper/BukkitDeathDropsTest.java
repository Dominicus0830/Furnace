package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.fixtures.DeathProbe;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 진짜 Bukkit 플러그인 jar 의 PlayerDeathEvent 리스너가 getDrops()·getDroppedExp()·setKeepInventory·setKeepLevel·getNewLevel 을
 * Paper 1.21.1 처럼 쓸 수 있다(무덤·keep-inventory 류가 죽을 때마다 오류를 내던 자리). 실제 드롭·인벤토리·리스폰 경험치로 확인한다.
 */
class BukkitDeathDropsTest {

    private static final String YML = """
            name: DeathUser
            version: '1'
            main: du.DeathUser
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package du;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.event.*;
            import org.bukkit.event.entity.EntityDeathEvent;
            import org.bukkit.event.entity.PlayerDeathEvent;
            import org.bukkit.inventory.ItemStack;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.DeathProbe;
            public final class DeathUser extends JavaPlugin implements Listener {
                @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
                @EventHandler public void death(PlayerDeathEvent e) {
                    try {
                        StringBuilder sb = new StringBuilder("drops=");
                        for (ItemStack d : e.getDrops()) sb.append(d.getType().name()).append('x').append(d.getAmount()).append(';');
                        DeathProbe.LOG.add(sb + " exp=" + e.getDroppedExp() + " keep=" + e.getKeepInventory() + "," + e.getKeepLevel()
                                + " new=" + e.getNewExp() + "," + e.getNewLevel() + "," + e.getNewTotalExp());
                        switch (DeathProbe.mode) {
                            case "grave" -> {   // 무덤·keep-inventory 류
                                e.setKeepInventory(true);
                                e.getDrops().clear();
                                e.setDroppedExp(0);
                                e.setKeepLevel(true);
                            }
                            case "edit" -> {
                                Iterator<ItemStack> it = e.getDrops().iterator();
                                while (it.hasNext()) { ItemStack d = it.next(); if (d.getType() == Material.SHIELD) it.remove(); }
                                for (ItemStack d : e.getDrops()) if (d.getType() == Material.DIRT) d.setAmount(9);
                                e.getDrops().add(new ItemStack(Material.GOLD_INGOT, 2));
                                e.setDroppedExp(11);
                                e.setNewLevel(2);
                                e.setNewTotalExp(10);
                                e.setNewExp(3);
                            }
                            case "cancel" -> {
                                DeathProbe.LOG.add("revive=" + e.getReviveHealth());
                                e.setCancelled(true);
                                e.setReviveHealth(3);
                            }
                            case "soulbound" -> {   // Paper getItemsToKeep 문서의 예
                                Iterator<ItemStack> it = e.getDrops().iterator();
                                while (it.hasNext()) { ItemStack d = it.next(); if (d.getType() == Material.DIRT) { it.remove(); e.getItemsToKeep().add(d); } }
                                e.setShouldDropExperience(false);
                            }
                            default -> { }
                        }
                    } catch (Throwable t) { DeathProbe.failure = t; }
                }
                @EventHandler public void mobDeath(EntityDeathEvent e) {
                    if (e instanceof PlayerDeathEvent) return;
                    try {
                        StringBuilder sb = new StringBuilder("mob=" + e.getEntityType().name() + " drops=");
                        for (ItemStack d : e.getDrops()) sb.append(d.getType().name()).append('x').append(d.getAmount()).append(';');
                        DeathProbe.LOG.add(sb + " exp=" + e.getDroppedExp() + " revive=" + e.getReviveHealth());
                        switch (DeathProbe.mode) {
                            case "mob-edit" -> {
                                e.getDrops().removeIf(d -> d.getType() == Material.SADDLE);
                                e.getDrops().add(new ItemStack(Material.GOLD_INGOT, 2));
                                e.setDroppedExp(5);
                            }
                            case "mob-cancel" -> e.setCancelled(true);
                            default -> { }
                        }
                    } catch (Throwable t) { DeathProbe.failure = t; }
                }
            }
            """;

    private static final ItemStack CURSED = new ItemStack("minecraft:iron_sword", 1,
            Map.of("minecraft:enchantments", "{levels:{\"minecraft:vanishing_curse\":1}}"), java.util.Set.of());

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { DeathProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("du.jar"), YML, Map.of("du.DeathUser", PLUGIN));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static TestPlayer joinWithItems(FurnaceTestServer server) {
        TestPlayer tp = server.join("Dana");
        server.tick(2);
        tp.sync();
        var inv = tp.player().inventory();
        var writes = List.of(inv.set(0, new ItemStack("minecraft:dirt", 4)), inv.set(1, new ItemStack("minecraft:stone", 2)), inv.set(2, CURSED),
                inv.set(36, new ItemStack("minecraft:iron_helmet", 1)), inv.set(40, new ItemStack("minecraft:shield", 1)));
        server.tick(3);
        for (int i = 0; i < 40 && !writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone); i++) { server.tick(1); tp.sync(); }
        assertTrue(writes.stream().allMatch(java.util.concurrent.CompletableFuture::isDone), "쓰기가 끝나지 않았다: " + writes);
        return tp;
    }

    private static void die(FurnaceTestServer server, TestPlayer tp) {
        tp.player().setHealth(0f);
        server.tick(3);
        tp.sync();
        server.tick(3);
    }

    private static Map<String, Integer> floor(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            Map<String, Integer> out = new TreeMap<>();
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var st = ((ItemEntity) e).getItem();
                out.merge(server.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
            }
            return out;
        }));
    }

    private static int orbs(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            int sum = 0;
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ExperienceOrb)) sum += ((ExperienceOrb) e).value() * Math.max(1, ((ExperienceOrb) e).count());
            return sum;
        }));
    }

    private static int inventoryKinds(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        int n = 0;
        for (int i = 0; i <= 40; i++) if (!p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(i)).isEmpty()) n++;
        return n;
    }

    @Test
    void listenerReadsRealDropsExpAndDefaults() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "리스너가 던졌다: " + DeathProbe.failure);
            assertEquals(List.of("drops=DIRTx4;STONEx2;IRON_HELMETx1;SHIELDx1; exp=35 keep=false,false new=0,0,0"), DeathProbe.LOG);
            assertEquals(Map.of("minecraft:dirt", 4, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(server));
            assertEquals(0, inventoryKinds(server));
            assertEquals(35, orbs(server));
        }
    }

    @Test
    void graveStylePluginKeepsItemsWithoutErrors() throws Exception {
        DeathProbe.mode = "grave";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "getDrops().clear() 등이 던지면 안 된다: " + DeathProbe.failure);
            assertEquals(Map.of(), floor(server), "드롭 목록을 비웠으므로 아무것도 안 떨어진다");
            assertEquals(5, inventoryKinds(server), "keepInventory → 인벤토리 그대로");
            assertEquals(0, orbs(server), "setDroppedExp(0)");
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(5, p.vitals().xpLevel(), "setKeepLevel(true) → 레벨 유지");
        }
    }

    @Test
    void editedListAndNewExpFlowBackToTheServer() throws Exception {
        DeathProbe.mode = "edit";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "리스너가 던졌다: " + DeathProbe.failure);
            assertEquals(Map.of("minecraft:dirt", 9, "minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:gold_ingot", 2), floor(server),
                    "방패는 뺐고 흙은 제자리 수정, 금 추가");
            assertEquals(0, inventoryKinds(server), "keepInventory 가 false 라 인벤토리는 비운다");
            assertEquals(11, orbs(server));
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(2, p.vitals().xpLevel());
            assertEquals(13, p.vitals().xpTotal(), "newTotalExp 10 + newExp 3");
        }
    }

    @Test
    void cancellingTheDeathKeepsThePlayerAlive() throws Exception {
        DeathProbe.mode = "cancel";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "setCancelled·setReviveHealth 가 던지면 안 된다: " + DeathProbe.failure);
            assertTrue(DeathProbe.LOG.contains("revive=20.0"), "되살릴 체력 기본 = 최대 체력: " + DeathProbe.LOG);
            var p = server.internals().playerByName("Dana");
            assertTrue(!p.vitals().isDeadOrDying(), "Paper: 취소하면 죽지 않는다");
            assertEquals(3f, p.vitals().health());
            assertEquals(Map.of(), floor(server));
            assertEquals(0, orbs(server));
            assertEquals(5, inventoryKinds(server), "인벤토리 그대로");
        }
    }

    @Test
    void soulboundStylePluginKeepsTheItemInItsSlot() throws Exception {
        DeathProbe.mode = "soulbound";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            server.execute("xp set Dana 5 levels");
            server.tick(2);
            die(server, tp);
            assertNull(DeathProbe.failure, () -> "getItemsToKeep 등이 던지면 안 된다: " + DeathProbe.failure);
            assertEquals(Map.of("minecraft:stone", 2, "minecraft:iron_helmet", 1, "minecraft:shield", 1), floor(server));
            var p = server.internals().playerByName("Dana");
            var slot0 = p.inventory().get(org.domi.furnace.server.entity.PlayerInventory.windowSlot(0));
            assertEquals("minecraft:dirt", server.internals().world().itemRegistry().nameOf(slot0.itemId()));
            assertEquals(4, slot0.count(), "흙은 그 칸에 남는다");
            assertEquals(1, inventoryKinds(server), "나머지는 비운다");
            assertEquals(0, orbs(server), "setShouldDropExperience(false)");
        }
    }

    /** 플레이어 옆에 안장 얹은 돼지를 소환해 /kill 한다. 돌려주는 값: 살아 남은 돼지의 [체력, 안장 여부] (없으면 null). */
    private static Object[] killSaddledPig(FurnaceTestServer server, TestPlayer tp) {
        var p = server.internals().playerByName("Dana");
        server.execute("gamemode spectator Dana");   // 옆의 플레이어가 구슬·아이템을 줍지 않게
        server.tick(2);
        server.execute(String.format(java.util.Locale.ROOT, "summon minecraft:pig %.2f %.2f %.2f {Saddle:1b}", p.x() + 1, p.y(), p.z()));
        server.tick(2);
        server.execute("kill @e[type=minecraft:pig]");
        server.tick(3);
        tp.sync();
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof org.domi.furnace.server.entity.Pig)) {
                var pig = (org.domi.furnace.server.entity.Pig) e;
                if (!pig.isRemoved() && !pig.isDeadOrDying()) return new Object[] {pig.health(), pig.isSaddled()};
            }
            return null;
        }));
    }

    @Test
    void mobDeathDropsCanBeEditedLikePaper() throws Exception {
        DeathProbe.mode = "mob-edit";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            assertNull(killSaddledPig(server, tp), "돼지는 죽는다");
            assertNull(DeathProbe.failure, () -> "몹 getDrops 등이 던지면 안 된다: " + DeathProbe.failure);
            var mobLog = DeathProbe.LOG.stream().filter(x -> x.startsWith("mob=")).toList();
            assertEquals(1, mobLog.size(), DeathProbe.LOG.toString());
            assertTrue(mobLog.get(0).startsWith("mob=PIG drops=") && mobLog.get(0).contains("SADDLEx1;") && mobLog.get(0).endsWith(" exp=0 revive=10.0"), mobLog.toString());
            var f = floor(server);
            assertNull(f.get("minecraft:saddle"), "목록에서 뺀 안장은 안 나온다: " + f);
            assertEquals(2, f.get("minecraft:gold_ingot"), f.toString());
            assertEquals(5, orbs(server), "setDroppedExp(5)");
        }
    }

    @Test
    void cancellingMobDeathKeepsItAlive() throws Exception {
        DeathProbe.mode = "mob-cancel";
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = joinWithItems(server);
            Object[] pig = killSaddledPig(server, tp);
            assertNull(DeathProbe.failure, () -> "몹 setCancelled 가 던지면 안 된다: " + DeathProbe.failure);
            assertTrue(pig != null, "취소 → 돼지는 살아 있다: " + DeathProbe.LOG);
            assertEquals(10f, pig[0], "되살릴 체력 기본 = 최대 체력");
            assertEquals(true, pig[1], "안장 그대로");
            assertEquals(Map.of(), floor(server), "드롭 없음");
        }
    }
}

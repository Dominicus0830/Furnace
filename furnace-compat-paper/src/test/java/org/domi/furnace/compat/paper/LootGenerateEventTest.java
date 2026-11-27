package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.LootProbe;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Paper LootGenerateEvent 포팅 — 자연 상자 채우기(plugin=false)와 Bukkit LootTable.fillInventory(plugin=true), 취소·전리품 바꾸기. */
class LootGenerateEventTest {

    private static final String YML = """
            name: LootEvt
            version: '1'
            main: lt.LootEvt
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package lt;
            import java.util.List;
            import org.bukkit.Material;
            import org.bukkit.event.*;
            import org.bukkit.event.world.LootGenerateEvent;
            import org.bukkit.inventory.ItemStack;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.LootProbe;
            public final class LootEvt extends JavaPlugin implements Listener {
                @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
                @EventHandler public void loot(LootGenerateEvent e) {
                    try {
                        LootProbe.LOG.add("plugin=" + e.isPlugin() + " table=" + e.getLootTable().getKey() + " n=" + e.getLoot().size()
                                + " world=" + e.getWorld().getName() + " luck=" + e.getLootContext().getLuck());
                        if ("cancel".equals(LootProbe.mode)) e.setCancelled(true);
                        if ("replace".equals(LootProbe.mode)) e.setLoot(List.of(new ItemStack(Material.DIAMOND, 64)));
                    } catch (Throwable t) { LootProbe.failure = t; }
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { LootProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path vanilla = Path.of("../tools/vanilla-data/server-" + org.domi.furnace.api.GameVersion.MINECRAFT);
        Assumptions.assumeTrue(Files.isDirectory(vanilla.resolve("data/minecraft/loot_table")), "로컬 바닐라 데이터 없음");
        Path jar = JarCompiler.jar(tmp.resolve("lt.jar"), YML, Map.of("lt.LootEvt", PLUGIN));
        FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        server.join("Looter");
        server.tick(3);
        var ld = org.domi.furnace.server.world.storage.LevelData.create("world", 1234L, true, false, org.domi.furnace.server.world.GameMode.SURVIVAL, false,
                org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of());
        server.internals().dataPacks(DataPackManager.start(vanilla, Files.createTempDirectory("lt-dp"), ld,
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + org.domi.furnace.api.GameVersion.RESOURCE_TAG + ".txt"), true, List.of("vanilla"), List.of()));
        return server;
    }

    private static int count(ItemStack[] items, String item) {
        int n = 0;
        for (var s : items) if (s != null && !s.isEmpty() && s.itemId() == org.domi.furnace.server.item.ItemContext.id("minecraft:item", item)) n += s.count();
        return n;
    }

    private static boolean empty(ItemStack[] items) {
        for (var s : items) if (s != null && !s.isEmpty()) return false;
        return true;
    }

    private ItemStack[] unpack(FurnaceTestServer server, BlockPos at, ChestBlockEntity chest) {
        server.onRegion(at, () -> {
            server.internals().unpackLoot(server.internals().world(), chest, null);
            return null;
        });
        return chest.items;
    }

    @Test
    void naturalChestFillFiresTheEventAndTheLootIsFilled() throws Exception {
        try (FurnaceTestServer server = start()) {
            var at = new BlockPos(3, 70, 3);
            var chest = new ChestBlockEntity(at.x(), at.y(), at.z(), new ItemStack[27]);
            chest.lootTable = "minecraft:chests/simple_dungeon";
            var items = unpack(server, at, chest);
            assertNull(LootProbe.failure);
            assertEquals(1, LootProbe.LOG.size(), LootProbe.LOG.toString());
            assertTrue(LootProbe.LOG.get(0).startsWith("plugin=false table=minecraft:chests/simple_dungeon "), LootProbe.LOG.get(0));
            assertFalse(empty(items), "취소하지 않으면 상자가 채워진다");
        }
    }

    @Test
    void cancelLeavesTheChestEmpty() throws Exception {
        LootProbe.mode = "cancel";
        try (FurnaceTestServer server = start()) {
            var at = new BlockPos(3, 70, 3);
            var chest = new ChestBlockEntity(at.x(), at.y(), at.z(), new ItemStack[27]);
            chest.lootTable = "minecraft:chests/simple_dungeon";
            assertTrue(empty(unpack(server, at, chest)));
            assertNull(chest.lootTable, "바닐라는 취소해도 표 이름은 이미 지운다");
        }
    }

    @Test
    void replacedLootIsWhatGetsFilled() throws Exception {
        LootProbe.mode = "replace";
        try (FurnaceTestServer server = start()) {
            var at = new BlockPos(3, 70, 3);
            var chest = new ChestBlockEntity(at.x(), at.y(), at.z(), new ItemStack[27]);
            chest.lootTable = "minecraft:chests/simple_dungeon";
            var items = unpack(server, at, chest);
            assertEquals(64, count(items, "minecraft:diamond"));
            assertEquals(64, java.util.Arrays.stream(items).filter(s -> s != null && !s.isEmpty()).mapToInt(ItemStack::count).sum(), "바꾼 전리품만");
            assertEquals(1, java.util.Arrays.stream(items).filter(s -> s != null && !s.isEmpty()).count(), "플러그인이 준 묶음은 다시 쪼개지지 않는다");
        }
    }

    @Test
    void bukkitFillInventoryFiresWithPluginTrue() throws Exception {
        try (FurnaceTestServer server = start()) {
            var table = CraftLootTable.ofKey(org.bukkit.NamespacedKey.fromString("minecraft:chests/simple_dungeon"));
            var world = Bukkit.getWorlds().get(0);
            var ctx = new org.bukkit.loot.LootContext.Builder(new org.bukkit.Location(world, 3.5, 70, 3.5)).luck(1.5f).build();
            var inv = Bukkit.createInventory(null, 27);
            LootProbe.mode = "replace";
            table.fillInventory(inv, new java.util.Random(5), ctx);
            assertNull(LootProbe.failure);
            assertEquals(1, LootProbe.LOG.size(), LootProbe.LOG.toString());
            assertTrue(LootProbe.LOG.get(0).startsWith("plugin=true table=minecraft:chests/simple_dungeon "), LootProbe.LOG.get(0));
            // Paper CraftLootTable.convertContext 는 Bukkit 문맥의 luck 을 NMS 쪽에 넘기지 않는다(0 으로 굴림) — 그대로
            assertTrue(LootProbe.LOG.get(0).endsWith("luck=0.0"), LootProbe.LOG.get(0));
            int diamonds = 0;
            for (var s : inv.getContents()) if (s != null && s.getType() == org.bukkit.Material.DIAMOND) diamonds += s.getAmount();
            assertEquals(64, diamonds);
        }
    }
}

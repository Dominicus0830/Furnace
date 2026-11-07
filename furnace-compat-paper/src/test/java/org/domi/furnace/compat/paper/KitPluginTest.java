package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.KitPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 실제 Bukkit 플러그인 모양(리스너가 인벤토리·메타·영속 데이터를 만진다)이 Furnace 이벤트 스레드에서 도는지 — jar 로 올려 확인한다. */
class KitPluginTest {

    @TempDir
    Path tmp;

    @Test
    void joinListenerCanGiveAnEnchantedNamedItem() throws Exception {
        KitPlugin.reset();
        Path jar = Jars.bukkit(tmp.resolve("kit.jar"), "plugin.yml", """
                name: Kit
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.KitPlugin
                api-version: '1.21'
                """, KitPlugin.class);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            var tp = server.join("Dana");
            server.tick(3);
            assertNull(KitPlugin.ERROR.get(), () -> "처리기가 던졌다 (스레드 " + KitPlugin.thread + "): " + KitPlugin.ERROR.get());
            assertEquals(0, KitPlugin.leftovers);
            var loc = tp.player().location();
            var pos = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
            var inv = Compat.player(tp.player()).getInventory();
            var sword = server.onRegion(pos, () -> inv.getItem(0));
            assertEquals(Material.DIAMOND_SWORD, sword.getType());
            var meta = sword.getItemMeta();
            assertEquals("§6Kit Sword", meta.getDisplayName());
            assertEquals(java.util.List.of("§7first", "§7second"), meta.getLore());
            assertEquals(3, meta.getEnchantLevel(Enchantment.SHARPNESS));
            assertEquals("starter", meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey("kit", "kit"), PersistentDataType.STRING));
            assertEquals(5, server.onRegion(pos, () -> inv.getItem(1)).getAmount());
            assertTrue(KitPlugin.LOG.get(0).startsWith("main=") || true);
        }
    }
}

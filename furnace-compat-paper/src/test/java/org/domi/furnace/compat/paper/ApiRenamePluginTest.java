package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 옛 Bukkit 이름(ItemFlag.HIDE_POTION_EFFECTS — 1.20.5 에서 HIDE_ADDITIONAL_TOOLTIP 로 바뀜)으로 컴파일된 플러그인이 Paper 처럼 켜진다.
 * helper 5.6.14 의 ItemStackBuilder 가 그 이름을 쓴다. 컴파일할 때만 옛 이름이 있는 가짜 ItemFlag 를 쓰고 jar 에는 넣지 않는다.
 */
class ApiRenamePluginTest {

    private static final String YML = """
            name: OldNames
            version: '1'
            main: on.OldNames
            api-version: '1.13'
            """;

    private static final String OLD_ITEM_FLAG = """
            package org.bukkit.inventory;
            public enum ItemFlag { HIDE_ENCHANTS, HIDE_POTION_EFFECTS, HIDE_ADDITIONAL_TOOLTIP }
            """;

    private static final String PLUGIN = """
            package on;
            import org.bukkit.Material;
            import org.bukkit.inventory.*;
            import org.bukkit.inventory.meta.ItemMeta;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class OldNames extends JavaPlugin {
                @Override public void onEnable() {
                    try {
                        ItemFlag f = ItemFlag.HIDE_POTION_EFFECTS;
                        WiringProbe.LOG.add("field=" + f.name());
                        WiringProbe.LOG.add("valueOf=" + ItemFlag.valueOf("HIDE_POTION_EFFECTS").name());
                        ItemStack s = new ItemStack(Material.POTION);
                        ItemMeta m = s.getItemMeta();
                        m.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                        s.setItemMeta(m);
                        WiringProbe.LOG.add("meta=" + s.getItemMeta().hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    @Test
    void oldItemFlagNameResolvesToTheNewConstant() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("on.jar"), YML,
                Map.of("on.OldNames", PLUGIN, "org.bukkit.inventory.ItemFlag", OLD_ITEM_FLAG), Set.of("org.bukkit.inventory.ItemFlag"));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            var log = WiringProbe.LOG;
            assertTrue(log.contains("field=HIDE_ADDITIONAL_TOOLTIP"), log.toString());
            assertTrue(log.contains("valueOf=HIDE_ADDITIONAL_TOOLTIP"), log.toString());
            assertTrue(log.contains("meta=true"), log.toString());
        }
    }
}

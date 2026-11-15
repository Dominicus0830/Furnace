package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackSource;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.FeatureFlag;
import org.domi.furnace.api.datapack.DataPacks;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

/**
 * T-141 E4b: Paper Datapack API(FAWE 디버그 붙여넣기의 getEnabledPacks·getName). 목록·켜고 끄기 자체는 서버 DataPacksApiTest 가 본다
 * (시험 서버는 데이터 팩 없이 떠서 목록이 비어 있다). 여기서는 Paper 형으로 옮기는 규칙을 본다.
 */
class BukkitDatapackTest {

    private static DataPacks.Pack pack(String id, DataPacks.Source source, Set<String> features, boolean enabled) {
        return new DataPacks.Pack(id, Text.text(id), Text.text("d"), id.equals("vanilla"), DataPacks.Compatibility.TOO_NEW, features, source, enabled, Text.text(id));
    }

    @Test
    void packViewFollowsPaper() {
        Datapack p = new CompatDatapacks.Pack(pack("file/x", DataPacks.Source.WORLD,
                Set.of("minecraft:bundle", "minecraft:update_1_21", "trade_rebalance", "other:thing"), true));
        assertEquals("file/x", p.getName());
        assertTrue(p.isEnabled());
        assertFalse(p.isRequired());
        assertEquals(Datapack.Compatibility.TOO_NEW, p.getCompatibility());
        assertSame(DatapackSource.WORLD, p.getSource());
        assertEquals("world", p.getSource().toString());
        assertEquals(Set.of(FeatureFlag.BUNDLE, FeatureFlag.TRADE_REBALANCE), p.getRequiredFeatures(), "폐기·모르는 플래그는 빠진다");
        assertThrows(UnsupportedOperationException.class, () -> p.getRequiredFeatures().add(FeatureFlag.VANILLA));

        assertSame(DatapackSource.BUILT_IN, new CompatDatapacks.Pack(pack("vanilla", DataPacks.Source.BUILT_IN, Set.of(), true)).getSource());
        assertEquals("plugin", new CompatDatapacks.Pack(pack("p", DataPacks.Source.PLUGIN, Set.of(), false)).getSource().toString());
        assertEquals("minecraft:bundle", FeatureFlag.BUNDLE.getKey().toString());
        assertEquals("minecraft:update_1_21", FeatureFlag.UPDATE_121.getKey().toString());
    }

    @Test
    void managerOnServerWithoutPacks() {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
            var m = Bukkit.getDatapackManager();
            assertSame(m, Bukkit.getServer().getDatapackManager());
            assertTrue(m.getEnabledPacks().isEmpty());
            assertTrue(m.getPacks().isEmpty());
            assertNull(m.getPack("vanilla"));
            assertThrows(NullPointerException.class, () -> m.getPack(null));
            assertThrows(UnsupportedOperationException.class, () -> m.getEnabledPacks().add(null));
            m.refreshPacks();
        }
    }
}

// PR #500 후속(A13-09): /enchant 성공 메시지의 이름은 인챈트 정의의 description 텍스트이고, 정의가 없을 때만 기본 번역 키(enchantment.<ns>.<path>)다.
package org.domi.furnace.server.command.builtin;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.data.DynamicRegistries;
import org.domi.furnace.server.data.PackResources;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.loot.Enchantment;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnchantFullNameTest {

    @TempDir Path tmp;
    DynamicRegistries installed;

    @AfterEach
    void reset() { DynamicRegistries.install(null); }

    private static Enchantment enchant(String id, int maxLevel) {
        return new Enchantment(id, 1, maxLevel, 1, 10, 51, 10, null, null, null, List.of("mainhand"), 1);
    }

    @Test
    void descriptionTextFromDefinitionIsUsedElseTranslationKey() throws Exception {
        Path def = tmp.resolve("pack/data/test/enchantment/foo.json");
        Files.createDirectories(def.getParent());
        Files.writeString(def, "{\"description\":{\"text\":\"Foo Power\"},\"supported_items\":\"#minecraft:swords\",\"weight\":1,\"max_level\":3,"
                + "\"min_cost\":{\"base\":1,\"per_level_above_first\":10},\"max_cost\":{\"base\":51,\"per_level_above_first\":10},\"anvil_cost\":1,\"slots\":[\"mainhand\"]}");
        var rm = new ResourceManager(List.of(new PackResources.PathPack("pack", tmp.resolve("pack"))), List.of());
        DynamicRegistries.install(DynamicRegistries.load(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"), rm, List.of()));

        // 정의가 있으면 그 description 텍스트(레벨 2 → 레벨 번역 키가 붙는다)
        var named = ItemCommands.fullName(enchant("test:foo", 3), 2);
        assertTrue(named.getString().startsWith("Foo Power"), named.getString());
        // 정의가 없으면 바닐라 기본 번역 키
        var fallback = ItemCommands.fullName(enchant("test:missing", 1), 1);
        assertTrue(fallback.getString().contains("enchantment.test.missing") || fallback.getString().contains("missing"), fallback.getString());
    }
}

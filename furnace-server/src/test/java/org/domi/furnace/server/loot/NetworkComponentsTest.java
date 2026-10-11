// 전리품이 만드는 컴포넌트의 네트워크 인코딩이 바닐라 1.21.1 과 바이트 단위로 같은지.
// 기대값: 바닐라 서버 콘솔 /item replace 로 같은 아이템을 준 뒤 캡처한 container_set_slot 의 아이템 부분.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.Test;

class NetworkComponentsTest {

    private static final NetworkComponents NET = new NetworkComponents(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    private static void check(String item, int expectedId, String expectedHex, Object... patch) {
        LootStack s = new LootStack(item, 1);
        Map<String, Object> p = m(patch);
        for (var e : p.entrySet()) {
            if (e.getValue() == null) s.remove(e.getKey()); else s.patch().put(e.getKey(), e.getValue());
        }
        ItemStack is = NET.toItemStack(s);
        assertEquals(expectedId, is.itemId(), item);
        assertEquals(expectedHex, HexFormat.of().formatHex(is.components()), item);
    }

    @Test
    void encodesLikeVanilla() {
        check("minecraft:iron_sword", 833, "0200032509022003270201",
                "minecraft:damage", 37,
                "minecraft:enchantments", m("levels", m("minecraft:sharpness", 3, "minecraft:unbreaking", 2)));
        check("minecraft:enchanted_book", 1114, "01001701160101",
                "minecraft:stored_enchantments", m("levels", m("minecraft:mending", 1)));
        check("minecraft:potion", 998, "01001f01120000",
                "minecraft:potion_contents", m("potion", "minecraft:strong_slowness"));
        check("minecraft:suspicious_stew", 1193, "010020020e7816a001",
                "minecraft:suspicious_stew_effects", List.of(m("id", "minecraft:blindness", "duration", 120), m("id", "minecraft:saturation")));
        check("minecraft:goat_horn", 1203, "01002804", "minecraft:instrument", "minecraft:feel_goat_horn");
        check("minecraft:ominous_bottle", 1331, "01002904", "minecraft:ominous_bottle_amplifier", 4);
        check("minecraft:map", 1101, "0100060a0800097472616e736c617465001a66696c6c65645f6d61702e6275726965645f747265617375726500",
                "minecraft:item_name", "{\"translate\":\"filled_map.buried_treasure\"}");
        check("minecraft:bee_nest", 1222, "010035010b686f6e65795f6c6576656c0130", "minecraft:block_state", m("honey_level", "0"));
        check("minecraft:iron_chestplate", 865, "02012302010109011b04010c",
                "minecraft:trim", m("material", "minecraft:copper", "pattern", "minecraft:bolt"),
                "minecraft:enchantments", m("levels", m("minecraft:protection", 4)),
                "minecraft:attribute_modifiers", null);
    }
}

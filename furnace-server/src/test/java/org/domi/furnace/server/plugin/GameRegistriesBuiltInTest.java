package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** GameRegistries.builtIn: 바닐라 1.21.1 reports/registries.json 의 protocol_id 순(NMS 호환 레지스트리 shim 의 바탕). */
class GameRegistriesBuiltInTest {

    @Test
    void builtInListsFollowVanillaProtocolOrder() {
        var g = new GameRegistriesImpl();
        var items = g.builtIn("minecraft:item");
        assertEquals(1333, items.size());
        assertEquals("minecraft:air", items.get(0));
        assertEquals("minecraft:stone", items.get(1));
        assertEquals(items, g.builtIn("item"), "이름공간 생략");
        var blocks = g.builtIn("minecraft:block");
        assertEquals(1060, blocks.size());
        assertEquals("minecraft:air", blocks.get(0));
        assertEquals(130, g.builtIn("minecraft:entity_type").size());
        assertEquals(java.util.List.of("minecraft:empty", "minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"),
                g.builtIn("minecraft:fluid"));
        assertTrue(g.builtIn("minecraft:worldgen/biome").isEmpty(), "동기화(데이터 팩) 레지스트리는 내장 목록이 아니다");
        assertTrue(g.builtIn("minecraft:nope").isEmpty());
    }

    /** 태그 보기: 걸린 태그(없으면 바닐라 1.21.1 태그). 태그 안 태그는 풀려 있다. */
    @Test
    void tagsResolveNestedEntries() {
        var g = new GameRegistriesImpl();
        assertTrue(g.tags("minecraft:block").contains("minecraft:logs"));
        var logs = g.tag("block", "logs").orElseThrow();
        assertTrue(logs.contains("minecraft:oak_log"), "#oak_logs 가 풀려 있어야 한다");
        assertTrue(g.isTagged("minecraft:block", "minecraft:logs", "minecraft:oak_log"));
        assertTrue(g.isTagged("block", "mineable/pickaxe", "stone"), "이름공간 생략");
        assertTrue(!g.isTagged("minecraft:block", "minecraft:logs", "minecraft:stone"));
        assertTrue(!g.isTagged("minecraft:block", "minecraft:no_such_tag", "minecraft:oak_log"));
        assertTrue(!g.isTagged("minecraft:block", "minecraft:logs", "minecraft:no_such_block"));
        assertTrue(g.tag("minecraft:block", "minecraft:no_such_tag").isEmpty());
        assertTrue(g.tags("minecraft:sound_event").isEmpty(), "클라에 태그를 보내지 않는 레지스트리");
        assertTrue(g.tag("minecraft:item", "minecraft:swords").orElseThrow().contains("minecraft:diamond_sword"));
    }
}

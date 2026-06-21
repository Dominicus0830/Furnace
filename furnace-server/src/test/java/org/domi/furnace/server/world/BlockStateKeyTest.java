// canonical key 빌더가 oracle canonicalKey와 동일 포맷(정렬·구분자)을 산출하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockStateKeyTest {

    @Test
    void noProps_returnsNameOnly() {
        assertEquals("minecraft:dirt", BlockStateKey.of("minecraft:dirt", null));
        assertEquals("minecraft:dirt", BlockStateKey.of("minecraft:dirt", Map.of()));
    }

    @Test
    void singleProp() {
        assertEquals("minecraft:tall_grass;half=lower",
            BlockStateKey.of("minecraft:tall_grass", Map.of("half", "lower")));
    }

    @Test
    void multiProp_sortedByKeyAscending_regardlessOfInputOrder() {
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("flower_amount", "1");
        reordered.put("facing", "north");
        assertEquals("minecraft:pink_petals;facing=north,flower_amount=1",
            BlockStateKey.of("minecraft:pink_petals", reordered));
    }
}

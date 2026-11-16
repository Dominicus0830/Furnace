// 아이템 레지스트리 id→name 로드 검증
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ItemRegistryTest {

    @Test
    void loadsAndMapsIds() {
        ItemRegistry reg = ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt");
        assertEquals("minecraft:stone", reg.nameOf(1));
        assertEquals("minecraft:air", reg.nameOf(0));
        assertEquals("minecraft:air", reg.nameOf(999999)); // 미지원 → air
    }
}

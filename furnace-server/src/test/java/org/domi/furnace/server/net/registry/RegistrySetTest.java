// 레지스트리 엔트리 목록 로더가 리소스를 파싱하고 id를 인덱싱하는지 검증
package org.domi.furnace.server.net.registry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrySetTest {

    @Test
    void loads_overworld_inDimensionType() {
        RegistrySet set = RegistrySet.load("registries/1.21.1.txt");
        List<String> dims = set.entries("minecraft:dimension_type");
        assertTrue(dims.contains("minecraft:overworld"), dims.toString());
    }

    @Test
    void indexOf_findsEntryPosition() {
        RegistrySet set = RegistrySet.load("registries/1.21.1.txt");
        assertTrue(set.indexOf("minecraft:dimension_type", "minecraft:overworld") >= 0);
        assertEquals(-1, set.indexOf("minecraft:dimension_type", "minecraft:nope"));
    }
}

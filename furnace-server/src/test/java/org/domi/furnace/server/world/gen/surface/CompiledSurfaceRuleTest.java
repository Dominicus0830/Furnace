// 컴파일된 표면 규칙이 예전 해석기와 블록 단위로 같은지 — 여러 바이옴(배드랜드·얼음·산·바다 등)을 거치도록 넓게 흩어진 청크.
package org.domi.furnace.server.world.gen.surface;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class CompiledSurfaceRuleTest {

    @Test
    void compiledRulesMatchInterpreter() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource compiled = NoiseWorldSource.overworld(42L, blocks, registries);
        System.setProperty("furnace.surfaceInterp", "true");
        NoiseWorldSource interp;
        try {
            interp = NoiseWorldSource.overworld(42L, blocks, registries);
        } finally {
            System.clearProperty("furnace.surfaceInterp");
        }
        int checked = 0;
        for (int i = 0; i < 40; i++) {
            int cx = (i * 977) % 600 - 300, cz = (i * 613) % 600 - 300;
            ChunkColumn a = compiled.generate(cx, cz), b = interp.generate(cx, cz);
            for (int y = a.minY(); y <= a.maxY(); y++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        if (a.getBlock(x, y, z) != b.getBlock(x, y, z))
                            fail("청크 " + cx + "," + cz + " (" + x + "," + y + "," + z + ") 가 다르다");
            checked++;
        }
        assertEquals(40, checked);
    }
}

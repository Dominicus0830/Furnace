// 감사 I-1 후속 — 서버 경로(generateShared)의 decoStarts 가 상한을 넘어 정리될 때, 장식 도중인 가변 시작점(사막 신전)이
// 새 객체로 다시 만들어지지 않는지. 상한 0(완성마다 정리)과 기본 상한으로 같은 순서를 만들어 블록·상자 개수가 같아야 한다.
package org.domi.furnace.server.world.gen.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class SharedDecoStartsTrimTest {

    // seed 1234 의 사막 신전 시작 청크(StructurePieceStateIsolationTest 음성 대조에서 상자 4→0 재현된 곳)
    private static final long SEED = 1234L;
    private static final int PX = -154, PZ = 402;

    @Test
    void trimKeepsInProgressMutableStarts() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        Set<Integer> chest = blocks.stateIdsForBlock("minecraft:chest");
        NoiseWorldSource ref = NoiseWorldSource.overworld(SEED, blocks, registries);
        NoiseWorldSource tight = NoiseWorldSource.overworld(SEED, blocks, registries);
        tight.pipeline().decoStartsCap = 0;
        int refChests = 0;
        // 시작점 객체가 장식 도중에 바뀌지 않아야 한다(통째로 clear() 하면 새 객체가 된다)
        Object pyramid = null;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkColumn a = ref.generateWithFeatures(PX + dx, PZ + dz);
                ChunkColumn b = tight.generateWithFeatures(PX + dx, PZ + dz);
                var held = tight.pipeline().decoStartsAt(PX, PZ);
                assertNotNull(held, "장식 도중인 사막 신전 시작점이 정리됐다");
                assertFalse(held.isEmpty());
                if (pyramid == null) pyramid = held.get(0);
                assertSame(pyramid, held.get(0), "사막 신전 시작점이 새 객체로 다시 만들어졌다(가변 상태 초기화)");
                int ca = 0, cb = 0;
                for (int y = a.minY(); y <= a.maxY(); y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            int va = a.getBlock(x, y, z), vb = b.getBlock(x, y, z);
                            if (chest.contains(va)) ca++;
                            if (chest.contains(vb)) cb++;
                            if (va != vb) fail("청크 " + (PX + dx) + "," + (PZ + dz) + " (" + x + "," + y + "," + z + ") 가 decoStarts 정리에 따라 다르다");
                        }
                assertEquals(ca, cb, "상자 개수가 다르다");
                refChests += ca;
            }
        }
        assertTrue(refChests > 0, "사막 신전 상자가 없다 — 시나리오가 상자 경로를 검증하지 못한다");
    }
}

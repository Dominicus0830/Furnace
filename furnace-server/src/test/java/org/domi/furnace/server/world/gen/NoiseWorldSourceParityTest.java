// NoiseWorldSource 전 청크 카테고리(stone/water/lava/air) 바닐라 픽스처 일치 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

class NoiseWorldSourceParityTest {

    private void assertChunkParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int stoneId = reg.defaultStateId("minecraft:stone");
        int waterId = reg.defaultStateId("minecraft:water");
        int lavaId  = reg.defaultStateId("minecraft:lava");
        NoiseWorldSource src = NoiseWorldSource.overworld(seed, reg, registries);
        ChunkColumn col = src.generate(cx, cz);
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/chunk_"+seed+"_"+cx+"_"+cz+".bin")) {
            assertNotNull(in, "fixture 없음"); fx = in.readAllBytes();
        }
        int headerLen = 20;
        int mism = 0; String first = null;
        for (int lx=0; lx<16; lx++) for (int lz=0; lz<16; lz++) for (int y=-64; y<320; y++) {
            int idx = (y+64)*256 + lz*16 + lx;
            byte want = fx[headerLen + idx];
            int got = col.getBlock(lx, y, lz);
            byte gotCat;
            if (got == ChunkSection.AIR) gotCat = 0;
            else if (got == stoneId) gotCat = 1;
            else if (got == waterId) gotCat = 2;
            else if (got == lavaId) gotCat = 3;
            else gotCat = 4;
            if (want != gotCat) { mism++; if (first==null) first = "@local("+lx+","+y+","+lz+") want="+want+" got="+gotCat; }
        }
        assertEquals(0, mism, "seed="+seed+" cx="+cx+" cz="+cz+" mismatches="+mism+" "+first);
    }

    @Test void seed0_spawn() throws Exception { assertChunkParity(0L, 0, 0); }
    @Test void seed0_far() throws Exception { assertChunkParity(0L, 5, 5); }
    @Test void seedB() throws Exception { assertChunkParity(123456789L, 0, 0); }
}

// 순정 서버가 저장한 월드(region)의 블록으로 우리 빛 엔진을 돌려, 순정이 저장한 SkyLight/BlockLight 와 칸 단위로 비교한다(수동 실행).
//   -Dfurnace.light.world=<월드 폴더>   (순정 서버로 만든 월드. 저장소에 넣지 않는다)
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class VanillaLightTest {

    static final class Loaded {
        ChunkColumn col;
        ChunkLight light = new ChunkLight();
        NbtCompound nbt;
    }

    private static long key(int x, int z) { return (long) x << 32 | z & 0xFFFFFFFFL; }

    @Test
    void matchesVanillaSavedLight() throws Exception {
        String world = System.getProperty("furnace.light.world");
        Assumptions.assumeTrue(world != null);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        RegionFileStore store = new RegionFileStore(Path.of(world));
        Map<Long, Loaded> chunks = new HashMap<>();
        for (int cx = -32; cx < 32; cx++) {
            for (int cz = -32; cz < 32; cz++) {
                byte[] b = store.readChunk(cx, cz);
                if (b == null) continue;
                NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(b)));
                if (!"minecraft:full".equals(nbt.tags().get("Status"))) continue;
                Loaded l = new Loaded();
                l.col = codec.fromNbt(nbt);
                l.nbt = nbt;
                chunks.put(key(cx, cz), l);
            }
        }
        System.out.println("LIGHT chunks=" + chunks.size());
        LightWorld lw = new LightWorld() {
            public ChunkLight light(int cx, int cz) { Loaded l = chunks.get(key(cx, cz)); return l == null ? null : l.light; }
            public int state(int x, int y, int z) {
                if (y < -64 || y >= 320) return 12958;
                return chunks.get(key(x >> 4, z >> 4)).col.getBlock(x & 15, y, z & 15);
            }
            public int unloadedState() { return reg.defaultStateId("minecraft:bedrock"); }
            public int minY() { return -64; }
            public int maxY() { return 320; }
        };
        LevelLight engine = new LevelLight(lw, true);
        long t0 = System.nanoTime();
        for (var e : chunks.entrySet()) engine.lightChunk((int) (e.getKey() >> 32), (int) (long) e.getKey());
        System.out.println("LIGHT engine ms=" + (System.nanoTime() - t0) / 1_000_000);

        long compared = 0, skyBad = 0, blockBad = 0, skipped = 0;
        StringBuilder samples = new StringBuilder();
        for (var e : chunks.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            boolean interior = true;
            for (int dx = -2; dx <= 2 && interior; dx++) for (int dz = -2; dz <= 2; dz++) if (!chunks.containsKey(key(cx + dx, cz + dz))) { interior = false; break; }
            if (!interior) { skipped++; continue; }
            NbtList secs = e.getValue().nbt.getList("sections");
            for (int i = 0; i < secs.size(); i++) {
                NbtCompound s = (NbtCompound) secs.get(i);
                int sy = ((Number) s.tags().get("Y")).intValue();
                byte[] vs = s.tags().get("SkyLight") instanceof byte[] a ? a : null;
                byte[] vb = s.tags().get("BlockLight") instanceof byte[] a ? a : null;
                for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                    int x = cx * 16 + lx, y = sy * 16 + ly, z = cz * 16 + lz;
                    if (vs != null) {
                        int v = ChunkLight.get(vs, lx, ly, lz), m = engine.skyLight(x, y, z);
                        compared++;
                        if (v != m) { skyBad++; if (samples.length() < 1500) samples.append("sky ").append(x).append(' ').append(y).append(' ').append(z).append(" vanilla=").append(v).append(" ours=").append(m).append('\n'); }
                    }
                    if (vb != null) {
                        int v = ChunkLight.get(vb, lx, ly, lz), m = engine.blockLight(x, y, z);
                        compared++;
                        if (v != m) { blockBad++; if (samples.length() < 1500) samples.append("block ").append(x).append(' ').append(y).append(' ').append(z).append(" vanilla=").append(v).append(" ours=").append(m).append('\n'); }
                    }
                }
            }
        }
        System.out.println("LIGHT compared=" + compared + " skyBad=" + skyBad + " blockBad=" + blockBad + " skippedChunks=" + skipped);
        System.out.println(samples);
        store.closeAll();
        assertTrue(skyBad == 0 && blockBad == 0, "빛이 순정과 다르다: sky " + skyBad + ", block " + blockBad);
    }
}

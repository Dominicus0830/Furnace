// 저장된 월드(region 파일)의 한 상자 영역 블록 상태를 줄마다 "x y z 상태" 로 쓴다(수동 실행).
//   -Dfurnace.dump.world=<월드 폴더> -Dfurnace.dump.area=x1,y1,z1,x2,y2,z2 -Dfurnace.dump.out=<출력 파일>
// 같은 시나리오를 돌린 순정 서버·Furnace 의 덤프를 diff 해서 블록 동작을 비교한다(tools/block-oracle).
package org.domi.furnace.server.oracle;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class WorldDump {

    @Test
    void dump() throws Exception {
        String world = System.getProperty("furnace.dump.world");
        String area = System.getProperty("furnace.dump.area");
        String out = System.getProperty("furnace.dump.out");
        Assumptions.assumeTrue(world != null && area != null && out != null);
        String[] p = area.split(",");
        int x1 = Integer.parseInt(p[0]), y1 = Integer.parseInt(p[1]), z1 = Integer.parseInt(p[2]);
        int x2 = Integer.parseInt(p[3]), y2 = Integer.parseInt(p[4]), z2 = Integer.parseInt(p[5]);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        RegionFileStore store = new RegionFileStore(Path.of(world));
        Map<Long, ChunkColumn> cache = new HashMap<>();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(out), StandardCharsets.UTF_8))) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                        int cx = x >> 4, cz = z >> 4;
                        long key = (long) cx << 32 | (cz & 0xffffffffL);
                        ChunkColumn col = cache.computeIfAbsent(key, k -> {
                            try {
                                byte[] b = store.readChunk(cx, cz);
                                if (b == null) return null;
                                NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(b)));
                                return codec.fromNbt(nbt);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                        String s = col == null ? "<unloaded>" : reg.canonicalKeyOf(col.getBlock(x & 15, y, z & 15));
                        w.println(x + " " + y + " " + z + " " + s);
                    }
                }
            }
        }
        store.closeAll();
    }
}

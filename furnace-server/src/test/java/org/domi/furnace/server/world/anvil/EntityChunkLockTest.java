// C-5 (F-01 후속): 엔티티·POI 저장소 전체 잠금을 청크별 잠금으로 바꾼 뒤에도 같은 청크 덧붙이기(읽고·쓰기)가 하나도 사라지지 않는지.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityChunkLockTest {

    @TempDir Path tmp;

    @Test
    void concurrentAppendsSameChunks_noneLost() throws Exception {
        AnvilWorldSource src = new AnvilWorldSource(tmp, new RegionFileStore(tmp), null, null);
        src.entityStore(new RegionFileStore(tmp, "entities"));
        int[][] chunks = {{0, 0}, {1, 0}, {31, 31}, {32, 0}, {-1, -1}, {64, 5}};   // 여러 region 파일
        int threads = 8, per = 120;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int th = t;
            fs.add(ex.submit(() -> {
                for (int n = 0; n < per; n++) {
                    int[] c = chunks[(n + th) % chunks.length];
                    synchronized (src.entityLock(c[0], c[1])) {   // World.appendEntitiesAsync 와 같은 읽고·더하고·쓰기
                        NbtCompound root = src.readEntities(c[0], c[1]);
                        if (root == null) root = new NbtCompound();
                        NbtList list = root.tags().get("Entities") instanceof NbtList l ? l : new NbtList(NbtIo.TAG_COMPOUND);
                        NbtCompound e = new NbtCompound();
                        e.putInt("t", th);
                        list.add(e);
                        root.putList("Entities", list);
                        src.writeEntities(c[0], c[1], root);
                    }
                }
                return null;
            }));
        }
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        int total = 0;
        for (int[] c : chunks) {
            NbtCompound root = src.readEntities(c[0], c[1]);
            int n = ((NbtList) root.tags().get("Entities")).size();
            total += n;
        }
        assertEquals(threads * per, total, "덧붙이기 누락 없음");
        for (int i = 0; i < chunks.length; i++) {
            int expected = 0;
            for (int t = 0; t < threads; t++) for (int n = 0; n < per; n++) if ((n + t) % chunks.length == i) expected++;
            assertEquals(expected, ((NbtList) src.readEntities(chunks[i][0], chunks[i][1]).tags().get("Entities")).size(), "청크 " + i);
        }
    }
}

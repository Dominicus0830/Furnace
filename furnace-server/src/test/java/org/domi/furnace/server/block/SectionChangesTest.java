// SectionChanges(4096비트)가 예전 TreeSet<Short> 과 같은 칸 목록·순서를 내는가(무작위 1만 회, addAll 포함 — 한 칸뿐인 필드 경로·같은 칸 거듭 넣기도).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SectionChangesTest {
    @Test
    void sameOrderAsTreeSet() {
        Random r = new Random(273);
        for (int it = 0; it < 10_000; it++) {
            ChunkLevel.SectionChanges a = new ChunkLevel.SectionChanges(), b = new ChunkLevel.SectionChanges();
            TreeSet<Short> ta = new TreeSet<>(), tb = new TreeSet<>();
            int n = r.nextInt(4) == 0 ? r.nextInt(4096) : r.nextInt(8);
            for (int i = 0; i < n; i++) { int l = r.nextInt(4096); a.add(l); ta.add((short) l); if (r.nextInt(5) == 0) a.add(l); }
            int m = r.nextInt(50);
            for (int i = 0; i < m; i++) { int l = r.nextInt(4096); b.add(l); tb.add((short) l); }
            if (r.nextBoolean()) { a.addAll(b); ta.addAll(tb); }
            short[] got = a.toArray();
            assertEquals(ta.size(), got.length);
            assertEquals(ta.size(), a.count);
            int k = 0;
            for (short s : ta) assertEquals(s, got[k++]);
        }
    }
}

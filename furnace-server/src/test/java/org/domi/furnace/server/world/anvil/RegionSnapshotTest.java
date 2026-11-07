// 백업 스냅샷(쓰기 전 복사): 기준점 뒤 첫 쓰기 전에 그 파일의 기준점 상태를 뜨고, 기준점 뒤에 생긴 파일은 넣지 않으며,
// 레인 기준점 전의 쓰기는 기준점 상태로 들어간다. 외부 청크(.mcc)도 함께 뜬다.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionSnapshotTest {

    private static byte[] bytes(String s) { return s.getBytes(java.nio.charset.StandardCharsets.UTF_8); }

    private static String read(Path dir, int cx, int cz) throws Exception {
        RegionFileStore st = new RegionFileStore(dir, ".");
        try {
            byte[] b = st.readChunk(cx, cz);
            return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            st.closeAll();
        }
    }

    @Test
    void writeAfterCut_copiesCutStateFirst(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir, "region");
        store.writeChunk(0, 0, bytes("v1"));
        store.writeChunk(40, 0, bytes("other-v1"));   // 다른 파일 r.1.0
        Path dest = dir.resolve("backup/region");
        var snap = store.beginSnapshot(dest, (rx, rz) -> true);
        store.writeChunk(0, 0, bytes("v2"));          // 기준점 뒤 첫 쓰기 → 먼저 v1 을 뜬다
        store.writeChunk(1, 0, bytes("v2-b"));        // 같은 파일 두 번째 쓰기 — 다시 뜨지 않는다
        assertEquals(1, snap.copiedOnWrite());
        store.writeChunk(64, 0, bytes("new-file"));   // 기준점에 없던 파일 r.2.0 → 백업에 넣지 않는다
        snap.copyRemaining();
        snap.end();
        store.writeChunk(40, 0, bytes("other-v2"));   // 끝난 뒤 쓰기는 복사하지 않는다
        store.closeAll();

        assertEquals("v1", read(dest, 0, 0));
        assertNull(read(dest, 1, 0), "기준점에 없던 청크");
        assertEquals("other-v1", read(dest, 40, 0));
        assertFalse(Files.exists(dest.resolve("r.2.0.mca")), "기준점 뒤 새 파일은 백업에 없다");
        assertEquals("v2", read(dir.resolve("region"), 0, 0), "원본은 최신");
        assertEquals("other-v2", read(dir.resolve("region"), 40, 0));
        assertEquals(2, snap.files());
        assertNull(store.snapshot());
    }

    @Test
    void writesBeforeLaneCut_areInTheBackup(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir, "region");
        store.writeChunk(0, 0, bytes("v1"));
        AtomicBoolean passed = new AtomicBoolean(false);
        var snap = store.beginSnapshot(dir.resolve("b"), (rx, rz) -> passed.get());
        store.writeChunk(0, 0, bytes("v2"));   // 레인 marker 전 — 기준점 저장
        assertEquals(0, snap.copiedOnWrite());
        passed.set(true);
        store.writeChunk(0, 0, bytes("v3"));   // marker 뒤 — 먼저 v2 를 뜬다
        snap.copyRemaining();
        snap.end();
        store.closeAll();
        assertEquals("v2", read(dir.resolve("b"), 0, 0));
        assertEquals(1, snap.copiedOnWrite());
    }

    @Test
    void externalChunk_isCopiedWithItsRegion(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir, "region");
        byte[] big = new byte[1_200_000];   // 압축해도 255 섹터를 넘는다 → c.3.4.mcc
        new java.util.Random(7).nextBytes(big);
        store.writeChunk(3, 4, big);
        assertTrue(Files.exists(dir.resolve("region/c.3.4.mcc")));
        Path dest = dir.resolve("b");
        var snap = store.beginSnapshot(dest, (rx, rz) -> true);
        store.writeChunk(3, 4, bytes("small"));   // 다시 작아져 .mcc 를 지운다 — 그 전에 둘 다 뜬다
        assertFalse(Files.exists(dir.resolve("region/c.3.4.mcc")));
        snap.copyRemaining();
        snap.end();
        store.closeAll();
        assertTrue(Files.exists(dest.resolve("c.3.4.mcc")));
        RegionFileStore back = new RegionFileStore(dir, "b");
        assertArrayEquals(big, back.readChunk(3, 4));
        back.closeAll();
    }

    @Test
    void secondSnapshotWhileActive_isRefused(@TempDir Path dir) {
        RegionFileStore store = new RegionFileStore(dir, "region");
        var s = store.beginSnapshot(dir.resolve("a"), (rx, rz) -> true);
        assertThrows(IllegalStateException.class, () -> store.beginSnapshot(dir.resolve("b"), (rx, rz) -> true));
        s.end();
        store.beginSnapshot(dir.resolve("b"), (rx, rz) -> true).end();
    }
}

// T-012: 저장 빛을 믿지 않을 청크 표시가 파일로 남고(다시 열어도), 다 지우면 파일이 지워진다.
// 지우기는 표시 번호가 그대로일 때만(그 사이 다시 표시된 청크는 남는다). 파일을 못 읽으면 어떤 저장 빛도 믿지 않고, 그 상태를 파일에 남긴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LightStaleChunksTest {

    @Test
    void marks_persistAcrossReopen_andFileIsRemovedWhenEmpty(@TempDir Path dim) {
        Path file = dim.resolve("data").resolve(LightStaleChunks.FILE);
        LightStaleChunks a = LightStaleChunks.open(dim);
        assertEquals(0, a.size());
        a.mark(1, 2);
        a.mark(-5, 7);
        a.mark(-33554432, 33554431);   // 끝값 좌표
        a.saveIfDirty();
        assertTrue(Files.isRegularFile(file));

        LightStaleChunks b = LightStaleChunks.open(dim);
        assertEquals(3, b.size());
        assertFalse(b.trustNone());
        assertTrue(b.contains(1, 2) && b.contains(-5, 7) && b.contains(-33554432, 33554431));
        assertFalse(b.contains(2, 1));
        assertEquals(0, b.markSeq(2, 1));
        for (int[] c : new int[][]{{1, 2}, {-5, 7}, {-33554432, 33554431}}) b.clearIfSeq(c[0], c[1], b.markSeq(c[0], c[1]));
        assertEquals(0, b.size());
        b.saveIfDirty();
        assertFalse(Files.exists(file), "비면 파일을 지운다");
        assertEquals(0, LightStaleChunks.open(dim).size());
    }

    @Test
    void clearIfSeq_keepsAMarkMadeAfterTheSeqWasTaken() {
        LightStaleChunks s = LightStaleChunks.detached();
        s.mark(3, 4);
        long taken = s.markSeq(3, 4);   // 다시 계산할 때 잡은 번호
        s.mark(3, 4);                   // 그 뒤(저장이 끝나기 전) 다시 막힌 감소
        s.clearIfSeq(3, 4, taken);
        assertTrue(s.contains(3, 4), "새 표시는 옛 번호로 지우지 않는다");
        s.clearIfSeq(3, 4, s.markSeq(3, 4));
        assertFalse(s.contains(3, 4));
        s.clearIfSeq(3, 4, 0);          // 지울 표시 없음 — 아무 일 없음
    }

    @Test
    void brokenFile_trustsNoStoredLight_andKeepsThatState(@TempDir Path dim) throws Exception {
        Path file = dim.resolve("data").resolve(LightStaleChunks.FILE);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        LightStaleChunks broken = LightStaleChunks.open(dim);
        assertTrue(broken.trustNone());
        assertTrue(broken.contains(0, 0) && broken.contains(1000, -1000), "어떤 청크의 저장 빛도 믿지 않는다");
        broken.saveIfDirty();
        LightStaleChunks again = LightStaleChunks.open(dim);
        assertTrue(again.trustNone(), "다음 실행도 그대로(깨진 파일을 비운 파일로 덮어 표시를 잃지 않게)");
        assertTrue(again.contains(5, 5));
    }

    @Test
    void detached_neverWrites() {
        LightStaleChunks d = LightStaleChunks.detached();
        d.mark(0, 0);
        d.saveIfDirty();
        assertTrue(d.contains(0, 0));
    }
}

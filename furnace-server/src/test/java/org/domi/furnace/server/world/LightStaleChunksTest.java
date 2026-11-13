// T-012: 저장 빛을 믿지 않을 청크 표시가 파일로 남고(다시 열어도), 다 꺼내면 파일이 지워진다.
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
        assertTrue(b.contains(1, 2) && b.contains(-5, 7) && b.contains(-33554432, 33554431));
        assertFalse(b.contains(2, 1));
        assertTrue(b.take(1, 2));
        assertFalse(b.take(1, 2), "한 번만 꺼낸다");
        b.take(-5, 7);
        b.take(-33554432, 33554431);
        b.saveIfDirty();
        assertFalse(Files.exists(file), "비면 파일을 지운다");
        assertEquals(0, LightStaleChunks.open(dim).size());
    }

    @Test
    void brokenFile_isEmpty_andDetachedNeverWrites(@TempDir Path dim) throws Exception {
        Files.createDirectories(dim.resolve("data"));
        Files.write(dim.resolve("data").resolve(LightStaleChunks.FILE), new byte[]{1, 2, 3});
        assertEquals(0, LightStaleChunks.open(dim).size());
        LightStaleChunks d = LightStaleChunks.detached();
        d.mark(0, 0);
        d.saveIfDirty();
        assertTrue(d.contains(0, 0));
    }
}

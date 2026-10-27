package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashReportTest {

    @Test
    void writesVanillaStyleReport(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("crash-reports");
        Path f = CrashReport.write(dir, "Exception in server tick loop", new IllegalStateException("boom"), Thread.currentThread());
        assertNotNull(f);
        assertTrue(f.getFileName().toString().matches("crash-\\d{4}-\\d\\d-\\d\\d_\\d\\d\\.\\d\\d\\.\\d\\d-server(-\\d+)?\\.txt"));
        String s = Files.readString(f);
        assertTrue(s.startsWith("---- Minecraft Crash Report ----\n"));
        assertTrue(s.contains("Description: Exception in server tick loop"));
        assertTrue(s.contains("java.lang.IllegalStateException: boom"));
        assertTrue(s.contains("-- System Details --"));
        // 같은 초에 두 번 써도 덮어쓰지 않는다
        Path g = CrashReport.write(dir, "again", new RuntimeException("x"), null);
        assertNotEquals(f, g);
        try (var files = Files.list(dir)) {
            assertEquals(2, files.count());
        }
    }
}

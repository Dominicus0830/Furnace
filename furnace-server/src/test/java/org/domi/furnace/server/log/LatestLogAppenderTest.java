// latest.log 보관 파일 이름이 바닐라 log4j2 패턴(yyyy-MM-dd-N.log.gz, N 증가)과 같은지.
package org.domi.furnace.server.log;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatestLogAppenderTest {

    @Test
    void archiveIncrementsIndex(@TempDir Path dir) throws Exception {
        Path latest = dir.resolve("latest.log");
        LocalDate day = LocalDate.of(2026, 10, 9);
        Files.writeString(latest, "first");
        assertEquals("2026-10-09-1.log.gz", LatestLogAppender.archive(latest, day).getFileName().toString());
        assertFalse(Files.exists(latest));
        Files.writeString(latest, "second");
        Path second = LatestLogAppender.archive(latest, day);
        assertEquals("2026-10-09-2.log.gz", second.getFileName().toString());
        try (var in = new GZIPInputStream(Files.newInputStream(second))) {
            assertEquals("second", new String(in.readAllBytes()));
        }
    }

    @Test
    void backToBackPrepareKeepsDistinctIndexesAndRecompressMatches(@TempDir Path dir) throws Exception {
        Path latest = dir.resolve("latest.log");
        LocalDate day = LocalDate.of(2026, 10, 9);
        Files.writeString(latest, "a");
        Path p1 = LatestLogAppender.prepare(latest, day);
        Files.writeString(latest, "b");
        Path p2 = LatestLogAppender.prepare(latest, day);
        assertEquals("2026-10-09-1.log.gz.pending", p1.getFileName().toString());
        assertEquals("2026-10-09-2.log.gz.pending", p2.getFileName().toString());
        Path t2 = LatestLogAppender.compress(p2);
        Path t1 = LatestLogAppender.compress(p1);
        try (var in = new GZIPInputStream(Files.newInputStream(t1))) {
            assertEquals("a", new String(in.readAllBytes()));
        }
        assertFalse(Files.exists(p1));
        assertTrue(Files.exists(t2));
    }
}

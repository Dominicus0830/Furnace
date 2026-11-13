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

    private static final ch.qos.logback.classic.LoggerContext CTX = new ch.qos.logback.classic.LoggerContext();

    static {
        CTX.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
    }

    private static ch.qos.logback.classic.spi.ILoggingEvent event(String msg, LocalDate day) {
        var e = new ch.qos.logback.classic.spi.LoggingEvent("t", CTX.getLogger("t"), ch.qos.logback.classic.Level.INFO, msg, null, null);
        e.setTimeStamp(day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return e;
    }

    private static LatestLogAppender appender(Path file) {
        var a = new LatestLogAppender();
        a.setContext(CTX);
        a.setFile(file.toString());
        var enc = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        enc.setContext(a.getContext());
        enc.setPattern("%msg%n");
        enc.start();
        a.setEncoder(enc);
        return a;
    }

    @Test
    void rolloverThroughAppenderCompressesAfterStop(@TempDir Path dir) throws Exception {
        Path latest = dir.resolve("latest.log");
        var a = appender(latest);
        a.start();
        LocalDate today = LocalDate.now();
        a.doAppend(event("today", today));
        a.doAppend(event("next", today.plusDays(1)));
        a.doAppend(event("late", today)); // 늦게 온 전날 이벤트는 롤오버를 되돌리지 않는다
        a.stop();
        Path gz = dir.resolve(today + "-1.log.gz");
        assertTrue(Files.exists(gz));
        try (var in = new GZIPInputStream(Files.newInputStream(gz))) {
            assertEquals("today\n", new String(in.readAllBytes()));
        }
        assertFalse(Files.exists(dir.resolve(today + "-2.log.gz")));
        String cur = Files.readString(latest);
        assertTrue(cur.contains("next") && cur.contains("late"));
    }

    @Test
    void brokenPendingDoesNotBlockLatestArchive(@TempDir Path dir) throws Exception {
        // 압축할 수 없는 .pending(디렉터리)가 있어도 이전 latest.log 는 보관된다
        Files.createDirectory(dir.resolve("2026-01-01-1.log.gz.pending"));
        Path latest = dir.resolve("latest.log");
        Files.writeString(latest, "prev-run");
        var a = appender(latest);
        a.start();
        a.stop();
        boolean archived;
        try (var s = Files.list(dir)) {
            archived = s.anyMatch(f -> f.getFileName().toString().matches("\\d{4}-\\d\\d-\\d\\d-\\d+\\.log\\.gz"));
        }
        assertTrue(archived);
    }
}

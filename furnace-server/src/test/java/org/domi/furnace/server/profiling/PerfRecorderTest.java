// /perf: 시작·멈춤·zip 보고서(바닐라 PerfCommand 의 흐름).
package org.domi.furnace.server.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerfRecorderTest {

    @TempDir Path tmp;

    @Test
    void startStop_writesZipReportAndReportsTicks() throws Exception {
        AtomicLong ticks = new AtomicLong(), nanos = new AtomicLong();
        PerfRecorder rec = new PerfRecorder(PerfRecorder.simple(ticks::get, nanos::get, "world"), tmp);
        AtomicReference<PerfRecorder.Result> result = new AtomicReference<>();
        AtomicReference<Path> report = new AtomicReference<>();
        assertFalse(rec.isRecording());
        assertTrue(rec.start(result::set, report::set, e -> { throw new AssertionError(e); }));
        assertTrue(rec.isRecording());
        assertFalse(rec.start(r -> { }, p -> { }, e -> { }), "이미 시작했으면 두 번째 시작은 거절");
        ticks.addAndGet(37);
        nanos.addAndGet(37 * 5_000_000L);
        Thread.sleep(250);   // 표본 몇 개
        rec.stop();
        assertFalse(rec.isRecording());
        rec.stop();   // 두 번 멈춰도 한 번만 알린다

        assertEquals(37, result.get().ticks());
        assertTrue(result.get().seconds() >= 0.2);
        Path zip = report.get();
        assertNotNull(zip);
        assertTrue(zip.startsWith(tmp.resolve("debug/profiling")), zip.toString());
        assertTrue(zip.getFileName().toString().endsWith("-world-1.21.1.zip"), zip.getFileName().toString());
        Set<String> names = new HashSet<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            for (var en = z.entries(); en.hasMoreElements(); ) names.add(en.nextElement().getName());
            ZipEntry summary = z.getEntry("profiling.txt");
            String text = new String(z.getInputStream(summary).readAllBytes());
            assertTrue(text.contains("Global ticks: 37"), text);
            assertTrue(text.contains("Global tick average: 5.000 ms"), text);
            String csv = new String(z.getInputStream(z.getEntry("metrics/ticking.csv")).readAllBytes());
            assertTrue(csv.lines().count() >= 3, csv);
        }
        assertEquals(Set.of("system.txt", "profiling.txt", "metrics/ticking.csv", "metrics/jvm.csv"), names);

        // 다시 시작할 수 있다. 같은 초에 만들면 이름 뒤에 _1 이 붙는다(바닐라 findAvailableName)
        AtomicReference<Path> second = new AtomicReference<>();
        assertTrue(rec.start(r -> { }, second::set, e -> { throw new AssertionError(e); }));
        rec.stop();
        assertNotNull(second.get());
        assertFalse(second.get().equals(zip));
        rec.shutdown();
    }

    @Test
    void findAvailable_appendsCounter() throws Exception {
        Files.createDirectories(tmp);
        Path first = PerfRecorder.findAvailable(tmp, "x");
        assertEquals("x.zip", first.getFileName().toString());
        Files.writeString(first, "a");
        assertEquals("x_1.zip", PerfRecorder.findAvailable(tmp, "x").getFileName().toString());
    }

    @Test
    void reportFailure_isReported() throws Exception {
        Path notADir = tmp.resolve("file");
        Files.writeString(notADir, "x");   // baseDir/debug 를 만들 수 없다
        PerfRecorder rec = new PerfRecorder(PerfRecorder.simple(() -> 0, () -> 0, "w"), notADir);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicReference<PerfRecorder.Result> result = new AtomicReference<>();
        rec.start(result::set, p -> { throw new AssertionError(); }, failed::set);
        rec.stop();
        assertNotNull(result.get(), "요약은 보고서 실패와 상관없이 알린다");
        assertNotNull(failed.get());
        rec.shutdown();
    }
}

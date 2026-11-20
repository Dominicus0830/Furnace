// 백업 zip 의 파일 압축 방식(backup.compression): STORED·FAST 모두 표준 zip 으로 다시 읽혀야 한다.
package org.domi.furnace.server.maintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class BackupZipTest {

    private static byte[] bytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static Path tree(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("world/region"));
        Files.write(dir.resolve("world/region/r.0.0.mca"), bytes(300_000, 1));          // 버퍼(64KiB) 여러 개를 넘는 크기
        Files.write(dir.resolve("world/level.dat"), bytes(10, 2));
        Files.write(dir.resolve("world/empty.dat"), new byte[0]);                        // 빈 파일도 STORED 로 들어가야 한다
        Files.writeString(dir.resolve("world/text.txt"), "가나다".repeat(5000));          // 잘 줄어드는 내용
        return dir;
    }

    @Test
    void stored_roundTripsEveryFile_withoutRecompressing(@TempDir Path tmp) throws Exception {
        Path src = tree(tmp.resolve("src"));
        Path zip = tmp.resolve("stored.zip");
        WorldBackup.zip(src, zip, MaintenanceOptions.BackupCompression.STORED);
        try (var z = new ZipFile(zip.toFile())) {
            for (String n : new String[]{"world/region/r.0.0.mca", "world/level.dat", "world/empty.dat", "world/text.txt"}) {
                ZipEntry e = z.getEntry(n);
                assertNotNull(e, n);
                assertEquals(ZipEntry.STORED, e.getMethod(), n);
                assertEquals(e.getSize(), e.getCompressedSize(), n);
                try (var in = z.getInputStream(e)) {
                    assertArrayEquals(Files.readAllBytes(src.resolve(n)), in.readAllBytes(), n);   // CRC 도 읽을 때 검사된다
                }
            }
        }
    }

    @Test
    void fast_roundTripsAndShrinksCompressibleFiles(@TempDir Path tmp) throws Exception {
        Path src = tree(tmp.resolve("src"));
        Path zip = tmp.resolve("fast.zip");
        WorldBackup.zip(src, zip, MaintenanceOptions.BackupCompression.FAST);
        try (var z = new ZipFile(zip.toFile())) {
            ZipEntry text = z.getEntry("world/text.txt");
            assertEquals(ZipEntry.DEFLATED, text.getMethod());
            assertTrue(text.getCompressedSize() < text.getSize() / 4);
            try (var in = z.getInputStream(z.getEntry("world/region/r.0.0.mca"))) {
                assertArrayEquals(Files.readAllBytes(src.resolve("world/region/r.0.0.mca")), in.readAllBytes());
            }
        }
        Path stored = tmp.resolve("stored.zip");
        WorldBackup.zip(src, stored, MaintenanceOptions.BackupCompression.STORED);
        assertTrue(Files.size(stored) > Files.size(zip), "STORED 는 zip 이 더 크다(결정 D3 의 대가)");
    }
}

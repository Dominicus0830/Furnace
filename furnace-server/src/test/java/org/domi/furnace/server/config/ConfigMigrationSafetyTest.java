// PR #234 독립 검토 R1~R3: 설정 형식 마이그레이션이 기동을 멈추거나, 새 파일에서 괜히 돌지 않는다.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMigrationSafetyTest {

    @TempDir Path dir;

    /** R1: 쓸 수 없는 furnace.properties(읽기 전용 마운트 등)면 ensure() 처럼 경고만 하고 계속한다. */
    @Test
    void readOnlyV1FileMakesMigrationThrow() throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "plugins.compat.nms.spigot-mappings=off\nautosave-ticks=100\n");
        lockFile(f);
        try {
            List<String> warns = new ArrayList<>();
            // 고친 뒤 기대: 예외 없이 경고만 하고 null(또는 바꾸지 못했다는 결과)을 돌려준다
            assertNull(assertDoesNotThrow(() -> ConfigMigration.run(f, warns::add), "읽기 전용 설정 파일 때문에 서버가 켜지지 않는다"));
            assertEquals(1, warns.size(), warns.toString());
            try (var files = Files.list(dir)) {
                assertEquals(List.of(), files.filter(p -> p.getFileName().toString().contains(".bak")).toList(), "실패한 마이그레이션의 백업은 남기지 않는다");
            }
        } finally {
            unlockFile(f);
        }
    }

    /** R2: 새로 만든 furnace.properties 에 config-version 줄은 한 번. */
    @Test
    void freshFileWritesConfigVersionOnce() throws Exception {
        Path f = dir.resolve("furnace.properties");
        FurnacePropertiesFile.ensure(f);
        long n = Files.readAllLines(f, StandardCharsets.UTF_8).stream().filter(l -> l.startsWith(ConfigMigration.KEY + "=")).count();
        assertEquals(1, n, "config-version 줄 수");
    }

    /** R3: Paper 에서 넘어온 첫 기동 — 가져오기가 만든 furnace.properties 는 이미 지금 형식이라 마이그레이션·백업이 돌지 않는다. */
    @Test
    void paperImportFirstBootRunsMigration() throws Exception {
        Files.writeString(dir.resolve("spigot.yml"), "settings:\n  bungeecord: true\n");
        Path props = dir.resolve("furnace.properties");
        PaperConfigImport.runIfNeeded(dir, props);
        assertTrue(Files.exists(props), "가져오기가 파일을 만든다");
        ConfigMigration.Result r = ConfigMigration.run(props, s -> {});
        assertNull(r, "첫 기동인데 마이그레이션·백업이 돈다: " + (r == null ? "" : r.changes() + " 백업 " + r.backup()));
    }

    private static boolean root() {
        return "root".equals(System.getProperty("user.name"));
    }

    private static void lockFile(Path f) throws Exception {
        if (root()) {
            Process p = new ProcessBuilder("chattr", "+i", f.toString()).inheritIO().start();
            org.junit.jupiter.api.Assumptions.assumeTrue(p.waitFor() == 0, "chattr 를 못 쓴다");
        } else {
            org.junit.jupiter.api.Assumptions.assumeTrue(f.toFile().setReadOnly() && f.getParent().toFile().setWritable(false));
        }
    }

    private static void unlockFile(Path f) throws Exception {
        if (root()) new ProcessBuilder("chattr", "-i", f.toString()).start().waitFor();
        else f.getParent().toFile().setWritable(true);
    }
}

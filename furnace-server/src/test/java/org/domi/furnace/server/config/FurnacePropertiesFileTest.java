// furnace.properties 자동 생성: 없으면 모든 옵션, 있으면 빠진 옵션만 덧붙이고 적힌 값은 그대로, 만든 파일은 기본값과 같은 동작.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.net.ConnectionLimits;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.domi.furnace.server.plugin.PluginSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FurnacePropertiesFileTest {

    @Test
    void createsFileWithAllOptions_thatLoadAsDefaults(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        assertEquals(FurnacePropertiesFile.Result.CREATED, FurnacePropertiesFile.ensure(f));
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("\nchunks-per-tick=auto\n"), text);
        assertTrue(text.contains("\n#region-threads=\n"), "코어 수에 따라 달라지는 기본값은 주석으로");
        // 만든 파일을 읽으면 파일이 없을 때와 같은 설정이다(동작이 바뀌지 않는다)
        assertEquals(Config.DEFAULT, ConfigLoader.load(f, w -> fail("경고가 나오면 안 된다: " + w)));
        assertEquals(OperatorOptions.DEFAULT, OperatorOptions.load(f, w -> fail("경고: " + w)));
        assertEquals(PluginSettings.DEFAULT, PluginSettings.load(f));
        assertEquals(org.domi.furnace.server.world.WorldSettings.DEFAULT, org.domi.furnace.server.world.WorldSettings.load(f));
        ConnectionLimits c = ConnectionLimits.load(f, w -> fail("경고: " + w));
        for (ConnectionState s : ConnectionState.values()) assertEquals(ConnectionLimits.DEFAULT.of(s), c.of(s), s.name());
        assertEquals(ConnectionLimits.DEFAULT.proxyProtocol(), c.proxyProtocol());
        assertTrue(c.proxyTrusted().isEmpty());
        assertTrue(ConfigLoader.ignoredVanillaKeys(f).isEmpty(), "server.properties 가 정하는 키는 쓰지 않는다");
        // 두 번째 기동: 바꿀 것이 없다
        assertEquals(FurnacePropertiesFile.Result.UNCHANGED, FurnacePropertiesFile.ensure(f));
        assertEquals(text, Files.readString(f, StandardCharsets.UTF_8));
    }

    @Test
    void listsEveryKeyInTheKeyTable_andOnlyKnownKeys() {
        Set<String> keys = FurnacePropertiesFile.keys();
        for (String k : OptionKeys.keys()) assertTrue(keys.contains(k), "키 표에 있는데 파일에 없음: " + k);
        for (String k : keys) {
            if (k.contains("<")) continue;   // 플러그인별 로그 수준 예시(주석)
            assertTrue(OptionKeys.isKnown(k), "키 표가 모르는 키를 쓴다(오타 경고가 난다): " + k);
        }
        assertTrue(keys.containsAll(ConnectionLimits.knownKeys()));
        assertTrue(keys.containsAll(org.domi.furnace.server.remote.RconLimits.knownKeys()));
        assertTrue(keys.containsAll(org.domi.furnace.server.plugin.library.LibrarySettings.KEYS));
        for (String vanilla : Config.VANILLA_KEYS) assertFalse(keys.contains(vanilla), vanilla);
    }

    @Test
    void createdFile_raisesNoStartupWarning_andOtherLoadersSeeDefaults(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        FurnacePropertiesFile.ensure(f);
        List<String> warns = new ArrayList<>();
        OptionKeys.audit(f, warns::add);
        assertEquals(List.of(), warns, "기본값 파일은 오타·위험 경고가 없어야 한다");
        assertEquals(org.domi.furnace.server.maintenance.MaintenanceOptions.DEFAULT,
                org.domi.furnace.server.maintenance.MaintenanceOptions.load(f, w -> fail("경고: " + w)));
        assertEquals(org.domi.furnace.server.metrics.MetricsOptions.DEFAULT,
                org.domi.furnace.server.metrics.MetricsOptions.load(f, w -> fail("경고: " + w)));
        assertEquals(org.domi.furnace.server.remote.RconLimits.DEFAULT, org.domi.furnace.server.remote.RconLimits.load(f));
        assertEquals(org.domi.furnace.server.net.TransportOptions.DEFAULT, org.domi.furnace.server.net.TransportOptions.load(f));
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(f)) { p.load(in); }
        assertEquals(org.domi.furnace.server.plugin.library.LibrarySettings.DEFAULT,
                org.domi.furnace.server.plugin.library.LibrarySettings.from(p, org.slf4j.LoggerFactory.getLogger("test")));
        // 라이브러리 세부 키는 enabled=false 일 때 적혀 있으면 경고하므로 주석으로 둔다
        assertFalse(p.containsKey("plugins.libraries.offline"));
    }

    @Test
    void existingFile_keepsUserValues_andAppendsOnlyMissing(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        String user = "# 내 서버\nchunks-per-tick=24\n#region-threads=6\nplugins.dev-mode = true\nserver-brand:MyBrand";   // 마지막 줄 끝에 줄바꿈 없음
        Files.writeString(f, user, StandardCharsets.UTF_8);
        assertEquals(FurnacePropertiesFile.Result.APPENDED, FurnacePropertiesFile.ensure(f));
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(user + System.lineSeparator()), "적어 둔 내용은 그대로 앞에 남는다");
        String added = text.substring(user.length());
        assertFalse(added.contains("chunks-per-tick="), "이미 있는 키는 다시 쓰지 않는다");
        assertFalse(added.contains("region-threads="), "주석으로 적힌 키도 있는 것으로 본다");
        assertFalse(added.contains("plugins.dev-mode="));
        assertFalse(added.contains("server-brand="));
        assertTrue(added.contains("\nio-threads=4\n"));
        assertTrue(added.contains("(자동 추가)"));
        Config c = ConfigLoader.load(f);
        assertEquals(24, c.chunksPerTick());
        assertEquals("MyBrand", OperatorOptions.load(f, w -> {}).serverBrand());
        assertEquals(FurnacePropertiesFile.Result.UNCHANGED, FurnacePropertiesFile.ensure(f));
    }
}

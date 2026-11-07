// 라이브러리 자동 로딩(B5)을 부트스트랩에 붙인 모습: 꺼져 있으면 붙이지 않고, 켜면(캐시 + offline 으로 네트워크 없이) 클래스패스에 붙고,
// 못 풀면 그 플러그인만 빠지고, 설명 파일이 잘못되면 플러그인이 빠진다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLibrariesTest {

    private static final String P = "org.domi.furnace.server.plugin.TestBootstraps$";

    @TempDir Path tmp;

    private Path plugins() { return tmp.resolve("plugins"); }

    /** 캐시(plugins/.libraries)에 라이브러리 jar 를 Maven 저장소 모양으로 둔다. sha256 을 돌려 준다. */
    private String cacheLib(String path, String marker) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JarOutputStream o = new JarOutputStream(bo)) {
            o.putNextEntry(new JarEntry("lib/marker.txt"));
            o.write(marker.getBytes(StandardCharsets.UTF_8));
            o.closeEntry();
        }
        Path f = plugins().resolve(".libraries").resolve(path);
        Files.createDirectories(f.getParent());
        Files.write(f, bo.toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bo.toByteArray())) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void plugin(String name, String libraries) throws IOException {
        PluginBootstrapTest.jar(plugins().resolve(name + ".jar"), "name: " + name + "\nversion: 1\nmain: " + P + "Addon\n" + libraries, Map.of());
    }

    private static LibrarySettings offlineOn() { return LibrarySettings.DEFAULT.withEnabled(true).withOffline(true).withTransitive(false); }

    private static PluginSettings settings(LibrarySettings l) { return PluginSettings.DEFAULT.withLibraries(l); }

    private static String markerOf(PluginBootstrapper.Prepared p) throws IOException {
        try (var in = p.loader().getResourceAsStream("lib/marker.txt")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void disabledByDefaultDoesNotAttachLibraries() throws Exception {
        cacheLib("a/b/1/b-1.jar", "attached");
        plugin("Lib", "libraries: [a:b:1]\n");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins());
        assertTrue(boot.failures().isEmpty(), "라이브러리를 못 받아도 플러그인은 불러온다(꺼져 있을 때): " + boot.failures());
        assertNull(markerOf(boot.find("Lib")));
        boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
    }

    @Test
    void enabledAttachesCachedLibrary() throws Exception {
        String sha = cacheLib("a/b/1/b-1.jar", "attached");
        plugin("Lib", "libraries:\n  - coordinate: a:b:1\n    sha256: \"" + sha + "\"\n");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins(), settings(offlineOn()));
        assertTrue(boot.failures().isEmpty(), boot.failures().toString());
        assertEquals("attached", markerOf(boot.find("Lib")));
        boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
    }

    @Test
    void pluginWithoutLibrariesIsUntouched() throws Exception {
        plugin("Plain", "");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins(), settings(offlineOn()));
        assertTrue(boot.failures().isEmpty());
        assertNull(markerOf(boot.find("Plain")));
        boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
    }

    @Test
    void unresolvableLibraryDropsOnlyThatPlugin() throws Exception {
        plugin("Broken", "libraries: [x:missing:1]\n");   // 캐시에 없고 offline
        plugin("Fine", "");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins(), settings(offlineOn()));
        assertEquals(List.of("Broken"), boot.failures());
        assertNull(boot.find("Broken"));
        assertNotNull(boot.find("Fine"));
        boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
    }

    @Test
    void wrongPinDropsPlugin() throws Exception {
        cacheLib("a/b/1/b-1.jar", "attached");
        plugin("Lib", "libraries:\n  - coordinate: a:b:1\n    sha256: \"" + "0".repeat(64) + "\"\n");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins(), settings(offlineOn()));
        assertEquals(List.of("Lib"), boot.failures());
    }

    @Test
    void malformedLibrariesEntryDropsPlugin() throws Exception {
        plugin("Bad", "libraries: [\"not a coordinate\"]\n");
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(plugins(), settings(offlineOn()));
        assertEquals(1, boot.failures().size());
        assertNull(boot.find("Bad"));
    }

    @Test
    void propertiesFileTurnsItOn() throws Exception {
        Path props = tmp.resolve("furnace.properties");
        Files.writeString(props, "plugins.libraries.enabled=true\nplugins.libraries.offline=true\nplugins.libraries.transitive=false\n");
        PluginSettings s = PluginSettings.load(props);
        assertTrue(s.libraries().enabled());
        assertTrue(s.libraries().offline());
        assertFalse(s.libraries().transitive());
        assertFalse(PluginSettings.load(tmp.resolve("none.properties")).libraries().enabled());
    }
}

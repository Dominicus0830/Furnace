package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.NotAPlugin;
import org.domi.furnace.compat.paper.fixtures.ProbePlugin;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** plugin.yml / paper-plugin.yml 을 읽어 Bukkit 플러그인이 Furnace 위에서 로드·켜지·꺼지는지 — 실제 서버(헤드리스)에 jar 를 올려 확인한다. */
class CompatPluginTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() {
        RecorderPlugin.reset();
        ProbePlugin.LOG.clear();
    }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: 1.2.3
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                description: Records events
                author: Dana
                authors: [Lee, Kim]
                website: https://example.com
                commands:
                  rec:
                    description: Record
                    aliases: [r]
                    permission: recorder.use
                permissions:
                  recorder.use:
                    default: op
                """, Map.of("config.yml", "greeting: hello\nnested:\n  count: 3\n  names: [a, b]\n"), RecorderPlugin.class);
    }

    private FurnaceTestServer start(Path... jars) {
        var b = FurnaceTestServer.builder().failFast(false);
        for (Path j : jars) b.pluginJar(j);
        return b.start();
    }

    @Test
    void loadsPluginYmlAndRunsTheLifecycle() throws Exception {
        try (FurnaceTestServer server = start(recorderJar())) {
            var plugin = server.plugin("Recorder");
            assertInstanceOf(BukkitPluginAdapter.class, plugin);
            assertTrue(plugin.isEnabled());
            assertEquals(List.of("recorder:load", "recorder:enable"), RecorderPlugin.LOG);

            JavaPlugin jp = ((BukkitPluginAdapter) plugin).javaPlugin();
            assertEquals("Recorder", jp.getName());
            var d = jp.getDescription();
            assertEquals("1.2.3", d.getVersion());
            assertEquals("1.21", d.getAPIVersion());
            assertEquals(List.of("Dana", "Lee", "Kim"), d.getAuthors());
            assertEquals("https://example.com", d.getWebsite());
            assertEquals("Records events", d.getDescription());
            assertEquals("Recorder v1.2.3", d.getFullName());
            assertEquals(List.of("r"), d.getCommands().get("rec").get("aliases"));
            // Furnace 쪽 설명에도 이름·명령·권한이 옮겨졌다
            var fd = plugin.description();
            assertEquals("Recorder", fd.name());
            assertNull(fd.apiVersion(), "Bukkit api-version 은 서버의 ApiVersion 검사에 넘기지 않는다");
            assertTrue(fd.commands().containsKey("rec"));
            assertEquals("recorder.use", fd.permissions().get(0).name());

            // Bukkit 쪽에서 본 서버
            assertSame(jp, Bukkit.getPluginManager().getPlugin("Recorder"));
            assertTrue(Bukkit.getPluginManager().isPluginEnabled("Recorder"));
            assertEquals(1, Bukkit.getPluginManager().getPlugins().length);
            assertEquals("Furnace", Bukkit.getName());
            assertTrue(Bukkit.getVersion().contains("(MC: 1.21.1)"), Bukkit.getVersion());
            assertEquals("1.21.1-R0.1-SNAPSHOT", Bukkit.getBukkitVersion());
            assertEquals(List.of("world"), Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toList());
            assertEquals(server.overworld().name(), Bukkit.getWorld("world").getName());
            assertEquals(server.directory().resolve("plugins").resolve("Recorder").toFile(), jp.getDataFolder());
        }
        // 서버가 닫히며 껐다
        assertTrue(RecorderPlugin.LOG.contains("recorder:disable"));
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    @Test
    void configFollowsBukkitSemantics() throws Exception {
        try (FurnaceTestServer server = start(recorderJar())) {
            JavaPlugin jp = ((BukkitPluginAdapter) server.plugin("Recorder")).javaPlugin();
            Path file = jp.getDataFolder().toPath().resolve("config.yml");
            assertTrue(Files.exists(file), "saveDefaultConfig 가 jar 의 기본값을 데이터 폴더에 복사했다");
            var cfg = jp.getConfig();
            assertEquals("hello", cfg.getString("greeting"));
            assertEquals(3, cfg.getInt("nested.count"));
            assertEquals(List.of("a", "b"), cfg.getStringList("nested.names"));
            assertEquals(java.util.Set.of("count", "names"), cfg.getConfigurationSection("nested").getKeys(false));
            assertEquals("fallback", cfg.getString("missing", "fallback"));

            cfg.set("greeting", "bye");
            cfg.set("extra.flag", true);
            jp.saveConfig();
            assertTrue(Files.readString(file).contains("greeting: bye"));
            jp.reloadConfig();
            assertEquals("bye", jp.getConfig().getString("greeting"));
            assertTrue(jp.getConfig().getBoolean("extra.flag"));
            // 파일에서 지워도 jar 의 기본값이 대신 보인다
            jp.getConfig().set("greeting", null);
            jp.saveConfig();
            jp.reloadConfig();
            assertEquals("hello", jp.getConfig().getString("greeting"));
            assertFalse(jp.getConfig().isSet("nope"));
        }
    }

    @Test
    void paperPluginYmlDependenciesDecideTheOrder() throws Exception {
        Path base = Jars.bukkit(tmp.resolve("base.jar"), "plugin.yml", """
                name: Base
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                api-version: '1.21'
                """, ProbePlugin.class);
        Path top = Jars.bukkit(tmp.resolve("top.jar"), "paper-plugin.yml", """
                name: Top
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                api-version: '1.21'
                dependencies:
                  server:
                    Base:
                      load: BEFORE
                      required: true
                    Late:
                      load: AFTER
                      required: false
                """, ProbePlugin.class);
        Path late = Jars.bukkit(tmp.resolve("late.jar"), "plugin.yml", """
                name: Late
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                api-version: '1.21'
                """, ProbePlugin.class);
        try (FurnaceTestServer server = start(top, late, base)) {
            // Base → Top → Late : Top 은 Base 가 먼저(BEFORE), Late 는 Top 뒤(AFTER = loadbefore)
            List<String> loads = ProbePlugin.LOG.stream().filter(s -> s.endsWith(":load")).toList();
            assertEquals(List.of("Base:load", "Top:load", "Late:load"), loads);
            assertEquals(List.of("Base", "Top", "Late"), server.api().plugins().plugins().stream().map(p -> p.name()).toList());
        }
    }

    @Test
    void refusesNewerApiVersionAndMainThatIsNotAJavaPlugin() throws Exception {
        Path newer = Jars.bukkit(tmp.resolve("newer.jar"), "plugin.yml", """
                name: Newer
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                api-version: '1.21.4'
                """, ProbePlugin.class);
        Path wrongMain = Jars.bukkit(tmp.resolve("wrong.jar"), "plugin.yml", """
                name: WrongMain
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.NotAPlugin
                api-version: '1.21'
                """, NotAPlugin.class);
        Path bad = Jars.bukkit(tmp.resolve("bad.jar"), "plugin.yml", "name: Broken\nmain: x.Y\n");   // version 없음
        Path ok = Jars.bukkit(tmp.resolve("ok.jar"), "plugin.yml", """
                name: Fine
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                api-version: '1.21.1'
                """, ProbePlugin.class);
        try (FurnaceTestServer server = start(newer, wrongMain, bad, ok)) {
            assertNull(server.plugin("Newer"), "서버보다 새 API 를 요구하면 불러오지 않는다");
            assertNull(server.plugin("WrongMain"));
            assertNull(server.plugin("Broken"));
            assertNotNull(server.plugin("Fine"));
            assertEquals(List.of("Fine"), server.api().plugins().plugins().stream().map(p -> p.name()).toList());
        }
    }

    @Test
    void legacyPluginWithoutApiVersionStillLoadsWithAWarning() throws Exception {
        Path legacy = Jars.bukkit(tmp.resolve("legacy.jar"), "plugin.yml", """
                name: Legacy
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.ProbePlugin
                """, ProbePlugin.class);
        try (FurnaceTestServer server = start(legacy)) {
            assertTrue(server.plugin("Legacy").isEnabled());
        }
    }

    @Test
    void disablingTheBukkitPluginDropsItsListeners() throws Exception {
        try (FurnaceTestServer server = start(recorderJar())) {
            Plugin jp = ((BukkitPluginAdapter) server.plugin("Recorder")).javaPlugin();
            assertFalse(HandlerList.getRegisteredListeners(jp).isEmpty());
            server.api().plugins().disable(server.plugin("Recorder"));
            assertFalse(jp.isEnabled());
            assertTrue(HandlerList.getRegisteredListeners(jp).isEmpty(), "꺼지면 Bukkit 처리기 목록에서 빠진다");
            assertTrue(RecorderPlugin.LOG.contains("recorder:disable"));
        }
    }
}

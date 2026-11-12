package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A15 점검에서 고친 것의 회귀 시험(헤드리스 서버 + 시험 안에서 컴파일한 플러그인 jar 둘): 리소스 찾기 순서, getOnlinePlayers 보기.
 * 기대값은 Paper 1.21.1 PluginClassLoader#getResource·CraftServer#getOnlinePlayers 본문에서 옮겼다.
 */
class A15PluginSurfaceParityTest {

    private static final String A_YML = """
            name: AuditA
            version: '1'
            main: audita.APlugin
            api-version: '1.21'
            """;

    private static final String A = """
            package audita;
            import java.io.*;
            import java.nio.charset.StandardCharsets;
            import org.bukkit.Bukkit;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class APlugin extends JavaPlugin {
                static String read(InputStream in) throws IOException {
                    if (in == null) return "null";
                    try (in) { return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim(); }
                }
                @Override public void onEnable() {
                    try {
                        WiringProbe.LOG.add("own=" + read(getResource("lang/en_us.json")));
                        WiringProbe.LOG.add("server=" + (getResource("registries/builtin-1.21.1.txt") == null ? "null" : "found"));
                        WiringProbe.LOG.add("loader=" + (getClassLoader().getResource("registries/builtin-1.21.1.txt") == null ? "null" : "found"));
                    } catch (IOException e) { WiringProbe.failure = e; }
                    getServer().getPluginManager().registerEvents(new Listener() {
                        @EventHandler public void a1(PlayerToggleSneakEvent e) { WiringProbe.LOG.add("A1"); }
                    }, this);
                    // 다른 플러그인이 켜진 뒤(첫 틱) 같은 우선순위 처리기를 하나 더 단다
                    Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().registerEvents(new Listener() {
                        @EventHandler public void a2(PlayerToggleSneakEvent e) { WiringProbe.LOG.add("A2"); }
                    }, this));
                }
            }
            """;

    private static final String B_YML = """
            name: AuditB
            version: '1'
            main: auditb.BPlugin
            api-version: '1.21'
            """;

    private static final String B = """
            package auditb;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class BPlugin extends JavaPlugin implements Listener {
                @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
                @EventHandler public void b1(PlayerToggleSneakEvent e) { WiringProbe.LOG.add("B1"); }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    /** 컴파일한 jar 에 리소스 파일을 더한다. */
    private static void addResource(Path jar, String name, String body) throws Exception {
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of())) {
            Path p = fs.getPath(name);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, body, StandardCharsets.UTF_8);
        }
    }

    private FurnaceTestServer start() throws Exception {
        Path a = JarCompiler.jar(tmp.resolve("a.jar"), A_YML, Map.of("audita.APlugin", A));
        addResource(a, "lang/en_us.json", "A-OWN");   // 서버 jar 에도 같은 이름(lang/en_us.json)이 있다
        Path b = JarCompiler.jar(tmp.resolve("b.jar"), B_YML, Map.of("auditb.BPlugin", B));
        return FurnaceTestServer.builder().failFast(false).pluginJar(a).pluginJar(b).start();
    }

    /** Paper PluginClassLoader#getResource = 자기 jar(findResource) → 라이브러리 로더. 서버 jar 는 보지 않는다. */
    @Test
    void getResourceReadsThePluginJarFirstAndNeverTheServer() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(WiringProbe.failure);
            assertTrue(WiringProbe.LOG.contains("own=A-OWN"), () -> "자기 jar 의 lang/en_us.json 이어야 한다: " + WiringProbe.LOG);
            assertTrue(WiringProbe.LOG.contains("server=null"), () -> "서버 jar 의 리소스는 보이지 않아야 한다: " + WiringProbe.LOG);
            assertTrue(WiringProbe.LOG.contains("loader=null"), () -> "getClassLoader().getResource 도 같다: " + WiringProbe.LOG);
        }
    }

    /** CraftServer#getOnlinePlayers 는 고칠 수 없는 살아 있는 보기(playerView)다. */
    @Test
    void onlinePlayersIsALiveUnmodifiableView() throws Exception {
        try (FurnaceTestServer server = start()) {
            Collection<? extends Player> view = Bukkit.getOnlinePlayers();
            assertTrue(view instanceof java.util.List<?>, "Paper: unmodifiableList — List 로 캐스팅할 수 있다");
            assertThrows(UnsupportedOperationException.class, view::clear, "Paper: 비어 있어도 던진다");
            assertThrows(UnsupportedOperationException.class, () -> view.removeIf(p -> true), "Paper: 비어 있어도 던진다");
            TestPlayer dana = server.join("Dana");
            server.tick(2);
            assertEquals(1, view.size(), "Paper: 앞서 받은 컬렉션에도 새 접속자가 보인다");
            assertThrows(UnsupportedOperationException.class, () -> Bukkit.getOnlinePlayers().clear());
            assertEquals("Dana", ((java.util.List<? extends Player>) view).get(0).getName(), "List#get 도 살아 있다");
            dana.close();
        }
    }
}

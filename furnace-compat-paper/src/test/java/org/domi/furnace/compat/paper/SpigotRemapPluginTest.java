package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.nms.SpigotMappingsFixture;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.NmsProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * H7d: Spigot 이름으로 컴파일된 플러그인이 대응표({@code plugins.compat.nms.spigot-mappings})를 거쳐 실제 플러그인 클래스로더에서 Mojang 이름 shim 에
 * 이어지는지 — 바이트코드 참조(클래스·필드·상속된 메서드·Spigot 이 붙인 이름·정적 인터페이스 메서드)와 리플렉션 문자열. 대응표는 가짜다
 * ({@link SpigotMappingsFixture}, 실제 Mojang·Spigot 자료가 아니다).
 */
class SpigotRemapPluginTest {

    private static final String YML = """
            name: SpigUser
            version: '1'
            main: spig.SpigPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package spig;
            import net.minecraft.core.BlockPosition;
            import net.minecraft.network.chat.IChatBaseComponent;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class SpigPlugin extends JavaPlugin {
                @Override public void onEnable() {
                    try {
                        BlockPosition p = new BlockPosition(1, 2, 3);
                        NmsProbe.put("x", p.u());
                        NmsProbe.put("y", p.getY());
                        NmsProbe.put("offset.x", p.c(10, 0, 0).u());
                        NmsProbe.put("component", IChatBaseComponent.b("hi").getString());
                    } catch (Throwable t) { NmsProbe.put("direct", t); }
                    try {
                        NmsProbe.put("reflect.class", Class.forName("net.minecraft.core.BlockPosition").getName());
                        NmsProbe.put("reflect.field", Class.forName("net.minecraft.server.level.EntityPlayer").getDeclaredField("c").getName());
                        NmsProbe.put("reflect.method", BlockPosition.class.getMethod("u").getName());
                    } catch (Throwable t) { NmsProbe.put("reflect", t); }
                    NmsProbe.put("enabled", true);
                }
            }
            """;

    private static final String DIRECT = """
            package spig;
            import net.minecraft.network.chat.IChatBaseComponent;
            import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
            import net.minecraft.server.level.EntityPlayer;
            public final class Direct {
                public static void send(Object handle) {
                    EntityPlayer h = (EntityPlayer) handle;
                    h.c.b(new ClientboundSystemChatPacket(IChatBaseComponent.b("hello spigot"), false));
                }
            }
            """;

    // 컴파일에만 쓰는 Spigot 이름 스텁(jar 에는 넣지 않는다)
    private static final Map<String, String> STUBS = Map.of(
            "net.minecraft.core.BaseBlockPosition", """
                    package net.minecraft.core;
                    public class BaseBlockPosition {
                        public int u() { return 0; }
                        public int getY() { return 0; }
                    }
                    """,
            "net.minecraft.core.BlockPosition", """
                    package net.minecraft.core;
                    public class BlockPosition extends BaseBlockPosition {
                        public BlockPosition(int x, int y, int z) {}
                        public BlockPosition c(int x, int y, int z) { return this; }
                    }
                    """,
            "net.minecraft.network.chat.IChatBaseComponent", """
                    package net.minecraft.network.chat;
                    public interface IChatBaseComponent {
                        static IChatMutableComponent b(String s) { return null; }
                        String getString();
                    }
                    """,
            "net.minecraft.network.chat.IChatMutableComponent", """
                    package net.minecraft.network.chat;
                    public class IChatMutableComponent implements IChatBaseComponent {
                        public String getString() { return null; }
                    }
                    """,
            "net.minecraft.network.protocol.Packet", """
                    package net.minecraft.network.protocol;
                    public interface Packet<T> {}
                    """,
            "net.minecraft.network.protocol.game.ClientboundSystemChatPacket", """
                    package net.minecraft.network.protocol.game;
                    public class ClientboundSystemChatPacket implements net.minecraft.network.protocol.Packet<Object> {
                        public ClientboundSystemChatPacket(net.minecraft.network.chat.IChatBaseComponent c, boolean overlay) {}
                    }
                    """,
            "net.minecraft.server.network.ServerCommonPacketListenerImpl", """
                    package net.minecraft.server.network;
                    public abstract class ServerCommonPacketListenerImpl {
                        public void b(net.minecraft.network.protocol.Packet<?> p) {}
                    }
                    """,
            "net.minecraft.server.network.PlayerConnection", """
                    package net.minecraft.server.network;
                    public class PlayerConnection extends ServerCommonPacketListenerImpl {}
                    """,
            "net.minecraft.server.level.EntityPlayer", """
                    package net.minecraft.server.level;
                    public class EntityPlayer {
                        public net.minecraft.server.network.PlayerConnection c;
                    }
                    """);

    @TempDir
    Path tmp;

    private ListAppender<ILoggingEvent> logs;
    private Logger root;

    @BeforeEach
    void setUp() {
        NmsProbe.VALUES.clear();
        org.domi.furnace.compat.nms.NmsTrap.resetWarnings();
        org.domi.furnace.compat.nms.NmsReflect.resetWarnings();
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void tearDown() { root.detachAppender(logs); }

    private Path pluginJar(String manifestNamespace) throws Exception {
        Map<String, String> sources = new java.util.HashMap<>(STUBS);
        sources.put("spig.SpigPlugin", PLUGIN);
        sources.put("spig.Direct", DIRECT);
        Path jar = JarCompiler.jar(tmp.resolve("spig.jar"), YML, sources, Set.of("net.minecraft."));
        if (manifestNamespace != null) addManifest(jar, manifestNamespace);
        return jar;
    }

    private static void addManifest(Path jar, String ns) throws Exception {
        Path copy = jar.resolveSibling("with-manifest.jar");
        var man = new java.util.jar.Manifest();
        man.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        man.getMainAttributes().putValue("paperweight-mappings-namespace", ns);
        try (var in = new java.util.jar.JarFile(jar.toFile()); var out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(copy), man)) {
            var en = in.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                out.putNextEntry(new java.util.jar.JarEntry(e.getName()));
                try (var s = in.getInputStream(e)) {
                    s.transferTo(out);
                }
                out.closeEntry();
            }
        }
        java.nio.file.Files.move(copy, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private FurnaceTestServer start(Path jar, String mode) throws Exception {
        Path[] files = SpigotMappingsFixture.write(tmp.resolve("maps"));
        String joined = files[0] + File.pathSeparator + files[1] + File.pathSeparator + files[2];
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar)
                .compatOption("plugins.compat.nms.spigot-mappings", mode)
                .compatOption("plugins.compat.nms.spigot-mappings.files", joined)
                .start();
    }

    private List<String> messages() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList(); }

    @Test
    void spigotNamedPluginRunsAgainstMojangShims() throws Exception {
        try (FurnaceTestServer server = start(pluginJar(null), "files")) {
            assertEquals(Boolean.TRUE, NmsProbe.get("enabled"));
            assertEquals(null, NmsProbe.get("direct"), () -> String.valueOf(NmsProbe.get("direct")));
            assertEquals(1, NmsProbe.get("x"), "BlockPosition.u() → Vec3i.getX() (shim 상속 사슬)");
            assertEquals(2, NmsProbe.get("y"), "Spigot 이 붙인 getY → 난독화 v → Mojang getY");
            assertEquals(11, NmsProbe.get("offset.x"));
            assertEquals("hi", NmsProbe.get("component"), "정적 인터페이스 메서드 IChatBaseComponent.b → Component.literal");
            assertEquals(null, NmsProbe.get("reflect"), () -> String.valueOf(NmsProbe.get("reflect")));
            assertEquals("net.minecraft.core.BlockPos", NmsProbe.get("reflect.class"), "Class.forName 의 Spigot 문자열 이름도 바꾼다(Paper 와 같다)");
            assertEquals("connection", NmsProbe.get("reflect.field"));
            assertEquals("getX", NmsProbe.get("reflect.method"));
            List<String> log = messages();
            assertTrue(log.stream().anyMatch(m -> m.startsWith("플러그인 SpigUser: NMS·CraftBukkit 심볼") && m.contains("대응표로 바꿔 올린다")
                    && m.contains("아직 없음 0")), () -> String.join("\n", log));

            TestPlayer tp = server.join("Spig");
            server.tick(2);
            Player p = Bukkit.getPlayer("Spig");
            assertNotNull(p);
            Class<?> direct = Bukkit.getPluginManager().getPlugin("SpigUser").getClass().getClassLoader().loadClass("spig.Direct");
            var loc = tp.player().location();
            BlockPos at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
            server.onRegion(at, () -> {
                try {
                    direct.getMethod("send", Object.class).invoke(null, ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle());
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e.getCause() != null ? e.getCause() : e);
                }
                return null;
            });
            tp.awaitMessage(m -> m.equals("hello spigot"));
        }
    }

    @Test
    void mojangManifestJarsAreNotRemapped() throws Exception {
        try (FurnaceTestServer server = start(pluginJar("mojang"), "files")) {
            assertEquals(Boolean.TRUE, NmsProbe.get("enabled"));
            assertTrue(NmsProbe.get("direct") instanceof LinkageError, "Mojang 이름이라고 밝힌 jar 는 바꾸지 않는다 — Spigot 이름은 shim 에 없다: " + NmsProbe.get("direct"));
            assertTrue(NmsProbe.get("reflect") instanceof ClassNotFoundException, String.valueOf(NmsProbe.get("reflect")));
        }
    }

    @Test
    void offModeLeavesSpigotNamesUnresolvedAndHintsTheSetting() throws Exception {
        try (FurnaceTestServer server = start(pluginJar(null), "off")) {
            assertEquals(Boolean.TRUE, NmsProbe.get("enabled"), "변환이 없어도 플러그인은 켜진다(그 호출만 실패)");
            assertTrue(NmsProbe.get("direct") instanceof LinkageError, String.valueOf(NmsProbe.get("direct")));
            List<String> log = messages();
            assertTrue(log.stream().anyMatch(m -> m.startsWith("플러그인 SpigUser: NMS·CraftBukkit 심볼") && m.contains("spigot-mappings=download")),
                    () -> String.join("\n", log));
            assertSame(null, org.domi.furnace.compat.nms.SpigotMappings.forPlugin("SpigUser"));
        }
    }
}

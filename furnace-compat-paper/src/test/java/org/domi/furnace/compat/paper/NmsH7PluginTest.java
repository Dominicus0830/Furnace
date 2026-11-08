package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
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
 * H7: 실제 플러그인 클래스로더를 거쳐 — 미지원 NMS 호출이 그 호출 위치에서만 링크 오류(UnsupportedNmsLink)가 되고 나머지는 도는지, 로드 보고·strict·
 * version-package 옵션, NMS 클래스 상속 거부, {@code player.connection.send(packet)} 와 데이터 컴포넌트 shim.
 */
class NmsH7PluginTest {

    private static final String YML = """
            name: H7User
            version: '1'
            main: nmsh7.H7Plugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package nmsh7;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class H7Plugin extends JavaPlugin {
                @Override public void onEnable() {
                    try { Uses.missingStatic(); NmsProbe.put("missing.static", "ran"); }
                    catch (Throwable t) { NmsProbe.put("missing.static", t); }
                    try { Class.forName("net.minecraft.world.level.levelgen.NoiseChunk"); NmsProbe.put("reflect.class", "found"); }
                    catch (Throwable t) { NmsProbe.put("reflect.class", t); }
                    try { net.minecraft.server.level.ServerPlayer.class.getDeclaredField("nope"); NmsProbe.put("reflect.field", "found"); }
                    catch (Throwable t) { NmsProbe.put("reflect.field", t); }
                    NmsProbe.put("server.class", getServer().getClass().getName());
                    NmsProbe.put("server.package", getServer().getClass().getPackage().getName());
                    NmsProbe.put("enabled", true);
                }
            }
            """;

    private static final String USES = """
            package nmsh7;
            public final class Uses {
                static void missingStatic() { net.minecraft.server.level.ServerLevel.nopeStatic(); }
            }
            """;

    private static final String DIRECT = """
            package nmsh7;
            import net.minecraft.core.component.DataComponents;
            import net.minecraft.nbt.CompoundTag;
            import net.minecraft.network.chat.Component;
            import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
            import net.minecraft.server.level.ServerPlayer;
            import net.minecraft.world.item.component.CustomData;
            import org.bukkit.craftbukkit.entity.CraftPlayer;
            import org.bukkit.craftbukkit.inventory.CraftItemStack;
            import org.bukkit.entity.Player;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class Direct {
                public static void send(Player p) {
                    ServerPlayer h = ((CraftPlayer) p).getHandle();
                    NmsProbe.put("connection.player", h.connection.player);
                    h.connection.send(new ClientboundSystemChatPacket(Component.literal("hello nms"), false));
                    h.connection.send(new ClientboundSystemChatPacket(Component.literal("bar nms"), true));
                }
                public static void items() {
                    net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND));
                    NmsProbe.put("hover.default", s.getHoverName().getString());
                    s.set(DataComponents.CUSTOM_NAME, Component.literal("Gem"));
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("k", 3);
                    s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    NmsProbe.put("hover", s.getHoverName().getString());
                    NmsProbe.put("custom", s.get(DataComponents.CUSTOM_DATA).copyTag().getInt("k"));
                    NmsProbe.put("max", s.getOrDefault(DataComponents.MAX_STACK_SIZE, -1));
                    NmsProbe.put("old.name", s.remove(DataComponents.CUSTOM_NAME).getString());
                    NmsProbe.put("has.name", s.has(DataComponents.CUSTOM_NAME));
                }
                public static void kick(Player p) {
                    ((CraftPlayer) p).getHandle().connection.disconnect(Component.literal("bye nms"));
                }
            }
            """;

    /** NMS 클래스를 상속한 플러그인 클래스 — 정의 때 거부돼야 한다. */
    private static final String SUB = """
            package nmsh7;
            public class Sub extends net.minecraft.world.entity.LivingEntity {
                public Sub() { super(null); }
            }
            """;

    // 컴파일에만 쓰는 스텁 — 실제 shim 에 없는 정적 메서드를 가진 ServerLevel
    private static final String STUB_LEVEL = """
            package net.minecraft.server.level;
            public class ServerLevel {
                public static void nopeStatic() { throw new AssertionError("stub"); }
            }
            """;

    @TempDir
    Path tmp;

    private ListAppender<ILoggingEvent> logs;
    private Logger root;

    @BeforeEach
    void setUp() {
        NmsProbe.VALUES.clear();
        org.domi.furnace.compat.nms.NmsTrap.resetWarnings();   // 같은 플러그인 이름을 시험마다 다시 쓴다 — 경고 한 번 기록을 비운다
        org.domi.furnace.compat.nms.NmsReflect.resetWarnings();
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void tearDown() { root.detachAppender(logs); }

    private FurnaceTestServer start(FurnaceTestServer.Builder b) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("h7user.jar"), YML,
                Map.of("nmsh7.H7Plugin", PLUGIN, "nmsh7.Uses", USES, "nmsh7.Direct", DIRECT, "nmsh7.Sub", SUB,
                        "net.minecraft.server.level.ServerLevel", STUB_LEVEL),
                Set.of("net.minecraft."));
        return b.failFast(false).pluginJar(jar).start();
    }

    private List<String> messages() { return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList(); }

    @Test
    void unsupportedCallsFailOnlyWhereTheyRunAndTheReportIsLogged() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder())) {
            assertEquals(Boolean.TRUE, NmsProbe.get("enabled"), "미지원 호출이 있어도 플러그인은 켜진다");
            Object missing = NmsProbe.get("missing.static");
            assertTrue(missing instanceof NoSuchMethodError u && u instanceof org.domi.furnace.compat.nms.UnsupportedNmsLink
                    && u.getMessage().contains("ServerLevel#nopeStatic"), "JVM 과 같은 종류의 링크 오류: " + missing);
            assertTrue(NmsProbe.get("reflect.class") instanceof ClassNotFoundException, String.valueOf(NmsProbe.get("reflect.class")));
            assertTrue(NmsProbe.get("reflect.field") instanceof NoSuchFieldException, "리플렉션 실패는 원래 예외 그대로");
            assertEquals("org.bukkit.craftbukkit.CraftServer", NmsProbe.get("server.class"));

            List<String> log = messages();
            assertTrue(log.stream().anyMatch(m -> m.startsWith("플러그인 H7User: NMS·CraftBukkit 심볼") && m.contains("이름 체계=Spigot(가정")), () -> String.join("\n", log));
            assertTrue(log.stream().anyMatch(m -> m.startsWith("플러그인 H7User: 미지원 NMS 심볼") && m.contains("ServerLevel#nopeStatic(0)")), () -> String.join("\n", log));
            assertTrue(log.stream().anyMatch(m -> m.contains("NMS 클래스 net.minecraft.world.level.levelgen.NoiseChunk") && m.contains("등급 C")),
                    () -> String.join("\n", log));
            assertTrue(log.stream().anyMatch(m -> m.contains("NMS 멤버 net.minecraft.server.level.ServerPlayer#nope")), () -> String.join("\n", log));

            Plugin plugin = Bukkit.getPluginManager().getPlugin("H7User");
            ClassLoader loader = plugin.getClass().getClassLoader();
            var refused = assertThrows(UnsupportedNmsCall.class, () -> loader.loadClass("nmsh7.Sub"),
                    "NMS 클래스 상속은 정의 때 거부(원래 바이트로 올리지 않는다)");
            assertTrue(refused.getMessage().contains("LivingEntity"), refused.getMessage());
        }
    }

    @Test
    void strictRefusesToLoadAPluginWithUnsupportedSymbols() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder().compatNmsStrict(true))) {
            assertNull(Bukkit.getPluginManager().getPlugin("H7User"));
            assertNull(NmsProbe.get("enabled"));
        }
    }

    @Test
    void versionedPackageNamesAreReportedWhenAsked() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder().compatNmsVersionPackage("versioned"))) {
            assertEquals("org.bukkit.craftbukkit.v1_21_R1.CraftServer", NmsProbe.get("server.class"));
            assertEquals("org.bukkit.craftbukkit.v1_21_R1", NmsProbe.get("server.package"));
            assertSame(org.bukkit.craftbukkit.CraftServer.class, Bukkit.getServer().getClass(), "실제 클래스는 그대로 — 이름 읽기만 바뀐다");
        }
    }

    @Test
    void connectionSendDeliversPacketShimsAndComponentsWork() throws Exception {
        try (FurnaceTestServer server = start(FurnaceTestServer.builder())) {
            TestPlayer tp = server.join("Nms");
            server.tick(2);
            Player p = Bukkit.getPlayer("Nms");
            assertNotNull(p);
            Class<?> direct = Bukkit.getPluginManager().getPlugin("H7User").getClass().getClassLoader().loadClass("nmsh7.Direct");
            var loc = tp.player().location();
            BlockPos at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
            server.onRegion(at, () -> {
                try {
                    direct.getMethod("send", Player.class).invoke(null, p);
                    direct.getMethod("items").invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e.getCause() != null ? e.getCause() : e);
                }
                return null;
            });
            assertSame(((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle(), NmsProbe.get("connection.player"));
            tp.awaitMessage(m -> m.equals("hello nms"));
            server.tick(2);
            tp.sync();
            assertTrue(tp.actionBars().stream().anyMatch(t -> t.plainText().equals("bar nms")), () -> String.valueOf(tp.actionBars()));

            assertEquals("Diamond", NmsProbe.get("hover.default"), "이름 없으면 아이템 번역 키 — 바닐라 서버처럼 getString 은 en_us 로 번역한다");
            assertEquals("Gem", NmsProbe.get("hover"));
            assertEquals(3, NmsProbe.get("custom"));
            assertEquals(64, NmsProbe.get("max"), "종류 기본값이 보인다");
            assertEquals("Gem", NmsProbe.get("old.name"));
            assertEquals(Boolean.FALSE, NmsProbe.get("has.name"));

            server.onRegion(at, () -> {
                try {
                    direct.getMethod("kick", Player.class).invoke(null, p);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e.getCause() != null ? e.getCause() : e);
                }
                return null;
            });
            long until = System.nanoTime() + 5_000_000_000L;
            while (tp.isConnected() && System.nanoTime() < until) {
                server.tick();
                Thread.sleep(10);
            }
            assertTrue(!tp.isConnected(), "끊겼다(봇 쪽 이유는 끊기 패킷 또는 소켓 닫힘 — 순서에 따라 다르다)");
            assertTrue(messages().contains("Nms lost connection: bye nms"), "끊기 이유가 Component 그대로 간다");
        }
    }
}

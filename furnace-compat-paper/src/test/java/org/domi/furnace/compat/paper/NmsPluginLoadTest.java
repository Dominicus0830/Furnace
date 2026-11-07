package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.NmsProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H6: Spigot(버전 접미사 {@code org.bukkit.craftbukkit.v1_21_R1}) 이름으로 컴파일된 플러그인과 문자열 리플렉션이 이 서버의 접미사 없는
 * CraftBukkit 클래스로 풀리는지. 플러그인은 시험 안에서 컴파일한 진짜 jar 이고, 접미사 클래스는 컴파일에만 쓰는 스텁이라 jar 에 없다
 * (실제 Spigot 플러그인 jar 와 같은 모양) — 플러그인 클래스로더의 변환이 없으면 NoClassDefFoundError 다.
 */
class NmsPluginLoadTest {

    private static final String YML = """
            name: NmsUser
            version: '1'
            main: nmsuser.NmsUserPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package nmsuser;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class NmsUserPlugin extends JavaPlugin {
                @Override public void onEnable() {
                    try { Reflect.run(); } catch (Throwable t) { NmsProbe.put("enable.failure", t); }
                }
            }
            """;

    /** 실제 라이브러리가 하는 방식: 서버 패키지에서 버전을 읽거나, 버전을 문자열로 붙이거나, 여러 버전 이름을 차례로 찾아본다. */
    private static final String REFLECT = """
            package nmsuser;
            import java.lang.invoke.MethodHandles;
            import org.bukkit.Bukkit;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class Reflect {
                static String version = "v1_21_R1";
                static void run() throws Exception {
                    String pkg = Bukkit.getServer().getClass().getPackage().getName();
                    NmsProbe.put("server.package", pkg);
                    NmsProbe.put("forName.versioned", Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer"));
                    NmsProbe.put("forName.plain", Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer"));
                    NmsProbe.put("forName.3arg", Class.forName("org.bukkit.craftbukkit." + version + ".CraftServer", false, Reflect.class.getClassLoader()));
                    NmsProbe.put("loadClass", Reflect.class.getClassLoader().loadClass("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack"));
                    NmsProbe.put("lookup.findClass", MethodHandles.lookup().findClass("org.bukkit.craftbukkit." + version + ".CraftWorld"));
                    NmsProbe.put("forName.mojang", Class.forName("net.minecraft.server.level.ServerPlayer"));
                    try {
                        Class.forName("org.bukkit.craftbukkit.v1_20_R3.CraftServer");
                        NmsProbe.put("forName.otherVersion", "found");
                    } catch (ClassNotFoundException e) {
                        NmsProbe.put("forName.otherVersion", e.getMessage());
                    }
                    try {
                        Class.forName("org.bukkit.craftbukkit." + version + ".Nope");
                        NmsProbe.put("forName.missing", "found");
                    } catch (ClassNotFoundException e) {
                        NmsProbe.put("forName.missing", e.getMessage());
                    }
                    // 리플렉션으로 핸들 얻기(문자열 메서드 이름)
                    Class<?> craftItem = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
                    Object nms = craftItem.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class)
                            .invoke(null, new org.bukkit.inventory.ItemStack(org.bukkit.Material.APPLE, 2));
                    NmsProbe.put("reflect.asNMSCopy", nms.toString());
                }
            }
            """;

    /** 바이트코드로 직접 부르는 쪽(Spigot API 로 컴파일된 모양): 캐스트·getHandle·정적 다리. */
    private static final String DIRECT = """
            package nmsuser;
            import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
            import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
            import org.bukkit.entity.Player;
            import org.domi.furnace.compat.paper.fixtures.NmsProbe;
            public final class Direct {
                public static void run(Player p) {
                    CraftPlayer cp = (CraftPlayer) p;
                    net.minecraft.server.level.ServerPlayer h = cp.getHandle();
                    NmsProbe.put("direct.handle", h);
                    NmsProbe.put("direct.uuid", h.getUUID());
                    net.minecraft.world.item.ItemStack s = CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE, 3));
                    NmsProbe.put("direct.nms", s.toString());
                    NmsProbe.put("direct.mirror", CraftItemStack.asCraftMirror(s));
                    NmsProbe.put("direct.instanceof", p instanceof CraftPlayer);
                }
            }
            """;

    // Spigot 이름 스텁 — 컴파일에만 쓴다(jar 에 넣지 않는다)
    private static final String STUB_PLAYER = """
            package org.bukkit.craftbukkit.v1_21_R1.entity;
            public class CraftPlayer {
                public net.minecraft.server.level.ServerPlayer getHandle() { throw new AssertionError("stub"); }
            }
            """;

    private static final String STUB_ITEM = """
            package org.bukkit.craftbukkit.v1_21_R1.inventory;
            public final class CraftItemStack extends org.bukkit.inventory.ItemStack {
                private CraftItemStack() { super(org.bukkit.Material.AIR); }
                public static net.minecraft.world.item.ItemStack asNMSCopy(org.bukkit.inventory.ItemStack s) { throw new AssertionError("stub"); }
                public static CraftItemStack asCraftMirror(net.minecraft.world.item.ItemStack s) { throw new AssertionError("stub"); }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { NmsProbe.VALUES.clear(); }

    private FurnaceTestServer start() throws Exception { return start(true); }

    private FurnaceTestServer start(boolean nms) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("nmsuser.jar"), YML,
                Map.of("nmsuser.NmsUserPlugin", PLUGIN, "nmsuser.Reflect", REFLECT, "nmsuser.Direct", DIRECT,
                        "org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer", STUB_PLAYER,
                        "org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack", STUB_ITEM),
                Set.of("org.bukkit.craftbukkit.v1_21_R1."));
        return FurnaceTestServer.builder().failFast(false).compatNms(nms).pluginJar(jar).start();
    }

    @Test
    void turningTheOptionOffLoadsTheBytesUnchanged() throws Exception {
        try (FurnaceTestServer server = start(false)) {
            // 변환이 없으면 리플렉션은 진짜 JVM 처럼 접미사 이름을 못 찾고, 직접 참조는 링크 때 실패한다
            Object failure = NmsProbe.get("enable.failure");
            assertTrue(failure instanceof ClassNotFoundException cnfe && cnfe.getMessage().equals("org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer"), String.valueOf(failure));
            Plugin plugin = Bukkit.getPluginManager().getPlugin("NmsUser");
            Class<?> direct = plugin.getClass().getClassLoader().loadClass("nmsuser.Direct");
            var e = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> direct.getMethod("run", Player.class).invoke(null, (Player) null));
            assertTrue(e.getCause() instanceof NoClassDefFoundError, String.valueOf(e.getCause()));
        }
    }

    @Test
    void reflectionResolvesTheVersionedNamesToTheSameClasses() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(NmsProbe.get("enable.failure"), () -> "onEnable 이 던졌다: " + NmsProbe.get("enable.failure"));
            assertEquals("org.bukkit.craftbukkit", NmsProbe.get("server.package"), "기본은 Paper 처럼 접미사 없는 패키지를 알린다");
            assertSame(CraftPlayer.class, NmsProbe.get("forName.versioned"));
            assertSame(CraftPlayer.class, NmsProbe.get("forName.plain"));
            assertSame(CraftServer.class, NmsProbe.get("forName.3arg"));
            assertSame(org.bukkit.craftbukkit.inventory.CraftItemStack.class, NmsProbe.get("loadClass"));
            assertSame(org.bukkit.craftbukkit.CraftWorld.class, NmsProbe.get("lookup.findClass"));
            assertSame(net.minecraft.server.level.ServerPlayer.class, NmsProbe.get("forName.mojang"),
                    "Mojang 이름 런타임 판정(reflection-remapper 가 쓰는 것)이 참이 된다");
            assertEquals("org.bukkit.craftbukkit.v1_20_R3.CraftServer", NmsProbe.get("forName.otherVersion"),
                    "다른 버전 접미사는 풀지 않는다 — 버전 모듈을 고르는 라이브러리가 엉뚱한 모듈을 고르지 않게");
            assertEquals("org.bukkit.craftbukkit.v1_21_R1.Nope", NmsProbe.get("forName.missing"), "없는 이름은 원래 이름으로 ClassNotFoundException");
            assertEquals("2 minecraft:apple", NmsProbe.get("reflect.asNMSCopy"));
        }
    }

    @Test
    void bytecodeCompiledAgainstSpigotNamesLinksToTheCraftClasses() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(NmsProbe.get("enable.failure"), () -> "onEnable 이 던졌다: " + NmsProbe.get("enable.failure"));
            TestPlayer tp = server.join("Spi");
            server.tick(2);
            Player p = Bukkit.getPlayer("Spi");
            assertNotNull(p);
            Plugin plugin = Bukkit.getPluginManager().getPlugin("NmsUser");
            Class<?> direct = plugin.getClass().getClassLoader().loadClass("nmsuser.Direct");
            assertSame(plugin.getClass().getClassLoader(), direct.getClassLoader(), "플러그인 클래스로더가 올렸다(변환을 거쳤다)");
            var loc = tp.player().location();
            BlockPos at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
            server.onRegion(at, () -> {
                try {
                    direct.getMethod("run", Player.class).invoke(null, p);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e.getCause() != null ? e.getCause() : e);
                }
                return null;
            });
            assertSame(((CraftPlayer) p).getHandle(), NmsProbe.get("direct.handle"));
            assertEquals(p.getUniqueId(), NmsProbe.get("direct.uuid"));
            assertEquals("3 minecraft:stone", NmsProbe.get("direct.nms"));
            assertTrue(NmsProbe.get("direct.mirror") instanceof org.bukkit.craftbukkit.inventory.CraftItemStack);
            assertEquals(Boolean.TRUE, NmsProbe.get("direct.instanceof"));
            // 플러그인 jar 의 클래스는 자기 jar 를 코드 출처로 가진다(변환 경로도 URLClassLoader 와 같다)
            var src = direct.getProtectionDomain().getCodeSource();
            assertNotNull(src);
            assertTrue(src.getLocation().toString().endsWith("nmsuser.jar"), src.getLocation().toString());
            assertEquals("nmsuser", direct.getPackage().getName());
        }
    }
}

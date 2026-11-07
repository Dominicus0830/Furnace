package org.domi.furnace.compat.realworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.domi.furnace.compat.realworld.NmsScan.Group;
import org.domi.furnace.compat.realworld.NmsScan.JarScan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H5 호출 빈도 측정기의 규칙 시험 — 합성 클래스(NMS·CraftBukkit 모양의 가짜 선언을 부르는 플러그인)로 센 값을 확인한다. */
class NmsScanTest {

    @TempDir
    Path tmp;

    @Test
    void namesAreGroupedAndVersionSuffixesAreNormalized() {
        assertEquals(Group.CRAFTBUKKIT, NmsScan.groupOf("org/bukkit/craftbukkit/v1_21_R1/entity/CraftPlayer"));
        assertEquals(Group.CRAFTBUKKIT, NmsScan.groupOf("org/bukkit/craftbukkit/entity/CraftPlayer"));
        assertEquals(Group.NMS_LEGACY, NmsScan.groupOf("net/minecraft/server/v1_16_R3/EntityPlayer"));
        assertEquals(Group.NMS, NmsScan.groupOf("net/minecraft/server/level/ServerPlayer"));
        assertEquals(Group.NMS, NmsScan.groupOf("net/minecraft/server/MinecraftServer"));
        assertEquals(Group.AUTHLIB, NmsScan.groupOf("com/mojang/authlib/GameProfile"));
        assertEquals(Group.SERIALIZATION, NmsScan.groupOf("com/mojang/datafixers/util/Pair"));
        assertEquals(Group.BRIGADIER, NmsScan.groupOf("com/mojang/brigadier/CommandDispatcher"));
        assertNull(NmsScan.groupOf("org/bukkit/entity/Player"), "Bukkit API 는 H4 의 몫이다");
        assertNull(NmsScan.groupOf("java/lang/String"));

        assertEquals("org/bukkit/craftbukkit/entity/CraftPlayer", NmsScan.normalize("org/bukkit/craftbukkit/v1_21_R1/entity/CraftPlayer"));
        assertEquals("net/minecraft/server/EntityPlayer", NmsScan.normalize("net/minecraft/server/v1_16_R3/EntityPlayer"));
        assertEquals("net/minecraft/server/level/ServerPlayer", NmsScan.normalize("net/minecraft/server/level/ServerPlayer"));
        assertEquals("v1_21_R1", NmsScan.versionOf("org/bukkit/craftbukkit/v1_21_R1/CraftServer"));
        assertNull(NmsScan.versionOf("org/bukkit/craftbukkit/CraftServer"));
    }

    @Test
    void obfuscationAndNamingStyleHeuristics() {
        assertTrue(NmsScan.obfMember("a"));
        assertTrue(NmsScan.obfMember("ab"));
        assertTrue(NmsScan.obfMember("b2"));
        assertFalse(NmsScan.obfMember("getX"));
        assertFalse(NmsScan.obfMember("x"), "좌표 필드 x·y·z 는 읽을 수 있는 이름");
        assertFalse(NmsScan.obfMember("id"));
        assertTrue(NmsScan.obfClass("net/minecraft/core/HolderLookup$a"));
        assertFalse(NmsScan.obfClass("net/minecraft/core/HolderLookup$Provider"));
        assertFalse(NmsScan.obfClass("net/minecraft/core/BlockPos"));

        assertEquals(NmsScan.Style.SPIGOT, NmsScan.styleOf("net/minecraft/server/level/EntityPlayer"));
        assertEquals(NmsScan.Style.SPIGOT, NmsScan.styleOf("net/minecraft/network/protocol/game/PacketPlayOutChat"));
        assertEquals(NmsScan.Style.MOJANG, NmsScan.styleOf("net/minecraft/server/level/ServerPlayer"));
        assertEquals(NmsScan.Style.MOJANG, NmsScan.styleOf("net/minecraft/network/protocol/game/ClientboundSystemChatPacket"));
        assertEquals(NmsScan.Style.OTHER, NmsScan.styleOf("net/minecraft/world/item/ItemStack"), "두 체계가 같은 이름은 어느 쪽으로도 세지 않는다");
    }

    @Test
    void aCompiledPluginIsCountedByGroupStyleReflectionAndLiterals() throws Exception {
        Map<String, String> sources = Map.of(
                "org/bukkit/craftbukkit/v1_20_R1/entity/CraftPlayer.java",
                "package org.bukkit.craftbukkit.v1_20_R1.entity; public class CraftPlayer { public net.minecraft.server.level.EntityPlayer getHandle() { return null; } }",
                "net/minecraft/server/level/EntityPlayer.java",
                "package net.minecraft.server.level; public class EntityPlayer { public net.minecraft.server.network.PlayerConnection b; public void a(Object o) {} public int getX() { return 0; } }",
                "net/minecraft/server/network/PlayerConnection.java",
                "package net.minecraft.server.network; public class PlayerConnection { public void a(Object packet) {} }",
                "net/minecraft/core/HolderLookup.java",
                "package net.minecraft.core; public class HolderLookup { public static class a {} }",
                "demo/Plugin.java", """
                package demo;
                public class Plugin {
                    Object handle(org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer p) {
                        net.minecraft.server.level.EntityPlayer h = p.getHandle();
                        h.b.a(new Object());
                        h.a(null);
                        return new net.minecraft.core.HolderLookup.a();
                    }
                    Class<?> byName(Object server) throws Exception {
                        String v = server.getClass().getPackage().getName();
                        return Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer").getDeclaredMethod("getHandle").getDeclaringClass();
                    }
                    String literal() { return "net.minecraft.world.entity.player.Player"; }
                }
                """);
        Path classes = Files.createDirectory(tmp.resolve("classes"));
        compile(sources, classes);

        JarScan s = new JarScan("demo.jar", false);
        NmsScan.addClass(s, ClassRefs.parse(Files.readAllBytes(classes.resolve("demo/Plugin.class"))));

        assertTrue(s.classes(Group.CRAFTBUKKIT).contains("org/bukkit/craftbukkit/entity/CraftPlayer"), "접미사는 지워서 센다: " + s.classes);
        assertTrue(s.members(Group.CRAFTBUKKIT).contains("org/bukkit/craftbukkit/entity/CraftPlayer#getHandle"));
        assertTrue(s.classes(Group.NMS).contains("net/minecraft/server/level/EntityPlayer"));
        assertTrue(s.members(Group.NMS).contains("net/minecraft/server/level/EntityPlayer#a"));
        assertTrue(s.versions.contains("v1_20_R1"));
        assertTrue(s.obfMembers >= 2, "필드 b, 메서드 a 는 난독화처럼 보인다: " + s.obfMembers);
        assertTrue(s.obfInnerClasses >= 1, "HolderLookup$a");
        assertTrue(s.spigotClasses >= 1);
        assertTrue(s.reflectionCalls >= 2, "forName·getDeclaredMethod: " + s.reflectionCalls);
        assertTrue(s.usesPackageNameForVersion);
        assertTrue(s.fragmentLiterals.stream().anyMatch(f -> f.startsWith("org.bukkit.craftbukkit.")), s.fragmentLiterals.toString());
        assertTrue(s.nameLiterals.contains("net.minecraft.world.entity.player.Player"), s.nameLiterals.toString());
        assertTrue(s.touchesServerInternals());
        assertEquals("Spigot(난독화)", s.namespaceGuess());

        // 보고서가 이 값으로 그려진다(예외 없이)
        String md = NmsReport.render(List.of(s));
        assertTrue(md.contains("CraftPlayer"), md);
        assertNotNull(md);
    }

    @Test
    void aJarWithoutServerInternalsIsNotCounted() {
        JarScan s = new JarScan("clean.jar", true);
        assertFalse(s.touchesServerInternals());
        assertEquals("—", s.namespaceGuess());
    }

    private static void compile(Map<String, String> sources, Path out) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "시험은 JDK 에서 돈다");
        Path src = out.resolveSibling("src");
        for (var e : sources.entrySet()) {
            Path f = src.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
        }
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null)) {
            List<Path> files;
            try (var walk = Files.walk(src)) {
                files = walk.filter(p -> p.toString().endsWith(".java")).toList();
            }
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(files);
            Boolean ok = javac.getTask(null, fm, null, List.of("-proc:none", "-d", out.toString()), null, units).call();
            assertTrue(ok, "합성 소스가 컴파일돼야 한다");
        }
    }
}

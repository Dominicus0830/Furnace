// Furnace 네이티브 플러그인의 클래스로더에는 호환 클래스(org.bukkit.* 등)가 보이지 않는다 — 지향점은 네이티브 플러그인이 Furnace API 만 쓰는 것.
// Bukkit·Paper(호환 형식) 플러그인은 그대로 본다. 서버 모듈 시험 클래스패스의 org.bukkit.HiddenProbe 가 "서버 jar 에 실린 호환 클래스" 역할을 한다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeCompatHidingTest {

    @TempDir Path tmp;
    private PluginBootstrapper boot;

    @AfterEach
    void tearDown() {
        if (boot != null) boot.prepared().forEach(p -> PluginManagerImpl.closeQuietly(p.loader()));
    }

    private PluginManagerImpl.PluginClassLoader nativeLoader(PluginSettings settings, Class<?>... extra) throws Exception {
        Path dir = tmp.resolve("plugins" + System.nanoTime());
        Class<?>[] classes = new Class<?>[extra.length + 1];
        classes[0] = TestPlugins.Alpha.class;
        System.arraycopy(extra, 0, classes, 1, extra.length);
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml",
                "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", classes);
        boot = PluginBootstrapper.bootstrap(dir, settings, List.of());
        return boot.prepared().get(0).loader();
    }

    @Test
    void nativePluginsDoNotSeeCompatClassesOfTheServerJar() throws Exception {
        var loader = nativeLoader(PluginSettings.DEFAULT);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.bukkit.HiddenProbe"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bukkit.HiddenProbe", true, loader));
        for (String n : List.of("io.papermc.Whatever", "com.destroystokyo.paper.Whatever", "org.domi.furnace.compat.paper.CompatFormat",
                "org.objectweb.asm.ClassReader", "net.minecraft.server.MinecraftServer")) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(n), n);
        }
        // Furnace API 와 서버 클래스는 그대로 보인다
        assertSame(org.domi.furnace.api.plugin.FurnacePlugin.class, loader.loadClass("org.domi.furnace.api.plugin.FurnacePlugin"));
        assertSame(String.class, loader.loadClass("java.lang.String"));
    }

    @Test
    void aNativePluginsOwnCopyOfAHiddenNameWinsInsteadOfBeingShadowedByTheServer() throws Exception {
        var loader = nativeLoader(PluginSettings.DEFAULT, org.bukkit.HiddenProbe.class);
        Class<?> own = loader.loadClass("org.bukkit.HiddenProbe");
        assertNotSame(org.bukkit.HiddenProbe.class, own, "자기 jar 의 클래스가 서버 쪽 사본 대신 올라온다");
        assertSame(loader, own.getClassLoader());
    }

    @Test
    void theTestHookExposesThemAgain() throws Exception {
        var loader = nativeLoader(PluginSettings.DEFAULT.withExposeCompatToNative(true));
        assertSame(org.bukkit.HiddenProbe.class, loader.loadClass("org.bukkit.HiddenProbe"));
    }

    @Test
    void compatFormatPluginsStillSeeThem() throws Exception {
        Path dir = tmp.resolve("plugins-fmt");
        PluginSystemTest.jar(dir.resolve("b.jar"), "fake.cfg", "name=Beta\nmain=org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        boot = PluginBootstrapper.bootstrap(dir, PluginSettings.DEFAULT, List.of(new PluginFormatSpiTest.FakeFormat()));
        var loader = boot.prepared().get(0).loader();
        assertEquals("Beta", boot.prepared().get(0).description().name());
        assertSame(org.bukkit.HiddenProbe.class, loader.loadClass("org.bukkit.HiddenProbe"), "호환 형식 플러그인의 로더는 부모(서버 jar)의 호환 클래스를 본다");
    }
}

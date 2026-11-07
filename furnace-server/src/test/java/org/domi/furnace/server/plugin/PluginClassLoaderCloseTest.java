package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 변환기가 있는 플러그인 로더를 닫은 뒤에는 jar 를 다시 열지 않고, 변환기 없는 로더(URLClassLoader)처럼 찾기가 실패한다(findLocal = 이 jar 만 찾기). */
class PluginClassLoaderCloseTest {
    @TempDir Path dir;

    @Test
    void closedLoaderDoesNotReopenJar() throws Exception {
        Path src = dir.resolve("src/rev");
        Files.createDirectories(src);
        Files.writeString(src.resolve("A.java"), "package rev; public class A {}");
        Files.writeString(src.resolve("B.java"), "package rev; public class B {}");
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, jc.run(null, null, null, "-d", out.toString(), src.resolve("A.java").toString(), src.resolve("B.java").toString()));
        Path jar = dir.resolve("p.jar");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String n : List.of("rev/A.class", "rev/B.class")) {
                jo.putNextEntry(new JarEntry(n));
                jo.write(Files.readAllBytes(out.resolve(n)));
                jo.closeEntry();
            }
        }
        var withT = new PluginManagerImpl.PluginClassLoader(jar, null, false, List.of(), (n, b) -> null);
        assertEquals("rev.A", withT.loadClass("rev.A").getName());
        withT.close();
        assertNull(withT.findLocal("rev.B"), "닫은 로더가 클래스를 찾았다");
        Field f = PluginManagerImpl.PluginClassLoader.class.getDeclaredField("jarFile");
        f.setAccessible(true);
        assertNull(f.get(withT), "닫은 로더가 jar 를 다시 열었다");

        var plain = new PluginManagerImpl.PluginClassLoader(jar, null, false, List.of(), null);
        plain.loadClass("rev.A");
        plain.close();
        assertNull(plain.findLocal("rev.B"));
    }
}

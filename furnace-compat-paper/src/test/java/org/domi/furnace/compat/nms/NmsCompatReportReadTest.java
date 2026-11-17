package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spigot 이름 대응표가 필요한지의 판정(서버가 예전에 따로 jar 를 훑던 일)을 어댑터의 한 번 읽기에 합친 뒤의 동작 —
 * 판정 기준이 예전과 같고, 필요할 때만 대응표를 묻는다.
 */
class NmsCompatReportReadTest {

    @TempDir
    Path tmp;

    private Path jar(String name, String ns, String content) throws IOException {
        Path p = tmp.resolve(name);
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (ns != null) man.getMainAttributes().putValue("paperweight-mappings-namespace", ns);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p), man)) {
            out.putNextEntry(new JarEntry("x/A.class"));
            out.write(("Êþ" + content).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return p;
    }

    @Test
    void needsLooksAtTheManifestAndNmsReferences() throws Exception {
        assertTrue(NmsCompatReport.read(jar("a.jar", null, "net/minecraft/server/level/EntityPlayer")).needsSpigotMappings());
        assertTrue(NmsCompatReport.read(jar("b.jar", "spigot", "net.minecraft.server.level.EntityPlayer")).needsSpigotMappings(), "리터럴·리플렉션 문자열(점 형식)도 센다");
        assertFalse(NmsCompatReport.read(jar("c.jar", "mojang", "net/minecraft/server/level/ServerPlayer")).needsSpigotMappings(), "Mojang 이름이라고 밝힌 jar");
        assertFalse(NmsCompatReport.read(jar("d.jar", null, "org/bukkit/entity/Player")).needsSpigotMappings(), "NMS 를 가리키지 않는 jar");
        assertFalse(NmsCompatReport.read(jar("e.jar", null, "org/bukkit/craftbukkit/CraftServer")).needsSpigotMappings(), "CraftBukkit 만 가리키면 net.minecraft 대응표는 필요 없다");
    }

    @Test
    void forJarAsksForMappingsOnlyWhenTheJarNeedsThem() throws Exception {
        AtomicInteger asked = new AtomicInteger();
        NmsClassTransformer.forJar("A", jar("a.jar", null, "net/minecraft/server/level/EntityPlayer"), NmsClassTransformer.Options.DEFAULT,
                () -> {
                    asked.incrementAndGet();
                    return null;
                });
        assertEquals(1, asked.get(), "Spigot 이름 NMS jar 는 대응표를 묻는다(null 이면 바꾸지 않고 그대로 간다)");
        NmsClassTransformer.forJar("D", jar("d.jar", null, "org/bukkit/entity/Player"), NmsClassTransformer.Options.DEFAULT, () -> {
            asked.incrementAndGet();
            return null;
        });
        NmsClassTransformer.forJar("C", jar("c.jar", "mojang", "net/minecraft/server/level/ServerPlayer"), NmsClassTransformer.Options.DEFAULT, () -> {
            asked.incrementAndGet();
            return null;
        });
        assertEquals(1, asked.get(), "NMS 를 안 쓰는 jar·Mojang 이름 jar 는 묻지 않는다(서버가 받기를 시도하지도 않는다)");
    }

    @Test
    void adapterEntryPointAsksTheServerForMappingFilesOnlyForNmsJars() throws Exception {
        var format = new org.domi.furnace.compat.paper.CompatFormat();
        var desc = format.readDescriptor("plugin.yml", "name: Nms\nversion: '1'\nmain: x.A\napi-version: '1.21'\n");
        AtomicInteger asked = new AtomicInteger();
        // 서버가 줄 파일이 없으면(null) 바꾸지 않고 그대로 올린다
        assertTrue(format.transformer(desc, jar("n.jar", null, "net/minecraft/server/level/EntityPlayer"), java.util.Map.of(), () -> {
            asked.incrementAndGet();
            return null;
        }) != null);
        assertEquals(1, asked.get());
        format.transformer(desc, jar("o.jar", null, "org/bukkit/entity/Player"), java.util.Map.of(), () -> {
            asked.incrementAndGet();
            return "x";
        });
        assertEquals(1, asked.get(), "NMS 를 안 쓰는 jar 에는 서버가 받기를 시도하지도 않는다");
        // 읽을 수 없는 경로가 오면 경고하고 바꾸지 않는다(던지지 않는다)
        format.transformer(desc, jar("p.jar", null, "net/minecraft/server/level/EntityPlayer"), java.util.Map.of(), () -> "/no/a" + java.io.File.pathSeparator + "/no/b"
                + java.io.File.pathSeparator + "/no/c");
    }

    @Test
    void scanStillEqualsReadPlusAnalyze() throws Exception {
        Path j = jar("s.jar", null, "net/minecraft/server/level/EntityPlayer");
        NmsCompatReport a = NmsCompatReport.scan("S", j, NmsSymbols.RUNTIME, null);
        NmsCompatReport b = NmsCompatReport.analyze("S", NmsCompatReport.read(j), NmsSymbols.RUNTIME, null);
        assertEquals(a, b);
    }
}

package org.domi.furnace.compat.paper.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** 시험용 플러그인 jar 만들기 — 설명·자원 텍스트와 시험 클래스패스의 클래스 파일을 담는다. */
public final class Jars {

    private Jars() {}

    public static Path write(Path jar, Map<String, String> textEntries, Class<?>... classes) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, String> e : textEntries.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (Class<?> c : classes) {
                String path = c.getName().replace('.', '/') + ".class";
                try (InputStream in = c.getClassLoader().getResourceAsStream(path)) {
                    out.putNextEntry(new JarEntry(path));
                    out.write(in.readAllBytes());
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    public static Path bukkit(Path jar, String descriptorName, String descriptor, Class<?>... classes) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(descriptorName, descriptor);
        return write(jar, m, classes);
    }

    public static Path bukkit(Path jar, String descriptor, Map<String, String> extra, Class<?>... classes) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("plugin.yml", descriptor);
        m.putAll(extra);
        return write(jar, m, classes);
    }
}

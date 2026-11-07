package org.domi.furnace.compat.paper.fixtures;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * 자바 소스를 그 자리에서 컴파일해 플러그인 jar 로 묶는다. 시험 클래스패스에 없는 클래스만 들어 있어서 플러그인 클래스로더가 직접 올린다 —
 * 시험 클래스패스의 클래스를 jar 에 복사하는 {@link Jars} 는 부모 클래스로더가 먼저 찾아 "플러그인 클래스로더가 올린 클래스" 를 만들 수 없다.
 */
public final class JarCompiler {

    private JarCompiler() {}

    /** @param sources 클래스 이름(패키지 포함) → 소스. */
    public static Path jar(Path jar, String pluginYml, Map<String, String> sources) throws IOException {
        return jar(jar, pluginYml, sources, java.util.Set.of());
    }

    /**
     * @param stubs 컴파일에만 쓰고 jar 에는 넣지 않을 클래스 이름 접두(패키지 포함, 예: 서버에 실제로는 없는 Spigot 접미사 이름
     *              {@code org.bukkit.craftbukkit.v1_21_R1.} — 실제 플러그인이 Spigot API 로 컴파일된 모양을 흉내 낸다)
     */
    public static Path jar(Path jar, String pluginYml, Map<String, String> sources, java.util.Set<String> stubs) throws IOException {
        Map<String, byte[]> classes = compile(sources);
        classes.keySet().removeIf(n -> stubs.stream().anyMatch(n::startsWith));
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("plugin.yml"));
            out.write(pluginYml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey().replace('.', '/') + ".class"));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private static Map<String, byte[]> compile(Map<String, String> sources) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("시험 JVM 에 컴파일러가 없다(JDK 필요)");
        Map<String, java.io.ByteArrayOutputStream> out = new LinkedHashMap<>();
        var fm = new javax.tools.ForwardingJavaFileManager<>(compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
                var bytes = new java.io.ByteArrayOutputStream();
                out.put(className, bytes);
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public java.io.OutputStream openOutputStream() { return bytes; }
                };
            }
        };
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            units.add(new SimpleJavaFileObject(URI.create("string:///" + e.getKey().replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) { return e.getValue(); }
            });
        }
        StringWriter log = new StringWriter();
        boolean ok = compiler.getTask(log, fm, null, List.of("-classpath", System.getProperty("java.class.path"), "--release", "21", "-proc:none"), null, units).call();
        if (!ok) throw new IllegalStateException("시험 플러그인 컴파일 실패:\n" + log);
        Map<String, byte[]> classes = new LinkedHashMap<>();
        out.forEach((k, v) -> classes.put(k, v.toByteArray()));
        return classes;
    }
}

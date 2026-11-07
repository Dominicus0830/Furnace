package org.domi.furnace.compat.realworld;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Gradle 설정 {@code realWorld}(Maven Central 의 실제 플러그인·플러그인 라이브러리 jar)가 시스템 속성으로 넘겨 주는 경로 목록. */
public final class RealJars {

    public static final String PROPERTY = "furnace.realworld.jars";

    private RealJars() {}

    public static List<Path> list() {
        String v = System.getProperty(PROPERTY);
        if (v == null || v.isBlank()) throw new IllegalStateException("시스템 속성 " + PROPERTY + " 가 없다 — gradle 의 realWorld 설정으로 실행해야 한다");
        List<Path> out = new ArrayList<>();
        for (String s : v.split(File.pathSeparator)) if (!s.isBlank()) out.add(Path.of(s));
        out.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return out;
    }

    public static Path find(String namePrefix) {
        for (Path p : list()) if (p.getFileName().toString().startsWith(namePrefix)) return p;
        throw new IllegalStateException("jar 없음: " + namePrefix);
    }

    private static final Pattern NAME = Pattern.compile("(?m)^name:\\s*['\"]?([^'\"\\s#]+)");

    /** jar 의 plugin.yml(또는 paper-plugin.yml) {@code name}. 플러그인이 아니면 {@code null}. */
    public static String pluginName(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (String entry : new String[] {"plugin.yml", "paper-plugin.yml"}) {
                var e = jf.getJarEntry(entry);
                if (e == null) continue;
                String text = new String(jf.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = NAME.matcher(text);
                if (m.find()) return m.group(1);
            }
            return null;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}

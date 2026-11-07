// 시험 서버의 plugins/ 폴더를 채운다.
// 클래스패스 플러그인은 "설명 파일 + 자원(클래스 제외)" 만 담은 jar 를 만들어 넣는다. 플러그인 클래스로더는 부모(서버·시험 클래스패스)를
// 먼저 보므로 메인 클래스는 시험 코드와 같은 클래스가 되고(server.plugin(MyPlugin.class) 가 그대로 캐스트된다), 설명 파일·부트스트랩·
// 데이터 팩(datapack/)은 운영 서버와 같은 경로(PluginBootstrapper)로 읽힌다.
package org.domi.furnace.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginDescription;

final class PluginJars {
    private PluginJars() {}

    /** 서버 PluginBootstrapper.DESCRIPTOR_NAMES 와 같은 순서. */
    static final List<String> DESCRIPTOR_NAMES = List.of("furnace-plugin.yml", "furnace-plugin.yaml", "furnace-plugin.toml", "furnace-plugin.json");

    static void stage(List<Class<? extends FurnacePlugin>> classpath, List<Path> jars, Path pluginsDir) throws IOException {
        Set<String> names = new HashSet<>();
        for (Path jar : jars) {
            Path target = pluginsDir.resolve(jar.getFileName().toString());
            if (!names.add(target.getFileName().toString())) throw new IllegalArgumentException("같은 이름의 플러그인 jar: " + jar);
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
        }
        for (Class<? extends FurnacePlugin> main : classpath) {
            Found f = find(main);
            String name = f != null ? f.description.name() : main.getSimpleName();
            String file = "classpath-" + name + ".jar";
            if (!names.add(file)) throw new IllegalArgumentException("같은 이름의 플러그인을 두 번 넣었다: " + name);
            writeJar(pluginsDir.resolve(file), main, f);
        }
    }

    /** 찾은 설명 파일: 이름(형식), 내용, 자원 뿌리(폴더 또는 jar — 없으면 null). */
    private record Found(String fileName, byte[] bytes, PluginDescription description, URL url) {}

    /** main 이 이 클래스인 설명 파일을 클래스패스에서 찾는다. */
    private static Found find(Class<?> main) throws IOException {
        ClassLoader cl = main.getClassLoader();
        for (String n : DESCRIPTOR_NAMES) {
            for (URL url : Collections.list(cl.getResources(n))) {
                byte[] bytes;
                try (InputStream in = url.openStream()) {
                    bytes = in.readAllBytes();
                }
                PluginDescription d;
                try {
                    d = PluginDescription.read(ConfigFormat.fromFileName(n).orElse(ConfigFormat.YAML).parse(new String(bytes, StandardCharsets.UTF_8)));
                } catch (RuntimeException e) {
                    continue;   // 다른 플러그인의 망가진 설명 — 그 플러그인을 올릴 때 서버가 알린다
                }
                if (main.getName().equals(d.main())) return new Found(n, bytes, d, url);
            }
        }
        return null;
    }

    private static void writeJar(Path out, Class<?> main, Found f) throws IOException {
        try (OutputStream os = Files.newOutputStream(out); JarOutputStream jar = new JarOutputStream(os)) {
            if (f == null) {
                // 설명 파일이 없는 시험용 플러그인: 최소 설명을 만든다
                String yml = "name: " + main.getSimpleName() + "\nversion: test\nmain: " + main.getName() + "\n";
                put(jar, "furnace-plugin.yml", yml.getBytes(StandardCharsets.UTF_8));
                return;
            }
            put(jar, f.fileName, f.bytes);
            copyResources(jar, f);
        }
    }

    /** 설명 파일과 같은 뿌리의 자원(클래스·다른 설명 파일 제외)을 담는다 — config.yml·datapack/ 등. */
    private static void copyResources(JarOutputStream jar, Found f) throws IOException {
        URL url = f.url;
        if ("file".equals(url.getProtocol())) {
            Path root;
            try {
                root = Path.of(url.toURI()).getParent();
            } catch (URISyntaxException e) {
                return;
            }
            try (var s = Files.walk(root)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (skip(rel)) continue;
                    put(jar, rel, Files.readAllBytes(p));
                }
            }
        } else if ("jar".equals(url.getProtocol())) {
            var conn = (JarURLConnection) url.openConnection();
            conn.setUseCaches(false);
            try (JarFile jf = conn.getJarFile()) {
                for (JarEntry e : Collections.list(jf.entries())) {
                    if (e.isDirectory() || skip(e.getName())) continue;
                    try (InputStream in = jf.getInputStream(e)) {
                        put(jar, e.getName(), in.readAllBytes());
                    }
                }
            }
        }
    }

    private static boolean skip(String rel) {
        return rel.endsWith(".class") || DESCRIPTOR_NAMES.contains(rel) || rel.startsWith("META-INF/");
    }

    private static void put(JarOutputStream jar, String name, byte[] bytes) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(bytes);
        jar.closeEntry();
    }
}

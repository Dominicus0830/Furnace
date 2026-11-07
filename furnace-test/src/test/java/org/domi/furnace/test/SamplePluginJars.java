// 시험용: 소스 한 개짜리 플러그인을 그 자리에서 컴파일해 jar 로 묶는다(클래스가 시험 클래스패스에 없어야 jar 경로를 본다).
package org.domi.furnace.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

final class SamplePluginJars {
    private SamplePluginJars() {}

    static Path build(Path dir, String name, String mainClass, String source) throws Exception {
        Path src = dir.resolve("src").resolve(mainClass.replace('.', '/') + ".java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        Path out = Files.createDirectories(dir.resolve("classes"));
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-cp", System.getProperty("java.class.path"), "-d", out.toString(), src.toString());
        if (rc != 0) throw new IllegalStateException("컴파일 실패 " + rc);
        Path jar = dir.resolve(name + ".jar");
        try (JarOutputStream j = new JarOutputStream(Files.newOutputStream(jar))) {
            j.putNextEntry(new JarEntry("furnace-plugin.yml"));
            j.write(("name: " + name + "\nversion: '1'\nmain: " + mainClass + "\n").getBytes(StandardCharsets.UTF_8));
            j.closeEntry();
            try (var s = Files.walk(out)) {
                for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    j.putNextEntry(new JarEntry(out.relativize(p).toString().replace('\\', '/')));
                    j.write(Files.readAllBytes(p));
                    j.closeEntry();
                }
            }
        }
        return jar;
    }
}

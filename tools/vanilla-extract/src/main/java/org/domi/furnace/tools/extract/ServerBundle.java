package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 서버 번들러 jar(`java -jar server.jar` 로 뜨는 그것)를 푼다.
 * 안에 META-INF/versions.list(줄: `해시 <TAB> id <TAB> 경로`)가 가리키는 내부 서버 jar 한 개와, META-INF/libraries/**.jar 가 있다.
 */
public final class ServerBundle {
    /** 푼 결과. */
    public record Unpacked(Path serverJar, List<Path> libraries) {}

    private ServerBundle() {}

    public static Unpacked unpack(Path bundler, Path dir) throws IOException {
        Files.createDirectories(dir);
        List<Path> libs = new ArrayList<>();
        Path inner = null;
        try (ZipFile zf = new ZipFile(bundler.toFile())) {
            ZipEntry list = zf.getEntry("META-INF/versions.list");
            if (list == null) throw new IOException("번들러 jar 가 아니다(META-INF/versions.list 없음): " + bundler);
            String[] cols = new String(zf.getInputStream(list).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip().split("\t");
            if (cols.length < 3) throw new IOException("versions.list 형식이 다르다: " + String.join("|", cols));
            String innerPath = "META-INF/versions/" + cols[2].strip();
            ZipEntry ie = zf.getEntry(innerPath);
            if (ie == null) throw new IOException("내부 서버 jar 가 없다: " + innerPath);
            inner = dir.resolve("server-inner.jar");
            copy(zf, ie, inner);
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("META-INF/libraries/") || !e.getName().endsWith(".jar")) continue;
                Path target = dir.resolve("libs").resolve(e.getName().substring("META-INF/libraries/".length()));
                copy(zf, e, target);
                libs.add(target);
            }
        }
        libs.sort(Path::compareTo);
        return new Unpacked(inner, libs);
    }

    private static void copy(ZipFile zf, ZipEntry e, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (InputStream in = zf.getInputStream(e)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

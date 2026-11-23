// T-229: "이 클래스가 이미 shim 소스에 있나" 판정 — 손 shim(src/main/java), 패킷·등록부 생성물(build/generated), 손 조각(src/shims/java) 폴더를 본다.
// 안쪽 클래스(바깥$안쪽)는 바깥 파일에 그 이름의 선언이 있는지 정규식으로 본다(패킷 생성기와 같은 방식).
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ShimSources {
    private final List<Path> roots;
    private final Map<Path, String> cache = new HashMap<>();

    ShimSources(List<Path> roots) { this.roots = roots; }

    /** 내부 이름(net/minecraft/a/B$C) 의 선언이 이 폴더들 중 하나에 있나. */
    boolean has(String internalName) {
        for (Path r : roots) if (in(r, internalName)) return true;
        return false;
    }

    /** 이 폴더(하나)에 선언이 있나. */
    boolean in(Path root, String internalName) {
        String top = topLevel(internalName);
        Path p = root.resolve(top + ".java");
        if (!Files.isRegularFile(p)) return false;
        if (top.equals(internalName)) return true;
        String text = cache.computeIfAbsent(p, ShimSources::read);
        String inner = internalName.substring(internalName.lastIndexOf('$') + 1);
        return Pattern.compile("\\b(class|interface|enum|record)\\s+" + Pattern.quote(inner) + "\\b").matcher(text).find();
    }

    /** 최상위 파일이 이 폴더에 있나. */
    boolean topIn(Path root, String internalName) {
        return Files.isRegularFile(root.resolve(topLevel(internalName) + ".java"));
    }

    static String topLevel(String internalName) {
        int d = internalName.indexOf('$');
        return d < 0 ? internalName : internalName.substring(0, d);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

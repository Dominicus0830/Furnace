// 서버 jar 에 통째로 실린 호환 모듈 jar(N-lib-1). fat jar 는 호환 모듈 클래스를 풀어 넣지 않고 jar 그대로 META-INF/furnace/compat/ 에 싣는다 —
// 서버 로더가 호환 클래스를 보지 않게 해서, 꺼낸 jar 를 서버 제공 라이브러리 로더(SharedLibraryLoader)의 첫 항목으로 붙이기 위해서다.
// 그래야 호환 계층이 authlib·DataFixerUpper·Brigadier·Adventure 타입을 서명과 본문에 쓸 수 있다(Paper 는 라이브러리와 서버 구현이 한 로더에 있다).
// 시험·개발 실행(gradle 클래스패스에 호환 모듈이 바로 있음)에는 색인이 없어 예전처럼 서버 로더에서 찾는다.
package org.domi.furnace.server.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class CompatJars {

    /** jar 이름 목록(한 줄에 하나). 이 자원이 있으면 호환 모듈이 실려 있다. */
    static final String INDEX = "META-INF/furnace/compat/index.txt";
    private static final String DIR = "META-INF/furnace/compat/";

    private final ClassLoader source;
    private final List<String> names;

    private CompatJars(ClassLoader source, List<String> names) {
        this.source = source;
        this.names = List.copyOf(names);
    }

    /** 실린 호환 jar 목록. 없으면 null(클래스는 읽지 않는다). */
    static CompatJars find(ClassLoader source) {
        try (InputStream in = source.getResourceAsStream(INDEX)) {
            if (in == null) return null;
            List<String> names = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String n = line.trim();
                if (n.isEmpty() || n.startsWith("#")) continue;
                if (n.contains("/") || n.contains("\\") || n.contains("..")) throw new IOException("호환 jar 이름이 이상하다: " + n);
                names.add(n);
            }
            return names.isEmpty() ? null : new CompatJars(source, names);
        } catch (IOException e) {
            throw new IllegalStateException("호환 모듈 색인을 읽지 못했다", e);
        }
    }

    List<String> names() { return names; }

    /**
     * jar 들을 dir 아래에 꺼내 파일 경로로 돌려준다(색인 순서). 파일 이름에 내용 sha256 앞 16자를 붙여, 같은 내용이면 다시 쓰지 않고 판이 바뀌면 새 파일을 쓴다
     * (이미 열린 옛 파일을 덮어쓰지 않는다).
     */
    List<Path> extract(Path dir) throws IOException {
        Files.createDirectories(dir);
        List<Path> out = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            byte[] bytes;
            try (InputStream in = source.getResourceAsStream(DIR + name)) {
                if (in == null) throw new IOException("색인에 있는 호환 jar 가 없다: " + name);
                bytes = in.readAllBytes();
            }
            String base = name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
            Path target = dir.resolve(base + "-" + sha256(bytes).substring(0, 16) + ".jar");
            removeOldVersions(dir, base, target);
            String hash = sha256(bytes);
            if (!Files.exists(target) || !hash.equals(sha256(Files.readAllBytes(target)))) {
                Path tmp = Files.createTempFile(dir, base, ".tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            out.add(target);
        }
        return out;
    }

    /** 같은 jar 의 옛 판(이름-다른해시.jar)을 지운다. 열려 있어 못 지우면(윈도) 다음 시작에 다시 해 본다. */
    private static void removeOldVersions(Path dir, String base, Path keep) {
        java.util.regex.Pattern old = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(base) + "-[0-9a-f]{16}\\.jar");
        try (var files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.equals(keep) && old.matcher(p.getFileName().toString()).matches()) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // 열린 파일 — 다음에
                    }
                }
            }
        } catch (IOException ignored) {
            // 목록을 못 읽으면 정리만 건너뛴다
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

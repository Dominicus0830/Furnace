// 플러그인 설명 파일의 libraries(Maven 좌표)를 받아 로컬 캐시에 두고 jar 경로 목록을 돌려 준다.
//  - Maven Central 만 쓴다(주소는 코드에 고정). plugins.libraries.enabled=true 일 때만 부른다.
//  - 체크섬: 설명 파일의 sha256 > 잠금 파일(libraries.lock, 처음 받을 때 서버가 기록) > Central 이 주는 .sha1.
//    둘 중 하나와 다르면 그 플러그인은 불러오지 않는다. plugins.libraries.require-checksum=true 면 처음 받는 jar 도 위 두 가지 기록이 없으면 거부한다.
//  - 전이 의존(plugins.libraries.transitive): pom 의 compile/runtime 의존을 가까운 쪽 버전 우선으로 따라간다.
//  - 캐시는 Maven 저장소 모양(<그룹/>/<아티팩트>/<버전>/)이고 매번 해시를 다시 확인한다. offline=true 면 네트워크에 나가지 않는다.
package org.domi.furnace.server.plugin.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.api.plugin.PluginDescription.Library;
import org.slf4j.Logger;

public final class LibraryResolver {

    /** 라이브러리를 받아 오는 유일한 저장소. */
    public static final URI CENTRAL = URI.create("https://repo.maven.apache.org/maven2/");
    public static final String LOCK_FILE = "libraries.lock";
    private static final int MAX_POM_DEPTH = 10;
    private static final long MAX_POM_BYTES = 4L * 1024 * 1024;

    /** 바이트를 가져온다. 없는 파일(404)은 null. */
    public interface Fetcher {
        byte[] get(URI uri, long maxBytes) throws IOException;
    }

    /** 라이브러리를 풀지 못했다 — 그 플러그인은 불러오지 않는다. */
    public static class LibraryException extends Exception {
        public LibraryException(String message) { super(message); }

        public LibraryException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class NotFound extends LibraryException {
        NotFound(String message) { super(message); }
    }

    private final LibrarySettings settings;
    private final Path cache;
    private final URI repo;
    private final Fetcher fetcher;
    private final Logger log;
    private final Path lockFile;
    private final Map<String, String> lock = new TreeMap<>();
    private final Map<String, Effective> poms = new HashMap<>();

    public LibraryResolver(LibrarySettings settings, Path cache, Logger log) {
        this(settings, cache, CENTRAL, new HttpFetcher(settings.timeoutSeconds(), 1000), log);
    }

    /** 시험용: 다른 저장소·가져오기 구현. */
    LibraryResolver(LibrarySettings settings, Path cache, URI repo, Fetcher fetcher, Logger log) {
        this.settings = settings;
        this.cache = cache;
        this.repo = repo.toString().endsWith("/") ? repo : URI.create(repo + "/");
        this.fetcher = fetcher;
        this.log = log;
        this.lockFile = cache.resolve(LOCK_FILE);
        loadLock();
    }

    /**
     * 플러그인 하나의 라이브러리를 풀어 jar 경로를 돌려 준다(선언 순서, 전이 의존은 그 뒤). 하나라도 못 풀면 LibraryException.
     */
    public List<Path> resolve(String plugin, List<Library> declared) throws LibraryException {
        if (declared.isEmpty()) return List.of();
        record Item(Library lib, List<String> excludes, boolean direct) {}
        ArrayDeque<Item> queue = new ArrayDeque<>();
        for (Library l : declared) queue.add(new Item(l, List.of(), true));
        Set<String> seen = new HashSet<>();
        List<Path> jars = new ArrayList<>();
        while (!queue.isEmpty()) {
            Item it = queue.poll();
            Library lib = it.lib();
            if (!seen.add(lib.group() + ":" + lib.artifact() + ":" + (lib.classifier() == null ? "" : lib.classifier()))) continue;   // 가까운 쪽 우선
            Effective eff = null;
            if (settings.transitive()) {
                try {
                    eff = load(new MavenPom.Coord(lib.group(), lib.artifact(), lib.version()), 0);
                } catch (NotFound e) {
                    log.warn("[{}] {} 의 pom 이 없어 전이 의존은 건너뛴다", plugin, lib.coordinate());
                }
            }
            boolean hasJar = eff == null || !eff.packaging.equals("pom");
            if (hasJar) {
                try {
                    jars.add(ensureJar(plugin, lib));
                } catch (NotFound e) {
                    if (it.direct()) throw e;
                    log.warn("[{}] 전이 의존 {} 의 jar 가 Maven Central 에 없어 건너뛴다", plugin, lib.coordinate());
                    continue;
                }
            }
            if (eff != null) {
                for (Next n : dependenciesOf(plugin, lib, eff, it.excludes())) queue.add(new Item(n.lib(), n.excludes(), false));
            }
        }
        return jars;
    }

    // ------------------------------------------------------------------ 전이 의존

    private record Next(Library lib, List<String> excludes) {}

    // ------------------------------------------------------------------ jar

    private Path ensureJar(String plugin, Library lib) throws LibraryException {
        String coord = lib.coordinate();
        Path file = fileOf(lib, "jar");
        String locked = lock.get(coord);
        String pin = lib.sha256();
        if (pin != null && locked != null && !pin.equals(locked)) {
            throw new LibraryException("라이브러리 " + coord + " 의 설명 파일 sha256 이 잠금 파일(" + lockFile + ")의 기록과 다르다 — "
                    + "의도한 변경이면 잠금 파일에서 그 줄을 지워라");
        }
        String expected = pin != null ? pin : locked;
        if (Files.isRegularFile(file)) {
            String have = sha256(file);
            if (expected == null || expected.equals(have)) {
                if (expected == null) {
                    requireRecorded(coord);
                    record(coord, have);
                } else if (locked == null) {
                    record(coord, have);
                }
                return file;
            }
            log.warn("[{}] 캐시된 {} 의 해시가 기록과 다르다 — 지우고 다시 받는다", plugin, coord);
            deleteQuietly(file);
        }
        if (settings.offline()) throw new LibraryException("plugins.libraries.offline=true 인데 캐시에 " + coord + " 가 없다(" + file + ")");
        if (expected == null) requireRecorded(coord);
        URI uri = repo.resolve(relative(lib, "jar"));
        log.info("[{}] 라이브러리 {} 를 받는다", plugin, coord);
        byte[] body = fetch(uri, settings.maxSizeMb() * 1024L * 1024L, coord);
        if (body == null) throw new NotFound("Maven Central 에 " + coord + " 가 없다(" + uri + ")");
        String got = sha256(body);
        if (expected != null) {
            if (!expected.equals(got)) {
                throw new LibraryException("라이브러리 " + coord + " 의 sha256 이 " + (pin != null ? "설명 파일" : "잠금 파일") + "의 값과 다르다 (기대 " + expected + ", 실제 " + got + ")");
            }
        } else {
            verifySha1(coord, uri, body);
        }
        store(file, body);
        record(coord, got);
        return file;
    }

    private void requireRecorded(String coord) throws LibraryException {
        if (settings.requireChecksum()) {
            throw new LibraryException("plugins.libraries.require-checksum=true 인데 " + coord + " 의 sha256 기록이 없다 — 설명 파일 libraries 항목에 sha256 을 적거나 "
                    + lockFile + " 에 \"" + coord + "=<sha256>\" 을 더해라");
        }
    }

    private void verifySha1(String coord, URI jarUri, byte[] body) throws LibraryException {
        byte[] sum = fetch(URI.create(jarUri + ".sha1"), 4096, coord + ".sha1");
        if (sum == null) throw new LibraryException(coord + " 의 .sha1 체크섬이 Maven Central 에 없어 검증할 수 없다");
        String want = new String(sum, StandardCharsets.UTF_8).strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
        String got = hex(digest("SHA-1").digest(body));
        if (!want.equals(got)) throw new LibraryException(coord + " 가 Maven Central 의 .sha1 과 다르다 (기대 " + want + ", 실제 " + got + ")");
    }

    private byte[] fetch(URI uri, long max, String what) throws LibraryException {
        try {
            return fetcher.get(uri, max);
        } catch (IOException e) {
            throw new LibraryException(what + " 를 받지 못했다: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ pom

    /** 부모 pom·BOM 을 합친 pom 한 개. props·managed·deps 는 ${} 가 풀리기 전 값이다. */
    private static final class Effective {
        String packaging = "jar";
        final Map<String, String> props = new LinkedHashMap<>();
        final Map<String, MavenPom.Dep> managed = new LinkedHashMap<>();
        final List<MavenPom.Dep> deps = new ArrayList<>();
    }

    private Effective load(MavenPom.Coord c, int depth) throws LibraryException {
        if (depth > MAX_POM_DEPTH) throw new LibraryException("pom 의 부모·가져오기 단계가 너무 깊다: " + c);
        String key = c.group() + ":" + c.artifact() + ":" + c.version();
        Effective cached = poms.get(key);
        if (cached != null) return cached;
        byte[] xml = pomBytes(c);
        MavenPom pom;
        try {
            pom = MavenPom.parse(xml);
        } catch (Exception e) {
            throw new LibraryException(key + " 의 pom 을 읽지 못했다: " + e.getMessage(), e);
        }
        Effective parent = pom.parent == null ? null : load(pom.parent, depth + 1);
        Effective eff = new Effective();
        eff.packaging = pom.packaging;
        if (parent != null) {
            eff.props.putAll(parent.props);
            eff.managed.putAll(parent.managed);
            eff.deps.addAll(parent.deps);
        }
        eff.props.putAll(pom.properties);
        String g = pom.group != null ? pom.group : c.group();
        String v = pom.version != null ? pom.version : c.version();
        for (String p : new String[]{"project.", "pom."}) {
            eff.props.put(p + "groupId", g);
            eff.props.put(p + "artifactId", pom.artifact != null ? pom.artifact : c.artifact());
            eff.props.put(p + "version", v);
        }
        if (pom.parent != null) {
            eff.props.put("project.parent.version", pom.parent.version());
            eff.props.put("parent.version", pom.parent.version());
            eff.props.put("project.parent.groupId", pom.parent.group());
        }
        for (MavenPom.Dep d : pom.managed) {
            String dg = interpolate(d.group(), eff.props);
            String da = interpolate(d.artifact(), eff.props);
            if (dg == null || da == null) continue;
            if ("import".equals(d.scope()) && "pom".equals(d.type())) {
                String dv = interpolate(d.version(), eff.props);
                if (dv == null || dv.contains("${")) continue;
                Effective bom = load(new MavenPom.Coord(dg, da, dv), depth + 1);
                bom.managed.forEach((k, m) -> eff.managed.putIfAbsent(k, resolved(m, bom.props)));
            } else {
                eff.managed.put(dg + ":" + da, d);
            }
        }
        for (MavenPom.Dep d : pom.dependencies) {
            String dg = interpolate(d.group(), eff.props);
            String da = interpolate(d.artifact(), eff.props);
            if (dg == null || da == null) continue;
            eff.deps.removeIf(o -> dg.equals(interpolate(o.group(), eff.props)) && da.equals(interpolate(o.artifact(), eff.props))
                    && java.util.Objects.equals(d.classifier(), o.classifier()));
            eff.deps.add(d);
        }
        poms.put(key, eff);
        return eff;
    }

    private static MavenPom.Dep resolved(MavenPom.Dep d, Map<String, String> props) {
        return new MavenPom.Dep(interpolate(d.group(), props), interpolate(d.artifact(), props), interpolate(d.version(), props), d.scope(),
                d.optional(), d.classifier(), d.type(), d.exclusions());
    }

    private byte[] pomBytes(MavenPom.Coord c) throws LibraryException {
        Path file = cache.resolve(c.group().replace('.', '/')).resolve(c.artifact()).resolve(c.version())
                .resolve(c.artifact() + "-" + c.version() + ".pom");
        try {
            if (Files.isRegularFile(file)) return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new LibraryException(file + " 를 읽지 못했다: " + e.getMessage(), e);
        }
        String coord = c.group() + ":" + c.artifact() + ":" + c.version();
        for (String part : new String[]{c.group(), c.artifact(), c.version()}) {
            if (part == null || !part.matches("[A-Za-z0-9_.+-]+") || part.contains("..")) throw new LibraryException("pom 좌표가 올바르지 않다: " + coord);
        }
        if (settings.offline()) throw new NotFound("plugins.libraries.offline=true 인데 캐시에 " + coord + " 의 pom 이 없다");
        URI uri = repo.resolve(c.group().replace('.', '/') + "/" + c.artifact() + "/" + c.version() + "/" + c.artifact() + "-" + c.version() + ".pom");
        byte[] body = fetch(uri, MAX_POM_BYTES, coord + " pom");
        if (body == null) throw new NotFound("Maven Central 에 " + coord + " 의 pom 이 없다");
        verifySha1(coord + " pom", uri, body);
        store(file, body);
        return body;
    }

    /** 의존 하나씩 Library 로 바꿔 돌려 준다(건너뛴 것은 로그). */
    private List<Next> dependenciesOf(String plugin, Library from, Effective eff, List<String> inherited) {
        List<Next> out = new ArrayList<>();
        for (MavenPom.Dep d : eff.deps) {
            String g = interpolate(d.group(), eff.props);
            String a = interpolate(d.artifact(), eff.props);
            if (g == null || a == null) continue;
            MavenPom.Dep m = eff.managed.get(g + ":" + a);
            String scope = interpolate(d.scope(), eff.props);
            if (scope == null && m != null) scope = interpolate(m.scope(), eff.props);
            if (scope != null && !scope.equals("compile") && !scope.equals("runtime")) continue;
            if (d.optional()) continue;
            String type = interpolate(d.type(), eff.props);
            if (type != null && !type.equals("jar") && !type.equals("bundle")) continue;
            String version = interpolate(d.version(), eff.props);
            if (version == null && m != null) version = interpolate(m.version(), eff.props);
            if (version == null || version.contains("${")) {
                log.warn("[{}] {} 의 의존 {}:{} 의 버전을 알 수 없어 건너뛴다", plugin, from.coordinate(), g, a);
                continue;
            }
            version = lowerBound(version);
            if (version == null) {
                log.warn("[{}] {} 의 의존 {}:{} 의 버전 범위를 해석할 수 없어 건너뛴다 — 설명 파일 libraries 에 직접 적어라", plugin, from.coordinate(), g, a);
                continue;
            }
            if (excluded(inherited, g, a)) continue;
            String classifier = interpolate(d.classifier(), eff.props);
            Library lib;
            try {
                lib = new Library(g + ":" + a + ":" + version + (classifier == null ? "" : ":" + classifier));
            } catch (IllegalArgumentException e) {
                log.warn("[{}] {} 의 의존 {}:{}:{} 의 좌표가 올바르지 않아 건너뛴다", plugin, from.coordinate(), g, a, version);
                continue;
            }
            List<String> ex = new ArrayList<>(inherited);
            ex.addAll(d.exclusions());
            out.add(new Next(lib, ex));
        }
        return out;
    }

    private static boolean excluded(List<String> patterns, String g, String a) {
        for (String p : patterns) {
            int i = p.indexOf(':');
            String pg = p.substring(0, i);
            String pa = p.substring(i + 1);
            if ((pg.equals("*") || pg.equals(g)) && (pa.equals("*") || pa.equals(a))) return true;
        }
        return false;
    }

    /** "1.0" → 1.0, "[1.0]" → 1.0, "[1.0,2.0)" → 1.0, "(,2.0]" 처럼 하한이 없으면 null. */
    static String lowerBound(String v) {
        v = v.strip();
        if (!v.startsWith("[") && !v.startsWith("(")) return v;
        String inner = v.substring(1, v.length() - 1);
        String first = inner.split(",", -1)[0].strip();
        return first.isEmpty() || v.startsWith("(") ? null : first;
    }

    private static final Pattern PROP = Pattern.compile("\\$\\{([^}]+)}");

    /** ${이름} 을 props 로 푼다(풀리지 않은 것은 그대로 남긴다). null 은 null. */
    static String interpolate(String s, Map<String, String> props) {
        if (s == null) return null;
        for (int pass = 0; pass < 10 && s.contains("${"); pass++) {
            Matcher m = PROP.matcher(s);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            while (m.find()) {
                String v = props.get(m.group(1));
                if (v == null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(v));
                    changed = true;
                }
            }
            m.appendTail(sb);
            s = sb.toString();
            if (!changed) break;
        }
        return s;
    }

    // ------------------------------------------------------------------ 캐시·잠금 파일

    private Path fileOf(Library lib, String ext) { return cache.resolve(relative(lib, ext)); }

    private static String relative(Library lib, String ext) {
        String base = lib.artifact() + "-" + lib.version() + (lib.classifier() == null ? "" : "-" + lib.classifier());
        return lib.group().replace('.', '/') + "/" + lib.artifact() + "/" + lib.version() + "/" + base + "." + ext;
    }

    private void loadLock() {
        if (!Files.isRegularFile(lockFile)) return;
        try {
            for (String line : Files.readAllLines(lockFile, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.lastIndexOf('=');
                if (i <= 0) continue;
                lock.put(line.substring(0, i).strip(), line.substring(i + 1).strip().toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            log.warn("{} 를 읽지 못했다 — 기록이 없는 것으로 본다: {}", lockFile, e.getMessage());
        }
    }

    private synchronized void record(String coord, String sha) throws LibraryException {
        if (sha.equals(lock.get(coord))) return;
        lock.put(coord, sha);
        StringBuilder sb = new StringBuilder("# 라이브러리 자동 로딩이 받은 jar 의 sha256 — 이 값과 다른 파일은 받지도 쓰지도 않는다. 의도한 업데이트라면 해당 줄을 지워라.\n");
        lock.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try {
            store(lockFile, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (LibraryException e) {
            lock.remove(coord);
            throw e;
        }
    }

    private static void store(Path target, byte[] body) throws LibraryException {
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), "dl-", ".tmp");
            Files.write(tmp, body, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (tmp != null) deleteQuietly(tmp);
            throw new LibraryException(target + " 를 쓰지 못했다: " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 다음에 덮어쓴다
        }
    }

    // ------------------------------------------------------------------ 해시

    private static MessageDigest digest(String alg) {
        try {
            return MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] data) { return hex(digest("SHA-256").digest(data)); }

    private String sha256(Path file) throws LibraryException {
        MessageDigest md = digest("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        } catch (IOException e) {
            throw new LibraryException(file + " 를 읽지 못했다: " + e.getMessage(), e);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    // ------------------------------------------------------------------ HTTP

    /** java.net.http 로 가져온다. https 만(리다이렉트도 https→http 는 따르지 않는다), 429/5xx 는 몇 번 다시 시도한다. */
    static final class HttpFetcher implements Fetcher {
        private final HttpClient client;
        private final int timeoutSeconds;
        private final long retryDelayMillis;

        HttpFetcher(int timeoutSeconds, long retryDelayMillis) {
            this.timeoutSeconds = timeoutSeconds;
            this.retryDelayMillis = retryDelayMillis;
            this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).followRedirects(HttpClient.Redirect.NORMAL).build();
        }

        @Override
        public byte[] get(URI uri, long maxBytes) throws IOException {
            IOException last = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                if (attempt > 0) {
                    try {
                        Thread.sleep(retryDelayMillis << (attempt - 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("중단됨");
                    }
                }
                try {
                    HttpResponse<InputStream> r = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeoutSeconds))
                            .header("User-Agent", "Furnace-Libraries").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream in = r.body()) {
                        int code = r.statusCode();
                        if (code == 404) return null;
                        if (code == 429 || code >= 500) {
                            last = new IOException("HTTP " + code + " (" + uri + ")");
                            continue;
                        }
                        if (code != 200) throw new IOException("HTTP " + code + " (" + uri + ")");
                        long len = r.headers().firstValueAsLong("Content-Length").orElse(-1);
                        if (len > maxBytes) throw new IOException("크기 상한(" + maxBytes + "바이트)을 넘는다: " + uri);
                        byte[] data = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
                        if (data.length > maxBytes) throw new IOException("크기 상한(" + maxBytes + "바이트)을 넘는다: " + uri);
                        return data;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("중단됨");
                }
            }
            throw last != null ? last : new IOException("받지 못했다: " + uri);
        }
    }
}

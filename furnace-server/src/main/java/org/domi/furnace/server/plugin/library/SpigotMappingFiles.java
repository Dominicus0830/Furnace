// Spigot 이름 → Mojang 이름 대응표의 재료 파일 세 개를 운영자 컴퓨터에서 받아 캐시에 둔다(H7d. 2026-11-12 결정: 기본으로 켜고, 필요할 때만 받는다).
//  - 설정: furnace.properties plugins.compat.nms.spigot-mappings=download (기본). 그래도 plugins/bukkit-plugins/(또는 plugins/) 에
//    Spigot 이름 NMS 플러그인(매니페스트에 paperweight-mappings-namespace: mojang 이 없고 net.minecraft 를 가리키는 jar)이 있을 때만 받는다.
//  - 저장소·서버 jar 에는 싣지 않는다(Mojang·Spigot 에서 뽑은 자료, docs/policy/licensing.md). 받은 파일은 운영자 캐시에만 남는다.
//  - Mojang server.txt: 주소·sha1·크기를 코드에 고정했다. 값은 Mojang 버전 목록 → 1.21.1 버전 파일(piston-meta, sha1 82ba6501…)의
//    downloads.server_mappings 에서 옮겼고, 같은 파일의 downloads.server.sha1(59353fb4…)이 저장소의 바닐라 1.21.1 서버 jar 해시와 같음을 확인했다(2026-11-08).
//  - Spigot BuildData(클래스·멤버 csrg): Spigot 은 체크섬을 내지 않는다. hub.spigotmc.org/versions/1.21.1.json 의 BuildData 커밋에 고정해 받고,
//    그 커밋의 info.json 이 가리키는 Mojang 매핑이 위 sha1 과 같은지 확인한 뒤, 처음 받은 커밋·sha256 을 잠금 파일(spigot-mappings.lock)에 적는다.
//    그 뒤로는 다른 커밋·다른 바이트를 받지도 쓰지도 않는다(의도한 갱신이면 잠금 파일을 지운다).
//  - plugins.libraries.offline=true 면 네트워크에 나가지 않는다 — 파일을 손으로 캐시에 두면(아래 경로) 그것을 쓴다(Mojang 파일은 sha1 이 맞아야 한다).
package org.domi.furnace.server.plugin.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public final class SpigotMappingFiles {

    public static final String MINECRAFT = "1.21.1";
    /** Mojang 공식 서버 매핑(ProGuard). 주소의 경로가 곧 sha1 이다. */
    public static final String MOJANG_SHA1 = "03f8985492bda0afc0898465341eb0acef35f570";
    public static final long MOJANG_SIZE = 7_456_063L;
    public static final URI MOJANG_MAPPINGS = URI.create("https://piston-data.mojang.com/v1/objects/" + MOJANG_SHA1 + "/server.txt");
    /** Spigot 버전 파일(refs.BuildData = BuildData 저장소 커밋). */
    public static final URI SPIGOT_VERSION = URI.create("https://hub.spigotmc.org/versions/" + MINECRAFT + ".json");
    /** BuildData 저장소 원문(파일?at=커밋). */
    public static final URI BUILDDATA_RAW = URI.create("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/");

    public static final String DIR = "spigot-mappings";
    public static final String LOCK = "spigot-mappings.lock";
    public static final String MOJANG_FILE = "server.txt";
    public static final String CLASSES_FILE = "bukkit-cl.csrg";
    public static final String MEMBERS_FILE = "bukkit-members.csrg";

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final long SMALL = 1024L * 1024L;   // 버전 파일·info.json 상한

    /** 받은 세 파일. */
    public record Files3(Path mojang, Path classes, Path members) {
        /** 호환 형식에 넘기는 값(경로 구분자로 잇는다). */
        public String joined() { return mojang + File.pathSeparator + classes + File.pathSeparator + members; }
    }

    private final LibrarySettings settings;
    private final Path dir;
    private final LibraryResolver.Fetcher fetcher;
    private final URI mojangUri;
    private final String mojangSha1;
    private final long mojangSize;
    private final URI spigotVersionUri;
    private final URI buildDataRaw;
    private final Logger log;

    public SpigotMappingFiles(LibrarySettings settings, Path cacheRoot, Logger log) {
        this(settings, cacheRoot, new LibraryResolver.HttpFetcher(settings.timeoutSeconds(), 1000), MOJANG_MAPPINGS, MOJANG_SHA1, MOJANG_SIZE,
                SPIGOT_VERSION, BUILDDATA_RAW, log);
    }

    /** 시험용: 다른 주소·고정값·가져오기. */
    SpigotMappingFiles(LibrarySettings settings, Path cacheRoot, LibraryResolver.Fetcher fetcher, URI mojang, String mojangSha1, long mojangSize,
                       URI spigotVersion, URI buildDataRaw, Logger log) {
        this.mojangSha1 = mojangSha1;
        this.mojangSize = mojangSize;
        this.settings = settings;
        this.dir = cacheRoot.resolve(DIR).resolve(MINECRAFT);
        this.fetcher = fetcher;
        this.mojangUri = mojang;
        this.spigotVersionUri = spigotVersion;
        this.buildDataRaw = buildDataRaw.toString().endsWith("/") ? buildDataRaw : URI.create(buildDataRaw + "/");
        this.log = log;
    }

    /** 파일이 놓이는 폴더(오프라인 안내용). */
    public Path directory() { return dir; }

    /** 세 파일을 준비한다(캐시에 맞는 것이 있으면 네트워크에 나가지 않는다). */
    public Files3 ensure() throws LibraryResolver.LibraryException {
        Path mojang = ensureMojang();
        Path[] spigot = ensureSpigot();
        return new Files3(mojang, spigot[0], spigot[1]);
    }

    // ------------------------------------------------------------------ Mojang

    private Path ensureMojang() throws LibraryResolver.LibraryException {
        Path file = dir.resolve(MOJANG_FILE);
        if (Files.isRegularFile(file)) {
            if (mojangSha1.equals(hash("SHA-1", read(file)))) return file;
            log.warn("캐시된 Mojang 매핑 {} 의 sha1 이 고정값과 다르다 — 지우고 다시 받는다", file);
            deleteQuietly(file);
        }
        offlineCheck("Mojang 1.21.1 서버 매핑(" + mojangUri + ", sha1 " + mojangSha1 + ")을 " + file + " 에 두라");
        log.info("Spigot 이름 플러그인을 위해 Mojang 1.21.1 서버 매핑을 받는다({}, {} 바이트)", mojangUri, mojangSize);
        byte[] body = get(mojangUri, Math.max(mojangSize, settings.maxSizeMb() * SMALL));
        if (body == null) throw new LibraryResolver.LibraryException("Mojang 매핑이 없다(404): " + mojangUri);
        String got = hash("SHA-1", body);
        if (!mojangSha1.equals(got) || body.length != mojangSize) {
            throw new LibraryResolver.LibraryException("받은 Mojang 매핑이 고정값과 다르다(기대 sha1 " + mojangSha1 + "·" + mojangSize + " 바이트, 실제 " + got + "·" + body.length + ")");
        }
        store(file, body);
        return file;
    }

    // ------------------------------------------------------------------ Spigot BuildData

    private Path[] ensureSpigot() throws LibraryResolver.LibraryException {
        Path classes = dir.resolve(CLASSES_FILE);
        Path members = dir.resolve(MEMBERS_FILE);
        Map<String, String> lock = readLock();
        String lockedClasses = lock.get("classes.sha256");
        String lockedMembers = lock.get("members.sha256");
        if (Files.isRegularFile(classes) && Files.isRegularFile(members)) {
            if (lockedClasses == null && lockedMembers == null) {
                // 손으로 둔 파일(오프라인) — 지금 해시를 기록하고 쓴다
                lock.put("classes.sha256", hash("SHA-256", read(classes)));
                lock.put("members.sha256", hash("SHA-256", read(members)));
                lock.putIfAbsent("buildData", "수동");
                writeLock(lock);
                log.info("캐시에 둔 Spigot BuildData 매핑 {}·{} 을 쓴다 — sha256 을 {} 에 기록했다", classes, members, dir.resolve(LOCK));
                return new Path[] {classes, members};
            }
            if (hash("SHA-256", read(classes)).equals(lockedClasses) && hash("SHA-256", read(members)).equals(lockedMembers)) {
                return new Path[] {classes, members};
            }
            log.warn("캐시된 Spigot BuildData 매핑의 sha256 이 잠금 파일 {} 의 기록과 다르다 — 지우고 다시 받는다", dir.resolve(LOCK));
            deleteQuietly(classes);
            deleteQuietly(members);
        }
        offlineCheck("Spigot BuildData 의 mappings/bukkit-" + MINECRAFT + "-cl.csrg·-members.csrg 를 " + classes + "·" + members + " 에 두라");
        String commit = buildDataCommit();
        String lockedCommit = lock.get("buildData");
        if (lockedCommit != null && !lockedCommit.equals("수동") && !lockedCommit.equals(commit)) {
            throw new LibraryResolver.LibraryException("Spigot BuildData 커밋이 잠금 파일의 기록(" + lockedCommit + ")과 다르다(지금 " + commit
                    + ") — Spigot 이 " + MINECRAFT + " 매핑을 바꿨다. 의도한 갱신이면 " + dir.resolve(LOCK) + " 를 지워라");
        }
        JsonObject info = json(raw("info.json", commit), "BuildData info.json");
        String mappingsUrl = string(info, "mappingsUrl");
        if (mappingsUrl == null || !mappingsUrl.contains(mojangSha1)) {
            throw new LibraryResolver.LibraryException("BuildData " + commit + " 가 가리키는 Mojang 매핑(" + (mappingsUrl == null ? "적혀 있지 않음" : mappingsUrl) + ")이 1.21.1 서버 매핑(sha1 " + mojangSha1
                    + ")이 아니다 — 쓰지 않는다");
        }
        String clName = fileName(info, "classMappings");
        String memName = fileName(info, "memberMappings");
        byte[] cl = raw("mappings/" + clName, commit);
        byte[] mem = raw("mappings/" + memName, commit);
        String clSha = hash("SHA-256", cl);
        String memSha = hash("SHA-256", mem);
        if (lockedClasses != null && !lockedClasses.equals(clSha) || lockedMembers != null && !lockedMembers.equals(memSha)) {
            throw new LibraryResolver.LibraryException("받은 Spigot BuildData 매핑의 sha256 이 잠금 파일 " + dir.resolve(LOCK) + " 의 기록과 다르다 — 쓰지 않는다");
        }
        store(classes, cl);
        store(members, mem);
        lock.put("buildData", commit);
        lock.put("classes.sha256", clSha);
        lock.put("members.sha256", memSha);
        writeLock(lock);
        log.info("Spigot BuildData {} 의 {}·{} 을 받았다(sha256 {}·{}) — 이 값을 {} 에 고정했다", commit, clName, memName, clSha, memSha, dir.resolve(LOCK));
        return new Path[] {classes, members};
    }

    private String buildDataCommit() throws LibraryResolver.LibraryException {
        byte[] body = get(spigotVersionUri, SMALL);
        if (body == null) throw new LibraryResolver.LibraryException("Spigot 버전 파일이 없다(404): " + spigotVersionUri);
        JsonObject v = json(body, "Spigot 버전 파일");
        JsonElement refs = v.get("refs");
        String commit = refs != null && refs.isJsonObject() ? string(refs.getAsJsonObject(), "BuildData") : null;
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new LibraryResolver.LibraryException("Spigot 버전 파일 " + spigotVersionUri + " 에 refs.BuildData 커밋이 없다");
        }
        return commit;
    }

    private byte[] raw(String path, String commit) throws LibraryResolver.LibraryException {
        URI uri = buildDataRaw.resolve(path + "?at=" + commit);
        byte[] body = get(uri, settings.maxSizeMb() * SMALL);
        if (body == null) throw new LibraryResolver.LibraryException("BuildData 파일이 없다(404): " + uri);
        return body;
    }

    private static String fileName(JsonObject info, String key) throws LibraryResolver.LibraryException {
        String n = string(info, key);
        if (n == null || !FILE_NAME.matcher(n).matches()) {
            throw new LibraryResolver.LibraryException("BuildData info.json 의 " + key + " 가 없거나 파일 이름이 아니다: " + n);
        }
        return n;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static JsonObject json(byte[] body, String what) throws LibraryResolver.LibraryException {
        try {
            JsonElement e = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (e.isJsonObject()) return e.getAsJsonObject();
        } catch (RuntimeException ignored) {
            // 아래
        }
        throw new LibraryResolver.LibraryException(what + " 를 JSON 으로 읽지 못했다");
    }

    // ------------------------------------------------------------------ 필요 판정

    // 서버 클래스 파일에 호환 이름공간 문자열을 상수로 남기지 않는다(CompatIsolationStaticTest) — 실행 때 잇는다
    private static final byte[][] NMS = {String.join("/", "net", "minecraft", "").getBytes(StandardCharsets.UTF_8),
            String.join(".", "net", "minecraft", "").getBytes(StandardCharsets.UTF_8)};

    /**
     * 이 호환 플러그인 jar 에 대응표가 필요한가: 매니페스트에 {@code paperweight-mappings-namespace: mojang} 이 없고(Paper 와 같은 판정 — 속성이 없으면
     * Spigot 이름으로 본다) 클래스 파일 하나라도 {@code net/minecraft/}·{@code net.minecraft.} 를 담고 있다.
     */
    public static boolean needs(Path jar) throws IOException {
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile(), false)) {
            var man = jf.getManifest();
            String ns = man == null ? null : man.getMainAttributes().getValue("paperweight-mappings-namespace");
            if (ns != null && !ns.equalsIgnoreCase("spigot")) return false;
            var en = jf.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                byte[] b;
                try (var in = jf.getInputStream(e)) {
                    b = in.readAllBytes();
                }
                for (byte[] n : NMS) if (indexOf(b, n) >= 0) return true;
            }
        }
        return false;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0, last = hay.length - needle.length; i <= last; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ 공통

    private void offlineCheck(String hint) throws LibraryResolver.LibraryException {
        if (settings.offline()) throw new LibraryResolver.LibraryException("plugins.libraries.offline=true 인데 캐시에 없다 — " + hint);
    }

    private byte[] get(URI uri, long max) throws LibraryResolver.LibraryException {
        try {
            return fetcher.get(uri, max);
        } catch (IOException e) {
            throw new LibraryResolver.LibraryException(uri.getHost() + " 에서 받지 못했다(" + e.getMessage() + ")", e);
        }
    }

    private Map<String, String> readLock() {
        Map<String, String> m = new TreeMap<>();
        Path f = dir.resolve(LOCK);
        if (!Files.isRegularFile(f)) return m;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.indexOf('=');
                if (i > 0) m.put(line.substring(0, i).strip(), line.substring(i + 1).strip());
            }
        } catch (IOException e) {
            log.warn("{} 를 읽지 못했다 — 기록이 없는 것으로 본다: {}", f, e.getMessage());
        }
        return m;
    }

    private void writeLock(Map<String, String> lock) throws LibraryResolver.LibraryException {
        StringBuilder sb = new StringBuilder("# Spigot 이름 대응표 재료(H7d): 처음 받은 BuildData 커밋과 sha256. 이 값과 다른 파일은 받지도 쓰지도 않는다. 의도한 갱신이면 이 파일을 지워라.\n");
        lock.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        store(dir.resolve(LOCK), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] read(Path p) throws LibraryResolver.LibraryException {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new LibraryResolver.LibraryException(p + " 를 읽지 못했다: " + e.getMessage(), e);
        }
    }

    private static void store(Path target, byte[] body) throws LibraryResolver.LibraryException {
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), "dl-", ".tmp");
            Files.write(tmp, body);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (tmp != null) deleteQuietly(tmp);
            throw new LibraryResolver.LibraryException(target + " 에 쓰지 못했다: " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 다음에 덮어쓴다
        }
    }

    static String hash(String alg, byte[] data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance(alg).digest(data)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

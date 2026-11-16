// Spigot 이름 → Mojang 이름 대응표의 재료 파일 세 개를 운영자 컴퓨터에서 받아 캐시에 둔다(H7d. 2026-11-12 결정: 기본으로 켜고, 필요할 때만 받는다).
//  - 설정: furnace.properties plugins.compat.nms.spigot-mappings=download (기본). 그래도 plugins/bukkit-plugins/(또는 plugins/) 에
//    Spigot 이름 NMS 플러그인(매니페스트에 paperweight-mappings-namespace: mojang 이 없고 net.minecraft 를 가리키는 jar)이 있을 때만 받는다 — 판정은 호환 어댑터가 jar 를 훑으며 한다.
//  - 저장소·서버 jar 에는 싣지 않는다(Mojang·Spigot 에서 뽑은 자료, docs/policy/licensing.md). 받은 파일은 운영자 캐시에만 남는다.
//  - Mojang server.txt: 주소·sha1·크기를 코드에 고정했다. 값은 Mojang 버전 목록 → 1.21.1 버전 파일(piston-meta, sha1 82ba6501…)의
//    downloads.server_mappings 에서 옮겼고, 같은 파일의 downloads.server.sha1(59353fb4…)이 저장소의 바닐라 1.21.1 서버 jar 해시와 같음을 확인했다(2026-11-08).
//  - Spigot BuildData(클래스·멤버 csrg): Spigot 은 체크섬을 내지 않는다. hub.spigotmc.org/versions/1.21.1.json 의 BuildData 커밋에 고정해 받고,
//    그 커밋의 info.json 이 가리키는 Mojang 매핑이 위 sha1 과 같은지 확인한 뒤, 처음 받은 커밋·sha256 을 잠금 파일(spigot-mappings.lock)에 적는다.
//    그 뒤로는 다른 커밋·다른 바이트를 받지도 쓰지도 않는다(의도한 갱신이면 잠금 파일을 지운다).
//  - 받기가 연결·제한 시간 문제로 실패하면 그 사실을 캐시(download-failed.properties)에 남기고, 다음 시작들은 기다림 없이 건너뛴다(10분 → 1시간 → 6시간 → 24시간 상한 백오프).
//    인터넷이 막힌 서버가 시작마다 제한 시간을 기다리지 않게 한다. 캐시에 파일이 있거나 받기에 성공하면 기록은 지워진다.
//  - plugins.libraries.offline=true 면 네트워크에 나가지 않는다 — 파일을 손으로 캐시에 두면(아래 경로) 그것을 쓴다(Mojang 파일은 sha1 이 맞아야 한다).
package org.domi.furnace.server.plugin.library;

import org.domi.furnace.api.GameVersion;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.domi.furnace.server.i18n.Lang;
import org.slf4j.Logger;

public final class SpigotMappingFiles {

    public static final String MINECRAFT = GameVersion.MINECRAFT;
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
    /** 옛 버전이 잠금 파일에 적던 한국어 값("수동") — 코드 점으로 적어 소스에 한글을 두지 않는다. */
    private static final String LEGACY_MANUAL = new String(new int[] {0xC218, 0xB3D9}, 0, 2);

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
    private Clock clock = Clock.systemUTC();

    /** 받기 실패 기억 파일(캐시 폴더 안). 지우면 바로 다시 받는다. */
    public static final String FAILED = "download-failed.properties";
    /** 연속 실패 횟수별 다음 시도까지 기다리는 시간(마지막 값이 상한). */
    static final Duration[] BACKOFF = {Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6), Duration.ofHours(24)};

    /** 이전 시작에서 받기에 실패해 아직 기다리는 중이라 이번에는 네트워크에 나가지 않았다 — 운영자에게 한 줄로 알릴 뿐 다시 시도하지 않는다. */
    public static final class Backoff extends LibraryResolver.LibraryException {
        Backoff(String message) { super(message); }
    }

    /** 시험용: 시계를 바꾼다. */
    SpigotMappingFiles withClock(Clock c) {
        this.clock = c;
        return this;
    }

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
        try {
            Path mojang = ensureMojang();
            Path[] spigot = ensureSpigot();
            Files3 done = new Files3(mojang, spigot[0], spigot[1]);
            forgetFailure();
            return done;
        } catch (NetworkFailure e) {
            rememberFailure(e);
            throw e;
        }
    }

    /** 연결·제한 시간·이름 풀이 실패(HTTP 응답이 온 것은 아님) — 다음 시작에서 같은 기다림을 되풀이하지 않도록 기억한다. */
    private static final class NetworkFailure extends LibraryResolver.LibraryException {
        NetworkFailure(String message, Throwable cause) { super(message, cause); }
    }

    private Path failedFile() { return dir.resolve(FAILED); }

    /** 네트워크가 필요한 지점(캐시에 맞는 파일이 없다) 직전에 부른다 — 이전 시작의 실패가 아직 기다림 안이면 Backoff. */
    private void backoffCheck() throws Backoff {
        Path f = failedFile();
        if (!Files.isRegularFile(f)) return;
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(f)) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            int n = Math.max(1, Integer.parseInt(p.getProperty("failures", "1").strip()));
            Instant last = Instant.ofEpochMilli(Long.parseLong(p.getProperty("last-failure-epoch-ms").strip()));
            Instant next = last.plus(BACKOFF[Math.min(n, BACKOFF.length) - 1]);
            Instant now = clock.instant();
            if (last.isAfter(now)) return;   // 시계가 되돌려졌거나 기록이 잘못 앞선 시각이면 믿지 않는다(몇 달씩 막지 않게)
            if (now.isBefore(next)) {
                long mins = Math.max(1, Duration.between(now, next).toMinutes());
                String wait = Lang.t(mins >= 120 ? "spigot.wait-hours" : "spigot.wait-minutes", mins >= 120 ? (mins + 30) / 60 : mins);
                throw new Backoff(Lang.t("spigot.backoff", n, last, p.getProperty("reason", "connection failed"), wait, f));
            }
        } catch (IOException | RuntimeException e) {
            // 읽을 수 없는 기록은 없는 것으로 본다(받기를 막지 않는다)
        }
    }

    private void rememberFailure(NetworkFailure e) {
        Path f = failedFile();
        int n = 0;
        try (var in = Files.newInputStream(f)) {
            java.util.Properties p = new java.util.Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            n = Integer.parseInt(p.getProperty("failures", "0").strip());
        } catch (IOException | RuntimeException ignored) {
            // 기록 없음
        }
        java.util.Properties out = new java.util.Properties();
        out.setProperty("failures", String.valueOf(Math.max(0, n) + 1));
        out.setProperty("last-failure-epoch-ms", String.valueOf(clock.millis()));
        String why = String.valueOf(e.getMessage()).replaceAll("[\\r\\n]+", " ");
        out.setProperty("reason", why.length() > 200 ? why.substring(0, 200) : why);
        try {
            var w = new java.io.StringWriter();
            out.store(w, "Record of failed Spigot name-mapping downloads (connection problems only). Delete it to download again on the next start.");
            store(f, w.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | LibraryResolver.LibraryException ex) {
            log.debug("Could not write the download-failure record (the cache folder is not writable) - the next start tries again: {}", ex.getMessage());
        }
    }

    private void forgetFailure() {
        deleteQuietly(failedFile());
    }

    // ------------------------------------------------------------------ Mojang

    private Path ensureMojang() throws LibraryResolver.LibraryException {
        Path file = dir.resolve(MOJANG_FILE);
        if (Files.isRegularFile(file)) {
            if (mojangSha1.equals(hash("SHA-1", read(file)))) return file;
            log.warn(Lang.t("spigot.cached-mojang-hash", file));
            deleteQuietly(file);
        }
        offlineCheck(Lang.t("spigot.put-mojang", mojangUri, mojangSha1, file));
        log.info(Lang.t("spigot.download-mojang", mojangUri, mojangSize));
        byte[] body = get(mojangUri, Math.max(mojangSize, settings.maxSizeMb() * SMALL));
        if (body == null) throw new LibraryResolver.LibraryException(Lang.t("spigot.mojang-404", mojangUri));
        String got = hash("SHA-1", body);
        if (!mojangSha1.equals(got) || body.length != mojangSize) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.mojang-mismatch", mojangSha1, mojangSize, got, body.length));
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
                lock.putIfAbsent("buildData", "manual");
                writeLock(lock);
                log.info(Lang.t("spigot.cached-builddata-used", classes, members, dir.resolve(LOCK)));
                return new Path[] {classes, members};
            }
            if (hash("SHA-256", read(classes)).equals(lockedClasses) && hash("SHA-256", read(members)).equals(lockedMembers)) {
                return new Path[] {classes, members};
            }
            log.warn(Lang.t("spigot.cached-builddata-hash", dir.resolve(LOCK)));
            deleteQuietly(classes);
            deleteQuietly(members);
        }
        offlineCheck(Lang.t("spigot.put-builddata", MINECRAFT, classes, members));
        String commit = buildDataCommit();
        String lockedCommit = lock.get("buildData");
        if (lockedCommit != null && !lockedCommit.equals("manual") && !lockedCommit.equals(LEGACY_MANUAL) && !lockedCommit.equals(commit)) {   // "수동": 예전 버전이 적던 값
            throw new LibraryResolver.LibraryException(Lang.t("spigot.commit-changed", lockedCommit, commit, MINECRAFT, dir.resolve(LOCK)));
        }
        JsonObject info = json(raw("info.json", commit), "BuildData info.json");
        String mappingsUrl = string(info, "mappingsUrl");
        if (mappingsUrl == null || !mappingsUrl.contains(mojangSha1)) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.builddata-mojang-mismatch", commit, mappingsUrl == null ? Lang.t("spigot.not-listed") : mappingsUrl, mojangSha1));
        }
        String clName = fileName(info, "classMappings");
        String memName = fileName(info, "memberMappings");
        byte[] cl = raw("mappings/" + clName, commit);
        byte[] mem = raw("mappings/" + memName, commit);
        String clSha = hash("SHA-256", cl);
        String memSha = hash("SHA-256", mem);
        if (lockedClasses != null && !lockedClasses.equals(clSha) || lockedMembers != null && !lockedMembers.equals(memSha)) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.builddata-sha-mismatch", dir.resolve(LOCK)));
        }
        store(classes, cl);
        store(members, mem);
        lock.put("buildData", commit);
        lock.put("classes.sha256", clSha);
        lock.put("members.sha256", memSha);
        writeLock(lock);
        log.info(Lang.t("spigot.builddata-downloaded", commit, clName, memName, clSha, memSha, dir.resolve(LOCK)));
        return new Path[] {classes, members};
    }

    private String buildDataCommit() throws LibraryResolver.LibraryException {
        byte[] body = get(spigotVersionUri, SMALL);
        if (body == null) throw new LibraryResolver.LibraryException(Lang.t("spigot.version-404", spigotVersionUri));
        JsonObject v = json(body, Lang.t("spigot.version-file"));
        JsonElement refs = v.get("refs");
        String commit = refs != null && refs.isJsonObject() ? string(refs.getAsJsonObject(), "BuildData") : null;
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.version-no-commit", spigotVersionUri));
        }
        return commit;
    }

    private byte[] raw(String path, String commit) throws LibraryResolver.LibraryException {
        URI uri = buildDataRaw.resolve(path + "?at=" + commit);
        byte[] body = get(uri, settings.maxSizeMb() * SMALL);
        if (body == null) throw new LibraryResolver.LibraryException(Lang.t("spigot.builddata-404", uri));
        return body;
    }

    private static String fileName(JsonObject info, String key) throws LibraryResolver.LibraryException {
        String n = string(info, key);
        if (n == null || !FILE_NAME.matcher(n).matches()) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.builddata-info-key", key, n));
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
        throw new LibraryResolver.LibraryException(Lang.t("spigot.json-unreadable", what));
    }

    // 이 대응표가 필요한 플러그인인지(Spigot 이름 jar + NMS 이름 참조) 판정은 호환 어댑터가 jar 를 한 번 훑으며 한다(NmsCompatReport.Read.needsSpigotMappings) —
    // 서버가 같은 jar 를 따로 한 번 더 읽지 않는다.

    // ------------------------------------------------------------------ 공통

    private void offlineCheck(String hint) throws LibraryResolver.LibraryException {
        if (settings.offline()) throw new LibraryResolver.LibraryException(Lang.t("spigot.offline-not-cached", hint));
        backoffCheck();
    }

    private byte[] get(URI uri, long max) throws LibraryResolver.LibraryException {
        try {
            return fetcher.get(uri, max);
        } catch (LibraryResolver.RetriesExhausted e) {   // 429·5xx 를 몇 초 쉬며 되풀이하다 포기 — 시작마다 같은 기다림을 하지 않게 기억한다
            throw new NetworkFailure(Lang.t("spigot.download-failed-retry", uri.getHost(), e.getMessage()), e);
        } catch (LibraryResolver.HttpStatusException e) {
            throw new LibraryResolver.LibraryException(Lang.t("spigot.download-failed", uri.getHost(), e.getMessage()), e);
        } catch (IOException e) {
            throw new NetworkFailure(Lang.t("spigot.download-failed", uri.getHost(), e.getMessage()), e);
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
            log.warn(Lang.t("lib.records-read-failed", f, e.getMessage()));
        }
        return m;
    }

    private void writeLock(Map<String, String> lock) throws LibraryResolver.LibraryException {
        StringBuilder sb = new StringBuilder("# Inputs of the Spigot name mappings (H7d): the first BuildData commit and the sha256 of the files. A different file is neither downloaded nor used. If the update is intended, delete this file.\n");
        lock.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        store(dir.resolve(LOCK), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] read(Path p) throws LibraryResolver.LibraryException {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new LibraryResolver.LibraryException(Lang.t("lib.read-failed", p, e.getMessage()), e);
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
            throw new LibraryResolver.LibraryException(Lang.t("lib.write-failed", target, e.getMessage()), e);
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

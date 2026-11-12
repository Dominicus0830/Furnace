package org.domi.furnace.server.plugin.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** H7d: Spigot 이름 대응표 재료 받기 — 고정 sha1(Mojang), BuildData 커밋·sha256 잠금(Spigot), 오프라인·손으로 둔 파일. 네트워크 대신 메모리 저장소. */
class SpigotMappingFilesTest {

    private static final Logger LOG = LoggerFactory.getLogger("SpigotMappingFilesTest");
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final byte[] MOJANG = "net.minecraft.core.BlockPos -> jh:\n".getBytes(StandardCharsets.UTF_8);
    private static final String MOJANG_SHA1 = SpigotMappingFiles.hash("SHA-1", MOJANG);
    private static final URI MOJANG_URI = URI.create("https://mojang.test/v1/objects/" + MOJANG_SHA1 + "/server.txt");
    private static final URI VERSION_URI = URI.create("https://spigot.test/versions/1.21.1.json");
    private static final URI RAW = URI.create("https://spigot.test/raw/");

    @TempDir
    Path tmp;

    private final Map<String, byte[]> repo = new HashMap<>();
    private final List<String> asked = new ArrayList<>();

    private void standardRepo(String classes, String members) {
        repo.put(MOJANG_URI.toString(), MOJANG);
        repo.put(VERSION_URI.toString(), ("{\"name\":\"4300\",\"refs\":{\"BuildData\":\"" + COMMIT + "\",\"Bukkit\":\"x\"}}").getBytes(StandardCharsets.UTF_8));
        repo.put(RAW + "info.json?at=" + COMMIT, ("{\"minecraftVersion\":\"1.21.1\",\"mappingsUrl\":\"https://piston-data.mojang.com/v1/objects/" + MOJANG_SHA1
                + "/server.txt\",\"classMappings\":\"bukkit-1.21.1-cl.csrg\",\"memberMappings\":\"bukkit-1.21.1-members.csrg\"}").getBytes(StandardCharsets.UTF_8));
        repo.put(RAW + "mappings/bukkit-1.21.1-cl.csrg?at=" + COMMIT, classes.getBytes(StandardCharsets.UTF_8));
        repo.put(RAW + "mappings/bukkit-1.21.1-members.csrg?at=" + COMMIT, members.getBytes(StandardCharsets.UTF_8));
    }

    private SpigotMappingFiles files(LibrarySettings s) {
        return new SpigotMappingFiles(s, tmp.resolve("cache"), (uri, max) -> {
            asked.add(uri.toString());
            byte[] b = repo.get(uri.toString());
            if (b != null && b.length > max) throw new IOException("too big");
            return b;
        }, MOJANG_URI, MOJANG_SHA1, MOJANG.length, VERSION_URI, RAW, LOG);
    }

    private static LibrarySettings online() { return LibrarySettings.DEFAULT; }

    @Test
    void downloadsVerifiesAndLocksThenReusesTheCache() throws Exception {
        standardRepo("jh net/minecraft/core/BlockPosition\n", "# 비어 있음\n");
        SpigotMappingFiles.Files3 f = files(online()).ensure();
        assertEquals(new String(MOJANG, StandardCharsets.UTF_8), Files.readString(f.mojang()));
        assertTrue(Files.readString(f.classes()).contains("BlockPosition"));
        String lock = Files.readString(f.mojang().resolveSibling(SpigotMappingFiles.LOCK));
        assertTrue(lock.contains("buildData=" + COMMIT), lock);
        assertTrue(lock.contains("classes.sha256=" + SpigotMappingFiles.hash("SHA-256", "jh net/minecraft/core/BlockPosition\n".getBytes(StandardCharsets.UTF_8))), lock);
        assertTrue(f.joined().contains(java.io.File.pathSeparator));

        asked.clear();
        files(online().withOffline(true)).ensure();
        assertTrue(asked.isEmpty(), "캐시가 맞으면 네트워크에 나가지 않는다(오프라인에서도 쓴다): " + asked);
    }

    @Test
    void mojangBytesMustMatchThePinnedSha1() {
        standardRepo("jh a/B\n", "");
        repo.put(MOJANG_URI.toString(), "tampered".getBytes(StandardCharsets.UTF_8));
        var e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online()).ensure());
        assertTrue(e.getMessage().contains("고정값과 다르다"), e.getMessage());
        assertFalse(Files.exists(tmp.resolve("cache/spigot-mappings/1.21.1/server.txt")), "틀린 바이트는 캐시에 남기지 않는다");
    }

    @Test
    void buildDataPointingAtAnotherMojangMappingIsRefused() {
        standardRepo("jh a/B\n", "");
        repo.put(RAW + "info.json?at=" + COMMIT, ("{\"mappingsUrl\":\"https://piston-data.mojang.com/v1/objects/ffff/server.txt\","
                + "\"classMappings\":\"c.csrg\",\"memberMappings\":\"m.csrg\"}").getBytes(StandardCharsets.UTF_8));
        var e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online()).ensure());
        assertTrue(e.getMessage().contains("1.21.1 서버 매핑"), e.getMessage());
        // mappingsUrl 이 빠져 있어도 어느 Mojang 매핑용인지 모르므로 거부(검토 3)
        repo.put(RAW + "info.json?at=" + COMMIT, "{\"classMappings\":\"c.csrg\",\"memberMappings\":\"m.csrg\"}".getBytes(StandardCharsets.UTF_8));
        e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online()).ensure());
        assertTrue(e.getMessage().contains("1.21.1 서버 매핑"), e.getMessage());
    }

    @Test
    void lockedSpigotFilesRefuseDifferentBytesOrCommit() throws Exception {
        standardRepo("jh net/minecraft/core/BlockPosition\n", "");
        SpigotMappingFiles.Files3 f = files(online()).ensure();
        // 캐시 파일이 바뀌고(지워지고) 저장소도 다른 바이트를 준다 → 잠금과 달라 거부
        Files.delete(f.classes());
        standardRepo("jh net/minecraft/core/Evil\n", "");
        var e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online()).ensure());
        assertTrue(e.getMessage().contains("잠금 파일"), e.getMessage());
        // 커밋이 바뀌어도 거부
        repo.put(VERSION_URI.toString(), "{\"refs\":{\"BuildData\":\"ffffffffffffffffffffffffffffffffffffffff\"}}".getBytes(StandardCharsets.UTF_8));
        e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online()).ensure());
        assertTrue(e.getMessage().contains("커밋이 잠금 파일"), e.getMessage());
    }

    @Test
    void offlineUsesHandPlacedFilesAndExplainsWhatIsMissing() throws Exception {
        var e = assertThrows(LibraryResolver.LibraryException.class, () -> files(online().withOffline(true)).ensure());
        assertTrue(e.getMessage().contains("offline") && e.getMessage().contains("server.txt"), e.getMessage());
        assertTrue(asked.isEmpty());

        Path dir = tmp.resolve("cache/spigot-mappings/1.21.1");
        Files.createDirectories(dir);
        Files.write(dir.resolve(SpigotMappingFiles.MOJANG_FILE), MOJANG);
        Files.writeString(dir.resolve(SpigotMappingFiles.CLASSES_FILE), "jh x/Y\n");
        Files.writeString(dir.resolve(SpigotMappingFiles.MEMBERS_FILE), "");
        SpigotMappingFiles.Files3 f = files(online().withOffline(true)).ensure();
        assertEquals(dir.resolve(SpigotMappingFiles.CLASSES_FILE), f.classes());
        assertTrue(Files.readString(dir.resolve(SpigotMappingFiles.LOCK)).contains("buildData=수동"));
        assertTrue(asked.isEmpty());
    }

    // ------------------------------------------------------------------ 받기 실패 기억(백오프)

    private final java.util.concurrent.atomic.AtomicInteger networkCalls = new java.util.concurrent.atomic.AtomicInteger();

    /** 연결이 안 되는 저장소(방화벽): 호출마다 IOException. */
    private SpigotMappingFiles blocked(java.time.Instant now) {
        return new SpigotMappingFiles(online(), tmp.resolve("cache"), (uri, max) -> {
            networkCalls.incrementAndGet();
            throw new IOException("Connect timed out");
        }, MOJANG_URI, MOJANG_SHA1, MOJANG.length, VERSION_URI, RAW, LOG).withClock(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    }

    private SpigotMappingFiles healthy(java.time.Instant now) {
        return files(online()).withClock(java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    }

    @Test
    void networkFailureIsRememberedAndNextStartsSkipTheWaitUntilBackoffEnds() throws Exception {
        java.time.Instant t0 = java.time.Instant.parse("2026-11-12T00:00:00Z");
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0).ensure());
        assertEquals(1, networkCalls.get(), "첫 시작은 시도한다");
        Path record = tmp.resolve("cache/spigot-mappings/1.21.1").resolve(SpigotMappingFiles.FAILED);
        assertTrue(Files.isRegularFile(record));

        // 30분 뒤 다음 시작 — 아직 1시간 안이라 네트워크에 나가지 않고 한 줄 안내(Backoff)만
        var e = assertThrows(SpigotMappingFiles.Backoff.class, () -> blocked(t0.plusSeconds(30 * 60)).ensure());
        assertEquals(1, networkCalls.get(), "기다림 안에는 나가지 않는다");
        assertTrue(e.getMessage().contains("1번 연속") && e.getMessage().contains("다음 시도는 약 30분 뒤") && e.getMessage().contains(record.toString()), e.getMessage());
        assertFalse(e.getMessage().contains("\n"), "한 줄");

        // 1시간이 지나면 다시 시도하고, 또 실패하면 6시간으로 늘어난다
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0.plusSeconds(61 * 60)).ensure());
        assertEquals(2, networkCalls.get());
        assertThrows(SpigotMappingFiles.Backoff.class, () -> blocked(t0.plusSeconds(61 * 60 + 5 * 3600)).ensure());
        assertEquals(2, networkCalls.get(), "2번째 실패 뒤는 6시간 기다린다");
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0.plusSeconds(61 * 60 + 6 * 3600 + 60)).ensure());
        assertEquals(3, networkCalls.get());
        // 3번째 이후는 24시간 상한
        assertThrows(SpigotMappingFiles.Backoff.class, () -> blocked(t0.plusSeconds(61 * 60 + 6 * 3600 + 60 + 23 * 3600)).ensure());
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0.plusSeconds(61 * 60 + 6 * 3600 + 60 + 24 * 3600 + 60)).ensure());
        assertEquals(4, networkCalls.get());
    }

    @Test
    void successOrHandPlacedFilesClearTheFailureRecord() throws Exception {
        java.time.Instant t0 = java.time.Instant.parse("2026-11-12T00:00:00Z");
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0).ensure());
        Path dir = tmp.resolve("cache/spigot-mappings/1.21.1");
        Path record = dir.resolve(SpigotMappingFiles.FAILED);
        assertTrue(Files.isRegularFile(record));

        // 기다림 안이라도 캐시에 맞는 파일을 손으로 두면 받기를 건너뛰고 그 파일을 쓴다(기록도 지워진다)
        Files.write(dir.resolve(SpigotMappingFiles.MOJANG_FILE), MOJANG);
        Files.writeString(dir.resolve(SpigotMappingFiles.CLASSES_FILE), "jh x/Y\n");
        Files.writeString(dir.resolve(SpigotMappingFiles.MEMBERS_FILE), "");
        assertEquals(dir.resolve(SpigotMappingFiles.CLASSES_FILE), blocked(t0.plusSeconds(60)).ensure().classes());
        assertFalse(Files.exists(record));

        // 기록을 지우면(운영자가 직접) 바로 다시 시도한다
        Files.delete(dir.resolve(SpigotMappingFiles.CLASSES_FILE));
        Files.delete(dir.resolve(SpigotMappingFiles.MEMBERS_FILE));
        Files.delete(dir.resolve(SpigotMappingFiles.LOCK));
        Files.delete(dir.resolve(SpigotMappingFiles.MOJANG_FILE));
        networkCalls.set(0);
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0.plusSeconds(120)).ensure());
        assertEquals(1, networkCalls.get());
        Files.delete(record);
        assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0.plusSeconds(180)).ensure());
        assertEquals(2, networkCalls.get(), "지운 뒤에는 기다림 없이 시도한다");

        // 받기에 성공하면 기록이 사라진다
        standardRepo("jh net/minecraft/core/BlockPosition\n", "");
        healthy(t0.plusSeconds(86400L * 3)).ensure();
        assertFalse(Files.exists(record));
    }

    @Test
    void onlyConnectionFailuresAreRemembered_httpAndIntegrityFailuresAreNot() throws Exception {
        java.time.Instant t0 = java.time.Instant.parse("2026-11-12T00:00:00Z");
        Path record = tmp.resolve("cache/spigot-mappings/1.21.1").resolve(SpigotMappingFiles.FAILED);
        // 404(없음) — 바로 알 수 있는 실패라 기록하지 않는다
        assertThrows(LibraryResolver.LibraryException.class, () -> healthy(t0).ensure());
        assertFalse(Files.exists(record));
        // 변조된 Mojang 바이트
        standardRepo("jh a/B\n", "");
        repo.put(MOJANG_URI.toString(), "tampered".getBytes(StandardCharsets.UTF_8));
        assertThrows(LibraryResolver.LibraryException.class, () -> healthy(t0).ensure());
        assertFalse(Files.exists(record));
        // HTTP 응답은 왔는데 쓸 수 없음(403 등)
        var http = new SpigotMappingFiles(online(), tmp.resolve("cache"), (uri, max) -> {
            throw new LibraryResolver.HttpStatusException("HTTP 403 (" + uri + ")");
        }, MOJANG_URI, MOJANG_SHA1, MOJANG.length, VERSION_URI, RAW, LOG).withClock(java.time.Clock.fixed(t0, java.time.ZoneOffset.UTC));
        assertThrows(LibraryResolver.LibraryException.class, http::ensure);
        assertFalse(Files.exists(record));
        // offline 은 네트워크를 안 쓰므로 기록하지 않는다
        assertThrows(LibraryResolver.LibraryException.class, () -> files(online().withOffline(true)).ensure());
        assertFalse(Files.exists(record));
    }

    @Test
    void unreadableOrUnwritableRecordNeverBlocksStartup() throws Exception {
        java.time.Instant t0 = java.time.Instant.parse("2026-11-12T00:00:00Z");
        Path dir = tmp.resolve("cache/spigot-mappings/1.21.1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(SpigotMappingFiles.FAILED), "failures=abc\nlast-failure-epoch-ms=zzz\n");   // 깨진 기록은 없는 것으로 본다
        standardRepo("jh net/minecraft/core/BlockPosition\n", "");
        assertEquals(dir.resolve(SpigotMappingFiles.CLASSES_FILE), healthy(t0).ensure().classes());
        // 기록 파일 자리에 폴더가 있어 쓸 수 없어도 원래 실패를 그대로 던진다(기록 실패가 시작을 막지 않는다)
        Files.delete(dir.resolve(SpigotMappingFiles.CLASSES_FILE));
        Files.delete(dir.resolve(SpigotMappingFiles.MEMBERS_FILE));
        Files.delete(dir.resolve(SpigotMappingFiles.LOCK));
        Files.createDirectory(dir.resolve(SpigotMappingFiles.FAILED));
        Files.writeString(dir.resolve(SpigotMappingFiles.FAILED).resolve("x"), "비어 있지 않은 폴더라 덮어쓸 수 없다");
        var e = assertThrows(LibraryResolver.LibraryException.class, () -> blocked(t0).ensure());
        assertTrue(e.getMessage().contains("Connect timed out"), e.getMessage());
    }
}

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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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

    @Test
    void needsLooksAtTheManifestAndNmsReferences() throws Exception {
        assertTrue(SpigotMappingFiles.needs(jar("a.jar", null, "net/minecraft/server/level/EntityPlayer")));
        assertTrue(SpigotMappingFiles.needs(jar("b.jar", "spigot", "net.minecraft.server.level.EntityPlayer")));
        assertFalse(SpigotMappingFiles.needs(jar("c.jar", "mojang", "net/minecraft/server/level/ServerPlayer")), "Mojang 이름이라고 밝힌 jar");
        assertFalse(SpigotMappingFiles.needs(jar("d.jar", null, "org/bukkit/entity/Player")), "NMS 를 가리키지 않는 jar");
    }

    private Path jar(String name, String ns, String content) throws IOException {
        Path p = tmp.resolve(name);
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (ns != null) man.getMainAttributes().putValue("paperweight-mappings-namespace", ns);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p), man)) {
            out.putNextEntry(new JarEntry("x/A.class"));
            out.write(("Êþ" + content).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return p;
    }
}

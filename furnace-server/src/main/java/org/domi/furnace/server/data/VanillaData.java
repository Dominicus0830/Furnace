// 바닐라 내장 데이터(data/minecraft — 레시피·전리품·발전 과제·태그·동적 레지스트리…)를 서버 첫 실행 때 준비한다.
// Mojang 데이터를 저장소에 넣어 재배포하지 않고, Paperclip 처럼 공식 서버 jar 에서 꺼낸다:
//  1) cache/mojang_1.21.1.jar 가 있으면 그것, 없으면 -Dfurnace.vanillaJar=<경로> 의 jar, 그것도 없으면 Mojang 에서 내려받는다.
//  2) jar 는 sha1 로, 그 안의 실제 서버 jar(번들러 META-INF/versions/…)는 versions.list 의 sha256 으로 검증한다.
//  3) data/ 를 cache/vanilla-data-1.21.1/ 로 풀고 완료 표시를 남긴다(다음 실행부터는 바로 쓴다).
package org.domi.furnace.server.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VanillaData {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    public static final String VERSION = "1.21.1";
    /** 공식 1.21.1 서버 jar(번들러) sha1 — 버전 메타데이터의 downloads.server.sha1 과 같다. */
    static final String SERVER_JAR_SHA1 = "59353fb40c36d304f2035d51e7d6e6baa98dc05c";
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private VanillaData() {}

    /** data/ 를 담은 폴더를 돌려준다(필요하면 jar 를 구해 풀어 둔다). */
    public static Path ensure(Path serverDir) throws IOException {
        // 절대 경로로 정규화 — 추출 경로 검사(대상이 작업 폴더 안인가)가 "./" 같은 상대 경로에서 어긋나지 않게.
        Path cache = serverDir.toAbsolutePath().normalize().resolve("cache");
        Path out = cache.resolve("vanilla-data-" + VERSION);
        Path marker = out.resolve(".complete");
        if (Files.isRegularFile(marker) && SERVER_JAR_SHA1.equals(Files.readString(marker).trim())) return out;

        Files.createDirectories(cache);
        Path jar = cache.resolve("mojang_" + VERSION + ".jar");
        if (!Files.isRegularFile(jar) || !SERVER_JAR_SHA1.equals(sha1(jar))) {
            String given = System.getProperty("furnace.vanillaJar");
            if (given != null) {
                Path src = Path.of(given);
                String h = sha1(src);
                if (!SERVER_JAR_SHA1.equals(h)) throw new IOException("furnace.vanillaJar 가 바닐라 " + VERSION + " 서버 jar 가 아니다(sha1 " + h + ")");
                Files.copy(src, jar, StandardCopyOption.REPLACE_EXISTING);
            } else {
                download(jar);
            }
        }
        extract(jar, out);
        Files.writeString(marker, SERVER_JAR_SHA1);
        return out;
    }

    private static void download(Path jar) throws IOException {
        log.info("Downloading vanilla {} server jar from Mojang (for built-in data)", VERSION);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        try {
            JsonObject manifest = JsonParser.parseString(get(http, MANIFEST)).getAsJsonObject();
            String versionUrl = null;
            for (var v : manifest.getAsJsonArray("versions")) {
                JsonObject o = v.getAsJsonObject();
                if (VERSION.equals(o.get("id").getAsString())) { versionUrl = o.get("url").getAsString(); break; }
            }
            if (versionUrl == null) throw new IOException("Mojang 버전 목록에 " + VERSION + " 이 없다");
            JsonObject server = JsonParser.parseString(get(http, versionUrl)).getAsJsonObject()
                .getAsJsonObject("downloads").getAsJsonObject("server");
            if (!SERVER_JAR_SHA1.equals(server.get("sha1").getAsString()))
                throw new IOException("Mojang 이 알려 준 서버 jar sha1 이 예상과 다르다: " + server.get("sha1").getAsString());
            Path tmp = jar.resolveSibling(jar.getFileName() + ".part");
            HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(server.get("url").getAsString()))
                .timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofFile(tmp));
            if (r.statusCode() != 200) throw new IOException("서버 jar 다운로드 실패: HTTP " + r.statusCode());
            String h = sha1(tmp);
            if (!SERVER_JAR_SHA1.equals(h)) { Files.deleteIfExists(tmp); throw new IOException("받은 서버 jar 의 sha1 이 다르다: " + h); }
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("다운로드 중단", e);
        } catch (IOException e) {
            throw new IOException("바닐라 " + VERSION + " 서버 jar 를 구하지 못했다 — 직접 받아 " + jar
                + " 에 두거나 -Dfurnace.vanillaJar=<경로> 로 지정하라 (" + e.getMessage() + ")", e);
        }
    }

    private static String get(HttpClient http, String url) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException(url + " → HTTP " + r.statusCode());
        return r.body();
    }

    /** 번들러 jar → (sha256 검증) 실제 서버 jar → data/ 추출. 임시 폴더에 푼 뒤 통째로 옮긴다. */
    static void extract(Path bundler, Path out) throws IOException {
        log.info("Extracting vanilla {} data", VERSION);
        Path work = out.resolveSibling(out.getFileName() + ".tmp");
        deleteTree(work);
        Files.createDirectories(work);
        Path inner = work.resolve("server.jar");
        try (ZipFile zip = new ZipFile(bundler.toFile())) {
            ZipEntry list = zip.getEntry("META-INF/versions.list");
            if (list == null) throw new IOException("번들러 jar 가 아니다(META-INF/versions.list 없음)");
            String line = new String(zip.getInputStream(list).readAllBytes(), StandardCharsets.UTF_8).trim().split("\n")[0];
            String[] parts = line.split("\t");   // sha256 \t id \t path
            ZipEntry e = zip.getEntry("META-INF/versions/" + parts[2]);
            if (e == null) throw new IOException("번들러 안에 " + parts[2] + " 가 없다");
            MessageDigest sha256 = digest("SHA-256");
            try (InputStream in = new DigestInputStream(zip.getInputStream(e), sha256)) {
                Files.copy(in, inner);
            }
            String h = HexFormat.of().formatHex(sha256.digest());
            if (!h.equalsIgnoreCase(parts[0])) throw new IOException("내부 서버 jar sha256 이 다르다: " + h);
        }
        int files = 0;
        try (ZipFile zip = new ZipFile(inner.toFile())) {
            var it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                String name = e.getName();
                if (e.isDirectory() || !(name.startsWith("data/") || name.equals("version.json"))) continue;
                Path target = work.resolve(name).normalize();
                if (!target.startsWith(work)) throw new IOException("잘못된 경로: " + name);
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(e); OutputStream o = Files.newOutputStream(target)) {
                    in.transferTo(o);
                }
                files++;
            }
        }
        Files.delete(inner);
        deleteTree(out);
        Files.move(work, out, StandardCopyOption.ATOMIC_MOVE);
        log.info("Extracted {} vanilla data files", files);
    }

    static String sha1(Path p) throws IOException {
        MessageDigest md = digest("SHA-1");
        try (InputStream in = new DigestInputStream(Files.newInputStream(p), md)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static MessageDigest digest(String alg) {
        try {
            return MessageDigest.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(q);
        }
    }
}

package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 내려받기 + sha1 확인 + 캐시. 이미 받은 파일의 sha1 이 맞으면 다시 받지 않는다. 결과는 캐시 폴더에만 둔다(커밋 금지). */
public final class Downloader {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public String text(String url) throws IOException {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IOException("내려받기 실패 " + r.statusCode() + ": " + url);
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("내려받기 중단: " + url, e);
        }
    }

    /** url 을 target 으로. sha1 이 주어지면 일치할 때만 받아들인다. */
    public Path file(String url, String sha1, Path target) throws IOException {
        if (Files.isRegularFile(target) && (sha1 == null || sha1(target).equalsIgnoreCase(sha1))) return target;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("내려받기 실패 " + r.statusCode() + ": " + url);
            try (InputStream in = r.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("내려받기 중단: " + url, e);
        }
        if (sha1 != null && !sha1(tmp).equalsIgnoreCase(sha1)) {
            Files.deleteIfExists(tmp);
            throw new IOException("sha1 이 맞지 않는다: " + url);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

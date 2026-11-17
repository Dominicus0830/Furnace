package org.domi.furnace.tools.extract;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;

/**
 * 한 판의 version.json 에서 도구가 쓰는 값만 꺼낸 것(piston-meta 가 판마다 공개하는 파일).
 *
 * @param id          판 이름(예 1.21.1)
 * @param serverUrl   서버 번들러 jar
 * @param serverSha1  그 sha1
 * @param mappings    공식 대응표(난독화된 판만 — 26.x 처럼 난독화가 없는 판은 비어 있다)
 * @param protocol    네트워크 프로토콜 번호(없으면 -1)
 * @param javaMajor   실행에 필요한 Java 주 버전(없으면 -1)
 */
public record VersionInfo(String id, String serverUrl, String serverSha1, Optional<Download> mappings, int protocol, int javaMajor) {
    public record Download(String url, String sha1) {}

    public static VersionInfo parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonObject downloads = o.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("server")) throw new IllegalArgumentException("이 판에는 서버 jar 가 없다: " + str(o, "id"));
        JsonObject server = downloads.getAsJsonObject("server");
        Optional<Download> map = Optional.empty();
        if (downloads.has("server_mappings")) {
            JsonObject m = downloads.getAsJsonObject("server_mappings");
            map = Optional.of(new Download(m.get("url").getAsString(), m.get("sha1").getAsString()));
        }
        int protocol = o.has("protocol_version") ? o.get("protocol_version").getAsInt() : -1;
        int java = -1;
        JsonElement jv = o.get("javaVersion");
        if (jv != null && jv.isJsonObject() && jv.getAsJsonObject().has("majorVersion")) java = jv.getAsJsonObject().get("majorVersion").getAsInt();
        return new VersionInfo(str(o, "id"), server.get("url").getAsString(), server.get("sha1").getAsString(), map, protocol, java);
    }

    /** version_manifest_v2.json 에서 판 id 의 version.json 주소. */
    public static Optional<String> manifestUrl(String manifestJson, String id) {
        for (JsonElement e : JsonParser.parseString(manifestJson).getAsJsonObject().getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (id.equals(v.get("id").getAsString())) return Optional.of(v.get("url").getAsString());
        }
        return Optional.empty();
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsString() : "?";
    }
}

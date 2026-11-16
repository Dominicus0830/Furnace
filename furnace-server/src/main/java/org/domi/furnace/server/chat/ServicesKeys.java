// Mojang 서비스 공개키(authlib ServicesKeySet) — 플레이어 채팅 키 서명 검증용 playerCertificateKeys.
// 기동 시 비동기로 받아 오며, 실패하면 "Failed to request yggdrasil public key: 원인" 한 줄 경고(스택은 DEBUG)를 남기고 서명 채팅 검증을 못 한다.
package org.domi.furnace.server.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.domi.furnace.server.i18n.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServicesKeys {

    private static final Logger log = LoggerFactory.getLogger("Yggdrasil Key Fetcher");

    private volatile List<PublicKey> profileKeys = List.of();
    /** 프로필 속성(스킨 textures 등) 서명 공개키 — authlib ServicesKeyType.PROFILE_PROPERTY(profilePropertyKeys). */
    private volatile List<PublicKey> propertyKeys = List.of();

    /** 테스트·사설 인증용: 키를 직접 넣는다. */
    public void setProfileKeys(List<PublicKey> keys) { this.profileKeys = List.copyOf(keys); }

    /** 테스트·사설 인증용: 프로필 속성 서명 공개키를 직접 넣는다. */
    public void setPropertyKeys(List<PublicKey> keys) { this.propertyKeys = List.copyOf(keys); }

    /** 프로필 속성의 서명(Base64, SHA1withRSA 로 값 글자에 대한 서명)이 알려진 공개키 중 하나로 맞는가. 키를 모르거나 서명이 없으면 거짓. */
    public boolean validProfileProperty(String value, String signature) {
        if (signature == null || value == null) return false;
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (PublicKey key : propertyKeys) {
            try {
                java.security.Signature s = java.security.Signature.getInstance("SHA1withRSA");
                s.initVerify(key);
                s.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (s.verify(sig)) return true;
            } catch (java.security.GeneralSecurityException ignored) {
                // 이 키로는 확인하지 못함 — 다음 키
            }
        }
        return false;
    }

    public boolean canValidateProfileKeys() { return !profileKeys.isEmpty(); }

    /** 키가 없으면 null(바닐라 getProfileKeySignatureValidator). */
    public SignatureValidator profileKeySignatureValidator() {
        List<PublicKey> keys = profileKeys;
        return keys.isEmpty() ? null : SignatureValidator.anyOf(keys, "SHA1withRSA");
    }

    /** minecraft.api.services.host(기본 https://api.minecraftservices.com)/publickeys 에서 비동기로 받는다. */
    public void fetchAsync() {
        String host = System.getProperty("minecraft.api.services.host", "https://api.minecraftservices.com");
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .proxy(java.net.ProxySelector.getDefault()).build();
                HttpResponse<String> resp = client.send(HttpRequest.newBuilder(URI.create(host + "/publickeys"))
                        .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                profileKeys = parse(resp.body());
                propertyKeys = parseKeys(resp.body(), "profilePropertyKeys");
            } catch (Exception e) {
                log.warn(Lang.t("services.keys-failed", e.toString(), host));
                log.debug("Failed to request yggdrasil public key", e);
            }
        }, "Yggdrasil Key Fetcher");
        t.setDaemon(true);
        t.start();
    }

    static List<PublicKey> parse(String body) throws Exception { return parseKeys(body, "playerCertificateKeys"); }

    static List<PublicKey> parseKeys(String body, String field) throws Exception {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        List<PublicKey> out = new ArrayList<>();
        JsonArray arr = o.has(field) ? o.getAsJsonArray(field) : new JsonArray();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        for (JsonElement e : arr) {
            byte[] der = Base64.getDecoder().decode(e.getAsJsonObject().get("publicKey").getAsString());
            out.add(kf.generatePublic(new X509EncodedKeySpec(der)));
        }
        return out;
    }
}

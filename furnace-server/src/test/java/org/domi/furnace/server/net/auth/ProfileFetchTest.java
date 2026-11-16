// 프로필 조회(세션 서버 profile/<uuid>·서비스 서버 이름 조회)와 프로필 속성 서명 확인. 서버는 로컬 HTTP 가짜.
package org.domi.furnace.server.net.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.server.chat.ServicesKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProfileFetchTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private HttpServer http;

    @AfterEach
    void stop() {
        System.clearProperty("minecraft.api.services.host");
        if (http != null) http.stop(0);
    }

    private String serve(String path, int status, String body, AtomicReference<String> seen) throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext(path, ex -> {
            seen.set(ex.getRequestURI().getRawPath() + "?" + ex.getRequestURI().getRawQuery());
            if (body == null) {
                ex.sendResponseHeaders(status, -1);
            } else {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, b.length);
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    @Test
    void fetchProfileAsksForSignedProperties() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        String host = serve("/session/minecraft/profile", 200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":"
                + "[{\"name\":\"textures\",\"value\":\"dGV4\",\"signature\":\"c2ln\"}]}", seen);
        var found = new SessionService(host).fetchProfile(NOTCH).get(10, TimeUnit.SECONDS).orElseThrow();
        assertEquals("/session/minecraft/profile/069a79f444e94726a5befca90e38aaf5?unsigned=false", seen.get());
        assertEquals(NOTCH, found.id());
        assertEquals("Notch", found.name());
        assertEquals(List.of(new org.domi.furnace.server.players.GameProfile.Property("textures", "dGV4", "c2ln")), found.properties());
    }

    @Test
    void fetchProfileIsEmptyWhenThereIsNoProfileOrTheServerFails() throws Exception {
        for (int status : new int[] {204, 404, 429, 500}) {
            AtomicReference<String> seen = new AtomicReference<>();
            String host = serve("/session/minecraft/profile", status, null, seen);
            assertTrue(new SessionService(host).fetchProfile(NOTCH).get(10, TimeUnit.SECONDS).isEmpty(), "상태 " + status);
            http.stop(0);
        }
        // 닿지 않는 서버
        assertTrue(new SessionService("http://127.0.0.1:1").fetchProfile(NOTCH).get(10, TimeUnit.SECONDS).isEmpty());
        // 깨진 본문
        AtomicReference<String> seen = new AtomicReference<>();
        String host = serve("/session/minecraft/profile", 200, "not json", seen);
        assertTrue(new SessionService(host).fetchProfile(NOTCH).get(10, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void lookupByNameUsesTheServicesHost() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        String host = serve("/minecraft/profile/lookup/name", 200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}", seen);
        System.setProperty("minecraft.api.services.host", host);
        var found = new SessionService("http://127.0.0.1:1").lookupByName("Notch").get(10, TimeUnit.SECONDS).orElseThrow();
        assertEquals("/minecraft/profile/lookup/name/Notch?null", seen.get());
        assertEquals(NOTCH, found.id());
        assertEquals("Notch", found.name());
        assertTrue(found.properties().isEmpty());
        http.stop(0);
        String missing = serve("/minecraft/profile/lookup/name", 404, null, seen);
        System.setProperty("minecraft.api.services.host", missing);
        assertTrue(new SessionService("http://127.0.0.1:1").lookupByName("Nobody").get(10, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void propertySignaturesAreCheckedWithTheServicesKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair mojang = gen.generateKeyPair();
        KeyPair other = gen.generateKeyPair();
        String value = "dGV4dHVyZXM=";
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(mojang.getPrivate());
        signer.update(value.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        ServicesKeys keys = new ServicesKeys();
        assertFalse(keys.validProfileProperty(value, signature), "키를 모르면 맞다고 하지 않는다");
        keys.setPropertyKeys(List.of(other.getPublic(), mojang.getPublic()));
        assertTrue(keys.validProfileProperty(value, signature), "여러 키 중 하나로 맞으면 된다");
        assertFalse(keys.validProfileProperty(value + "x", signature), "값이 바뀌면 거짓");
        assertFalse(keys.validProfileProperty(value, null));
        assertFalse(keys.validProfileProperty(value, "not base64 !"));
        assertFalse(keys.validProfileProperty(null, signature));
        keys.setPropertyKeys(List.of(other.getPublic()));
        assertFalse(keys.validProfileProperty(value, signature), "다른 키로는 거짓");
    }
}

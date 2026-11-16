package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E10: 온라인(인증) 모드에서 프로필 채우기 — 사용자 캐시 → 이름 조회 → 세션 서버 순서와 결과. Mojang 은 로컬 HTTP 가짜. */
@SuppressWarnings({"removal", "deprecation"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitProfileOnlineTest {

    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID JEB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    FurnaceTestServer server;
    HttpServer http;
    final AtomicInteger nameCalls = new AtomicInteger();
    final AtomicInteger sessionCalls = new AtomicInteger();
    volatile int sessionStatus = 200;

    @BeforeAll
    void start() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/minecraft/profile/lookup/name/", ex -> {
            nameCalls.incrementAndGet();
            String name = ex.getRequestURI().getPath().substring("/minecraft/profile/lookup/name/".length());
            if (name.equalsIgnoreCase("Notch") || name.equalsIgnoreCase("jeb_")) {
                boolean jeb = name.equalsIgnoreCase("jeb_");
                byte[] b = ("{\"id\":\"" + (jeb ? "853c80ef3c3749fdaa49938b674adae6" : "069a79f444e94726a5befca90e38aaf5") + "\",\"name\":\"" + (jeb ? "jeb_" : "Notch") + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
            ex.close();
        });
        http.createContext("/session/minecraft/profile/", ex -> {
            sessionCalls.incrementAndGet();
            if (sessionStatus != 200) {
                ex.sendResponseHeaders(sessionStatus, -1);
            } else {
                byte[] b = ("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\",\"properties\":[{\"name\":\"textures\",\"value\":\"dGV4\",\"signature\":\"c2ln\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        http.start();
        String host = "http://127.0.0.1:" + http.getAddress().getPort();
        System.setProperty("minecraft.api.session.host", host);
        System.setProperty("minecraft.api.services.host", host);
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        // 프록시 뒤 online-mode=false 서버: 오프라인 프로필 규칙(usesAuthentication)은 그대로 두고 조회만 켠다(Paper usesAuthentication)
        server.internals().profileCache().setProfileLookups(true);
        server.tick(3);
    }

    @AfterAll
    void stop() {
        System.clearProperty("minecraft.api.session.host");
        System.clearProperty("minecraft.api.services.host");
        server.close();
        http.stop(0);
    }

    @Test
    void completeFindsTheUuidByNameThenFetchesTextures() {
        int names = nameCalls.get();
        int sessions = sessionCalls.get();
        PlayerProfile p = Bukkit.createProfile("jeb_");
        assertFalse(p.completeFromCache(false, true), "이름만 있고 캐시에 없다 — 묻지 않고는 못 채운다");
        assertNull(p.getId());
        assertEquals(names, nameCalls.get());

        assertTrue(p.complete(true, true));
        assertEquals(JEB, p.getId());
        assertEquals("jeb_", p.getName());
        assertTrue(p.hasTextures());
        assertEquals("dGV4", p.getProperties().iterator().next().getValue());
        assertEquals("c2ln", p.getProperties().iterator().next().getSignature());
        assertEquals(names + 1, nameCalls.get());
        assertEquals(sessions + 1, sessionCalls.get());

        // 이제 캐시에 있다 — 이름은 다시 묻지 않는다
        PlayerProfile again = Bukkit.createProfile("JEB_");
        assertTrue(again.completeFromCache(true, true));
        assertEquals(JEB, again.getId());
        assertEquals(names + 1, nameCalls.get(), "찾은 이름은 사용자 캐시에 들어간다");
        // 이미 다 가졌으면 묻지 않는다
        assertTrue(p.complete(true, true));
        assertEquals(sessions + 1, sessionCalls.get());
    }

    @Test
    void updateReturnsTexturesAndLeavesTheOriginal() throws Exception {
        PlayerProfile p = Bukkit.createProfile(UUID.randomUUID(), "Notch");
        PlayerProfile updated = p.update().get(10, TimeUnit.SECONDS);
        assertTrue(updated.hasTextures(), "프록시 뒤 오프라인 서버도 기본 플래그가 켜져 있어 스킨을 묻는다");
        assertFalse(p.hasTextures(), "원본은 그대로");
        assertFalse(server.internals().profileCache().usesAuthentication(), "서버 자신은 오프라인 모드");
        PlayerProfile viaDefault = Bukkit.createProfile(UUID.randomUUID(), "Steve");
        assertTrue(viaDefault.complete(true), "기본 플래그(프록시 온라인)로 complete 가 세션 서버에 묻는다");
        assertTrue(viaDefault.hasTextures());

        org.bukkit.profile.PlayerProfile spigot = Bukkit.createPlayerProfile(NOTCH, "Notch");
        org.bukkit.profile.PlayerProfile spigotUpdated = spigot.update().get(10, TimeUnit.SECONDS);
        assertEquals(NOTCH, spigotUpdated.getUniqueId());
        assertTrue(((org.bukkit.craftbukkit.profile.CraftPlayerProfile) spigotUpdated).getTextures().isEmpty(), "값 'dGV4'(= tex)에는 스킨 URL 이 없다 — 속성은 들어 있다");
        assertTrue(((org.bukkit.craftbukkit.profile.CraftPlayerProfile) spigotUpdated).buildValue().properties().stream().anyMatch(x -> x.getName().equals("textures")));
    }

    @Test
    void failuresLeaveTheProfileAsItWas() throws Exception {
        sessionStatus = 429;
        try {
            UUID fresh = UUID.randomUUID();   // 사용자 캐시에 속성이 없는 UUID
            PlayerProfile p = Bukkit.createProfile(fresh, "Notch");
            assertFalse(p.complete(true, true), "세션 서버가 막으면 텍스처를 못 받는다");
            assertFalse(p.hasTextures());
            assertEquals(fresh, p.getId());
            assertTrue(p.complete(false, true), "텍스처가 필요 없으면 UUID 와 이름으로 충분");
        } finally {
            sessionStatus = 200;
        }
        PlayerProfile nobody = Bukkit.createProfile("Nobody123");
        assertFalse(nobody.complete(true, true));
        assertNull(nobody.getId(), "없는 이름");
    }

    @Test
    void skullSetOwnerNeverAsksTheNetwork() {
        var s = (org.bukkit.block.Skull) org.bukkit.Material.PLAYER_HEAD.createBlockData().createBlockState();
        int before = nameCalls.get();
        var cache = server.internals().profileCache();
        cache.setUsesAuthentication(true);   // 온라인 모드 서버: 캐시에 없는 이름은 오프라인 프로필을 만들지 않는다
        try {
            assertFalse(s.setOwner("Unknown999"), "인증 모드에서 캐시에 없는 이름은 묻지 않고 false");
        } finally {
            cache.setUsesAuthentication(false);
        }
        assertEquals(before, nameCalls.get());
    }
}

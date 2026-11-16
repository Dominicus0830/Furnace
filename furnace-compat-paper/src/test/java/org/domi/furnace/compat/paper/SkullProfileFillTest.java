package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-141 E11c-1: 머리 아이템 스킨 자동 채우기(Paper SPIGOT-6558). 이름만 있는 머리가 인벤토리 칸에 쓰이면 비동기로 프로필을 찾아 그 칸의 머리에 채워 넣고,
 * 그새 칸이 바뀌었으면 건드리지 않는다. 인벤토리에 붙지 않은 평범한 ItemStack 은 Paper 처럼 채우지 않는다. Mojang 은 로컬 HTTP 가짜.
 */
@SuppressWarnings({"removal", "deprecation"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkullProfileFillTest {

    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    /** {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}} — 스킨 주소가 있는 값. */
    static final String TEXTURES = java.util.Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"}}}".getBytes(StandardCharsets.UTF_8));

    /** 가짜 Mojang 이 아는 이름. 서버의 프로필 캐시는 한 번 찾은 이름을 기억하므로 세션 서버를 붙잡아야 하는 시험은 저마다 다른 이름을 쓴다. */
    static final java.util.Map<UUID, String> KNOWN = java.util.Map.of(
            NOTCH, "Notch",
            UUID.nameUUIDFromBytes("Slow1".getBytes(StandardCharsets.UTF_8)), "Slow1",
            UUID.nameUUIDFromBytes("Slow2".getBytes(StandardCharsets.UTF_8)), "Slow2",
            UUID.nameUUIDFromBytes("Slow3".getBytes(StandardCharsets.UTF_8)), "Slow3");

    FurnaceTestServer server;
    HttpServer http;
    final AtomicInteger nameCalls = new AtomicInteger();
    final AtomicInteger sessionCalls = new AtomicInteger();
    volatile CountDownLatch gate;   // 세션 서버가 열릴 때까지 붙잡아 두는 문(null 이면 바로 답한다)

    @BeforeAll
    void start() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/minecraft/profile/lookup/name/", ex -> {
            nameCalls.incrementAndGet();
            String name = ex.getRequestURI().getPath().substring("/minecraft/profile/lookup/name/".length());
            UUID id = KNOWN.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
            if (id != null) {
                byte[] b = ("{\"id\":\"" + id.toString().replace("-", "") + "\",\"name\":\"" + KNOWN.get(id) + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
            ex.close();
        });
        http.createContext("/session/minecraft/profile/", ex -> {
            sessionCalls.incrementAndGet();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            String raw = ex.getRequestURI().getPath().substring("/session/minecraft/profile/".length());
            UUID id = UUID.fromString(raw.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
            String name = KNOWN.get(id);
            if (name == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                byte[] b = ("{\"id\":\"" + raw + "\",\"name\":\"" + name + "\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + TEXTURES + "\",\"signature\":\"c2ln\"}]}")
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

    @BeforeEach
    void reset() { gate = null; }

    static ItemStack namedHead(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwner(name);
        head.setItemMeta(meta);
        return head;
    }

    static boolean hasSkin(ItemStack stack) {
        return stack.getItemMeta() instanceof SkullMeta m && m.getPlayerProfile() != null && m.getPlayerProfile().hasTextures();
    }

    /** 서버를 한 틱씩 돌리며 조건이 될 때까지 기다린다(조회는 다른 스레드에서 돈다). */
    void await(BooleanSupplier cond, String what) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("timed out: " + what);
            server.tick(1);
            Thread.sleep(20);
        }
    }

    @Test
    void aNamedHeadPutInAnInventoryGetsItsSkin() throws Exception {
        try (TestPlayer tp = server.join("Filler")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Filler");
            assertNotNull(p);
            p.getInventory().setItem(3, namedHead("Notch"));
            await(() -> hasSkin(p.getInventory().getItem(3)), "head in slot 3 gets textures");
            SkullMeta filled = (SkullMeta) p.getInventory().getItem(3).getItemMeta();
            assertEquals(NOTCH, filled.getPlayerProfile().getId(), "이름만 있던 머리에 UUID 도 채워진다");
            assertEquals("Notch", filled.getPlayerProfile().getName());
        }
    }

    @Test
    void aHeadInHandStaysFilledThroughTheMirrorStack() throws Exception {
        try (TestPlayer tp = server.join("Mirror")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Mirror");
            p.getInventory().setItem(0, new ItemStack(Material.PLAYER_HEAD));
            ItemStack mirror = p.getInventory().getItem(0);   // 칸을 비추는 스택
            SkullMeta meta = (SkullMeta) mirror.getItemMeta();
            meta.setOwner("Notch");
            mirror.setItemMeta(meta);                          // 거울에 메타를 적용하면 그 칸에 쓰인다(Paper CraftItemStack.setItemMeta)
            await(() -> hasSkin(p.getInventory().getItem(0)), "mirror setItemMeta fills the slot");
            assertTrue(hasSkin(mirror), "같은 칸을 비추는 거울도 채워진 것을 본다");
        }
    }

    @Test
    void aSlotThatChangedMeanwhileIsLeftAlone() throws Exception {
        try (TestPlayer tp = server.join("Racer")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Racer");
            gate = new CountDownLatch(1);
            int before = sessionCalls.get();
            p.getInventory().setItem(5, namedHead("Slow1"));
            await(() -> sessionCalls.get() > before, "lookup reached the session server");
            p.getInventory().setItem(5, new ItemStack(Material.DIAMOND, 2));   // 조회가 끝나기 전에 칸을 바꾼다
            gate.countDown();
            for (int i = 0; i < 10; i++) {
                server.tick(1);
                Thread.sleep(30);
            }
            ItemStack now = p.getInventory().getItem(5);
            assertEquals(Material.DIAMOND, now.getType());
            assertEquals(2, now.getAmount());
            assertFalse(now.getItemMeta() instanceof SkullMeta, "찾은 프로필이 다른 아이템 위에 덮이지 않는다");
        }
    }

    @Test
    void aStackThatIsNotInAnInventoryIsNotFilled() throws Exception {
        int names = nameCalls.get();
        int sessions = sessionCalls.get();
        ItemStack plain = namedHead("Notch");
        for (int i = 0; i < 5; i++) {
            server.tick(1);
            Thread.sleep(30);
        }
        assertFalse(hasSkin(plain), "Paper 도 NMS 로 옮겨지지 않은 스택은 채우지 않는다");
        assertEquals(names, nameCalls.get());
        assertEquals(sessions, sessionCalls.get());
    }

    @Test
    void aHeadThatAlreadyHasASkinIsNotLookedUpAgain() throws Exception {
        try (TestPlayer tp = server.join("Done")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Done");
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(NOTCH, "Notch");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", TEXTURES, "c2ln"));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
            int names = nameCalls.get();
            int sessions = sessionCalls.get();
            p.getInventory().setItem(1, head);
            for (int i = 0; i < 5; i++) {
                server.tick(1);
                Thread.sleep(30);
            }
            assertEquals(names, nameCalls.get());
            assertEquals(sessions, sessionCalls.get());
            assertTrue(hasSkin(p.getInventory().getItem(1)));
        }
    }
    // ------------------------------------------------------------------ 독립 검토 R2·R3·S4

    @Test
    void anUnknownNameLeavesTheSlotUntouched() throws Exception {
        try (TestPlayer tp = server.join("Unknown")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Unknown");
            int names = nameCalls.get();
            p.getInventory().setItem(2, namedHead("Nobody"));
            org.domi.furnace.api.item.ItemStack written = p.getInventory().getItem(2).asFurnace();
            await(() -> nameCalls.get() > names, "the name lookup ran");
            for (int i = 0; i < 10; i++) {
                server.tick(1);
                Thread.sleep(30);
            }
            assertEquals(written, p.getInventory().getItem(2).asFurnace(), "찾지 못했으면(404) 칸은 쓰인 그대로 — 다시 쓰이지 않는다");
        }
    }

    @Test
    void aDifferentHeadSwappedInMeanwhileIsLeftAlone() throws Exception {
        try (TestPlayer tp = server.join("Swapper")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Swapper");
            gate = new CountDownLatch(1);
            int before = sessionCalls.get();
            p.getInventory().setItem(6, namedHead("Slow2"));
            await(() -> sessionCalls.get() > before, "lookup reached the session server");
            p.getInventory().setItem(6, namedHead("Other"));   // 같은 종류의 다른 프로필
            org.domi.furnace.api.item.ItemStack other = p.getInventory().getItem(6).asFurnace();
            gate.countDown();
            for (int i = 0; i < 10; i++) {
                server.tick(1);
                Thread.sleep(30);
            }
            assertEquals(other, p.getInventory().getItem(6).asFurnace(), "다른 프로필의 머리는 Notch 의 결과로 덮이지 않는다");
        }
    }

    @Test
    void aHeadWithOnlyAUuidGetsItsSkin() throws Exception {
        try (TestPlayer tp = server.join("UuidOnly")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("UuidOnly");
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(Bukkit.createProfile(NOTCH));   // 이름 없음
            head.setItemMeta(meta);
            p.getInventory().setItem(7, head);
            await(() -> hasSkin(p.getInventory().getItem(7)), "uuid-only head gets textures");
            SkullMeta filled = (SkullMeta) p.getInventory().getItem(7).getItemMeta();
            assertEquals(NOTCH, filled.getPlayerProfile().getId());
        }
    }

    @Test
    void withLookupsOffTheOfflineUuidIsFilledLikePaper() throws Exception {
        var cache = server.internals().profileCache();
        try (TestPlayer tp = server.join("Offliner")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Offliner");
            cache.setProfileLookups(false);   // 프록시 없는 오프라인 서버 — completeFromCache(onlineMode=false)
            int sessions = sessionCalls.get();
            p.getInventory().setItem(8, namedHead("Zed"));
            UUID offline = org.domi.furnace.server.players.GameProfile.offlineUuid("Zed");
            await(() -> ((SkullMeta) p.getInventory().getItem(8).getItemMeta()).getPlayerProfile().getId().equals(offline), "offline uuid filled");
            assertEquals(sessions, sessionCalls.get(), "조회가 꺼져 있으면 세션 서버에 묻지 않는다");
        } finally {
            cache.setProfileLookups(true);
        }
    }

    @Test
    void aPlayerWhoLeftBeforeTheAnswerArrivesIsNotAnError() throws Exception {
        TestPlayer tp = server.join("Leaver");
        server.tick(2);
        Player p = Bukkit.getPlayerExact("Leaver");
        gate = new CountDownLatch(1);
        int before = sessionCalls.get();
        p.getInventory().setItem(4, namedHead("Slow3"));
        await(() -> sessionCalls.get() > before, "lookup reached the session server");
        tp.close();
        server.tick(3);
        gate.countDown();
        for (int i = 0; i < 6; i++) {
            server.tick(1);
            Thread.sleep(30);
        }
        assertEquals(null, Bukkit.getPlayerExact("Leaver"));
    }

    @Test
    void aStrangeProfileDoesNotBreakSetItem() throws Exception {
        try (TestPlayer tp = server.join("Strange")) {
            server.tick(2);
            Player p = Bukkit.getPlayerExact("Strange");
            // 속성이 17개(Paper 의 한도 16 초과)인 프로필 — 저장된 아이템에서 올 수 있다. 칸 쓰기가 터지면 안 된다(쓰기는 되고 채우기만 건너뛴다).
            StringBuilder props = new StringBuilder();
            for (int i = 0; i < 17; i++) props.append(i == 0 ? "" : ",").append("{name:\"p").append(i).append("\",value:\"v\"}");
            var odd = new org.domi.furnace.api.item.ItemStack("minecraft:player_head", 1).withComponent("minecraft:profile", "{name:\"Odd\",properties:[" + props + "]}");
            p.getInventory().setItem(9, CompatItems.copyOf(odd));
            assertEquals(Material.PLAYER_HEAD, p.getInventory().getItem(9).getType());
            assertEquals(odd, p.getInventory().getItem(9).asFurnace(), "쓰인 그대로");
        }
    }

    // ------------------------------------------------------------------ S4: 블록 엔티티·큰 상자

    @Test
    void aHeadWrittenToABlockEntityInventoryIsFilled() throws Exception {
        var pos = new org.domi.furnace.api.world.BlockPos(11, -20, 11);
        String dim = "minecraft:overworld";
        server.loadChunk(pos.chunkX(), pos.chunkZ());
        server.onRegion(pos, () -> {
            server.api().world(dim).block(pos).set(new org.domi.furnace.api.world.BlockData("minecraft:barrel", java.util.Map.of()));
            ((org.bukkit.block.Barrel) Compat.block(dim, pos).getState()).getInventory().setItem(2, namedHead("Notch"));
            return null;
        });
        try {
            await(() -> server.onRegion(pos, () -> hasSkin(((org.bukkit.block.Barrel) Compat.block(dim, pos).getState()).getInventory().getItem(2))), "barrel head gets textures");
        } finally {
            server.onRegion(pos, () -> {
                server.api().world(dim).block(pos).set(org.domi.furnace.api.world.BlockData.AIR);
                return null;
            });
        }
    }
}

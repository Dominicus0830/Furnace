// E11d 독립 검토 R1·권고: 머리 프로필 확인의 서버 쪽 조회 — 접속자/사용자 캐시/Mojang/오프라인 프로필(소문자) 분기, 일시 장애 구별, 동시 요청 상한.
package org.domi.furnace.server.players;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadProfileBackendTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir Path tmp;
    private GameProfileCache cache;
    private Optional<GameProfile> joined = Optional.empty();
    private final AtomicInteger nameCalls = new AtomicInteger(), idCalls = new AtomicInteger();
    private final List<String> askedNames = new ArrayList<>();
    private CompletableFuture<Optional<GameProfile>> nameAnswer = CompletableFuture.completedFuture(Optional.empty());
    private CompletableFuture<Optional<GameProfile>> idAnswer = CompletableFuture.completedFuture(Optional.empty());
    private HeadProfileBackend backend;

    @BeforeEach
    void setUp() {
        cache = new GameProfileCache(ProfileLookup.NONE, tmp.resolve("usercache.json"));
        backend = new HeadProfileBackend(n -> joined, cache, new HeadProfileBackend.Sessions() {
            @Override
            public CompletableFuture<Optional<GameProfile>> lookupByName(String name) {
                nameCalls.incrementAndGet();
                askedNames.add(name);
                return nameAnswer;
            }

            @Override
            public CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id) {
                idCalls.incrementAndGet();
                return idAnswer;
            }
        });
    }

    private static <T> T await(CompletableFuture<T> f) { return f.join(); }

    @Test
    void aConnectedPlayerAnswersWithoutAnyLookup() {
        joined = Optional.of(new GameProfile(NOTCH, "Notch"));
        assertEquals(joined, await(backend.profileByName("notch")));
        assertEquals(0, nameCalls.get());
    }

    @Test
    void theUserCacheAnswersWithoutAnyLookup() {
        cache.add(new GameProfile(NOTCH, "Notch"));
        assertEquals(NOTCH, await(backend.profileByName("NOTCH")).orElseThrow().id());
        assertEquals(0, nameCalls.get());
    }

    @Test
    void aMojangHitIsCachedWithItsOwnCapitalisation() {
        cache.setUsesAuthentication(true);
        nameAnswer = CompletableFuture.completedFuture(Optional.of(new GameProfile(NOTCH, "Notch")));
        var p = await(backend.profileByName("nOtCh")).orElseThrow();
        assertEquals("Notch", p.name());
        assertEquals(List.of("notch"), askedNames, "바닐라 get(name) 처럼 소문자 key 로 묻는다");
        assertTrue(cache.peek("notch").isPresent());
    }

    @Test
    void notFoundOnAnOnlineServerIsEmpty() {
        cache.setUsesAuthentication(true);
        assertTrue(await(backend.profileByName("Nobody")).isEmpty());
        assertTrue(cache.peek("nobody").isEmpty());
    }

    @Test
    void notFoundOnAnOfflineServerIsTheLowercaseOfflineProfileAndIsNotCached() {
        cache.setUsesAuthentication(false);
        var p = await(backend.profileByName("Steve2X")).orElseThrow();
        assertEquals("steve2x", p.name(), "바닐라 createUnknownProfile(key) — 소문자 이름");
        assertEquals(GameProfile.offlineUuid("steve2x"), p.id());
        assertTrue(cache.peek("steve2x").isEmpty(), "새 이름마다 usercache.json 을 쓰지 않는다");
    }

    @Test
    void aTransientFailureOnAnOnlineServerIsAFailureNotAMissingProfile() {
        cache.setUsesAuthentication(true);
        nameAnswer = CompletableFuture.failedFuture(new java.io.IOException("429"));
        var ex = assertThrows(CompletionException.class, () -> backend.profileByName("Notch").join());
        assertInstanceOf(java.io.IOException.class, ex.getCause());
        idAnswer = CompletableFuture.failedFuture(new java.io.IOException("503"));
        assertThrows(CompletionException.class, () -> backend.fetchProfile(NOTCH).join());
    }

    @Test
    void aFailureOnAnOfflineServerFallsBackLikeVanilla() {
        cache.setUsesAuthentication(false);
        nameAnswer = CompletableFuture.failedFuture(new java.io.IOException("no network"));
        assertEquals("notch", await(backend.profileByName("Notch")).orElseThrow().name());
        idAnswer = CompletableFuture.failedFuture(new java.io.IOException("no network"));
        assertTrue(await(backend.fetchProfile(NOTCH)).isEmpty());
    }

    @Test
    void sessionLookupsRunAtMostFourAtATimeAndTheRestWait() {
        List<CompletableFuture<Optional<GameProfile>>> pending = new ArrayList<>();
        AtomicInteger started = new AtomicInteger();
        var limiter = new HeadProfileBackend.Limiter(4);
        List<CompletableFuture<Optional<GameProfile>>> outs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            outs.add(limiter.submit(() -> {
                started.incrementAndGet();
                var f = new CompletableFuture<Optional<GameProfile>>();
                pending.add(f);
                return f;
            }));
        }
        assertEquals(4, started.get());
        assertEquals(4, limiter.runningForTests());
        assertEquals(6, limiter.queuedForTests());
        pending.get(0).complete(Optional.empty());
        assertEquals(5, started.get(), "하나가 끝나면 줄 선 것이 하나 시작한다");
        pending.get(1).completeExceptionally(new java.io.IOException("x"));
        assertEquals(6, started.get(), "실패도 자리를 반납한다");
        assertTrue(outs.get(0).isDone());
        assertTrue(outs.get(1).isCompletedExceptionally());
        for (int i = 2; i < pending.size(); i++) pending.get(i).complete(Optional.empty());
        for (int i = 0; i < pending.size(); i++) if (!pending.get(i).isDone()) pending.get(i).complete(Optional.empty());
        assertEquals(10, started.get());
        assertEquals(0, limiter.runningForTests());
        assertEquals(0, limiter.queuedForTests());
    }
}

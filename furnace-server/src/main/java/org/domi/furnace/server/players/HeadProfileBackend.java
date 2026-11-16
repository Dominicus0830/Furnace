// 머리 프로필 확인(item/ProfileFill)이 쓰는 서버 쪽 조회: 접속자·사용자 캐시 → Mojang 이름 조회 → 오프라인 프로필(오프라인 모드), 그리고 세션 서버 UUID 조회.
//
// 바닐라 GameProfileCache.get(name) 은 이름을 소문자로 바꾼 key 로 조회·오프라인 프로필 만들기를 한다(그래서 오프라인 서버에서 Mojang 에 없는 "Steve2X" 는
// (offlineUuid("steve2x"), "steve2x") 가 된다). 이 서버의 GameProfileCache 는 Mojang 조회기가 없어(ProfileLookup.NONE) 캐시만 보므로 세션·서비스 서버 조회를 여기서 부른다.
//
// 요청은 동시에 몇 개로 묶고(바닐라 Util.backgroundExecutor 가 동시 실행을 제한하는 것처럼), 429·5xx·연결 실패는 몇 번 다시 시도한다. 끝내 안 되면 온라인 모드에서는
// **실패한 Future** 로 끝내 스킨 없는 결과가 아이템·블록에 굳지 않게 한다(머리는 확인 안 됨으로 남아 다음에 다시 시도). 오프라인 모드는 인터넷이 없을 수도 있어 바닐라처럼
// 없는 것으로 쳐서 오프라인 프로필로 떨어진다.
package org.domi.furnace.server.players;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.domi.furnace.server.item.ProfileFill;

public final class HeadProfileBackend implements ProfileFill.Backend {

    /** Mojang 쪽 조회(시험에서 바꿔 끼운다). 429·5xx·연결 실패는 끝내 안 되면 실패한 Future, "없음"은 empty. */
    public interface Sessions {
        CompletableFuture<Optional<GameProfile>> lookupByName(String name);

        CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id);
    }

    public static final int MAX_CONCURRENT = 4;
    public static final int ATTEMPTS = 3;
    public static final Duration RETRY_DELAY = Duration.ofMillis(750);

    private final Function<String, Optional<GameProfile>> online;
    private final GameProfileCache cache;
    private final Sessions sessions;
    private final Limiter limiter = new Limiter(MAX_CONCURRENT);

    /** online: 그 이름으로 접속한 플레이어의 프로필(없으면 empty). */
    public HeadProfileBackend(Function<String, Optional<GameProfile>> online, GameProfileCache cache, Sessions sessions) {
        this.online = online;
        this.cache = cache;
        this.sessions = sessions;
    }

    @Override
    public CompletableFuture<Optional<GameProfile>> profileByName(String name) {
        var joined = online.apply(name);
        if (joined.isPresent()) return CompletableFuture.completedFuture(joined);
        var cached = cache.peekAndTouch(name);
        if (cached.isPresent()) return CompletableFuture.completedFuture(cached);
        String key = name.toLowerCase(Locale.ROOT);   // GameProfileCache.get: lookupGameProfile(key)
        return limiter.submit(() -> sessions.lookupByName(key)).handle((found, error) -> {
            if (error == null && found.isPresent()) {
                cache.add(found.get());
                return found;
            }
            if (error != null && cache.usesAuthentication()) throw new java.util.concurrent.CompletionException(unwrap(error));
            // 못 찾음(또는 오프라인 모드의 연결 실패): 온라인 모드는 없음, 오프라인 모드는 소문자 이름의 오프라인 프로필 — 사용자 캐시에는 넣지 않는다
            return cache.usesAuthentication() ? Optional.<GameProfile>empty() : Optional.of(GameProfile.offline(key));
        });
    }

    @Override
    public CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id) {
        return limiter.submit(() -> sessions.fetchProfile(id)).handle((found, error) -> {
            if (error == null) return found;
            if (cache.usesAuthentication()) throw new java.util.concurrent.CompletionException(unwrap(error));
            return Optional.<GameProfile>empty();   // 오프라인 모드: 닿지 않으면 바닐라처럼 없는 것
        });
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof java.util.concurrent.CompletionException && t.getCause() != null ? t.getCause() : t;
    }

    /** 동시에 도는 비동기 작업 수를 max 로 묶는다(넘치면 줄을 선다). */
    static final class Limiter {
        private final int max;
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private int running;

        Limiter(int max) { this.max = max; }

        <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> task) {
            CompletableFuture<T> out = new CompletableFuture<>();
            Runnable start = () -> {
                CompletableFuture<T> f;
                try {
                    f = task.get();
                } catch (RuntimeException e) {
                    f = CompletableFuture.failedFuture(e);
                }
                f.whenComplete((v, e) -> {
                    if (e != null) out.completeExceptionally(unwrap(e));
                    else out.complete(v);
                    next();
                });
            };
            boolean now;
            synchronized (this) {
                now = running < max;
                if (now) running++;
                else queue.add(start);
            }
            if (now) start.run();
            return out;
        }

        private void next() {
            Runnable r;
            synchronized (this) {
                r = queue.poll();
                if (r == null) {
                    running--;
                    return;
                }
            }
            r.run();
        }

        synchronized int runningForTests() { return running; }

        synchronized int queuedForTests() { return queue.size(); }
    }
}

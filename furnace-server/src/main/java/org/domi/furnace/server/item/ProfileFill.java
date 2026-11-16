// 이름·UUID 만 있는 플레이어 머리의 프로필(스킨) 채우기 — 바닐라 PlayerHeadItem.verifyComponentsAfterLoad + ResolvableProfile.resolve +
// SkullBlockEntity.fetchGameProfile 의 서버 쪽 대응.
//
// 바닐라: 아이템 스택이 만들어질 때(NBT·명령 인자·네트워크 읽기·applyComponents) 머리의 profile 이 "확인 안 됨"(isResolved 아님)이면 세션 서버에 비동기로
// 물어, 끝나면 **그 스택 객체**에 채운 프로필을 쓴다(주 스레드). 머리 블록 엔티티도 setOwner 에서 같은 일을 한다.
// Furnace 의 서버 ItemStack 은 불변 값이라 "그 스택 객체"가 없다. 그래서 스택이 **놓이는 곳**(플레이어 인벤토리 칸·떨어진 아이템·상자 칸·머리 블록)이
// 쓰기 지점에서 이 클래스를 불러 "이 칸이 아직 같은 확인 안 된 프로필을 든 머리일 때만 채운 프로필로 바꾸는" 갱신을 맡긴다({@link Target}).
// 확인은 한 프로필에 한 번이다 — 결과는 항상 확인된 프로필(실패해도 이름 → (UUID 0, 이름) 으로 떨어진다)이라 다시 시작되지 않는다.
//
// 가정: 서버 쪽 읽기 경로 가운데 몹 장비·액자·말 가방 등 아직 이 훅을 달지 않은 곳(docs/feature-parity/context-notes/2026-11-16-e11d-skull-fill.md)은
// 채우지 않는다. 같은 머리가 다른 칸으로 옮겨지면 옮긴 칸의 쓰기 지점이 같은 확인(캐시)에 다시 붙는다.
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.players.GameProfile;

@VanillaPort({
    "net.minecraft.world.item.PlayerHeadItem",
    "net.minecraft.world.item.component.ResolvableProfile",
    "net.minecraft.world.level.block.entity.SkullBlockEntity"
})
public final class ProfileFill {

    private static final System.Logger LOG = System.getLogger("FurnaceProfileFill");

    private static final String PROFILE = "minecraft:profile";
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private ProfileFill() {}

    // ------------------------------------------------------------------ 바깥 세계(시험에서 바꿔 끼운다)

    /** 프로필을 찾는 곳. */
    public interface Backend {
        /**
         * GameProfileCache.getAsync(name): 접속자·사용자 캐시, 없으면 Mojang 이름 조회, 그래도 없으면 오프라인 모드에서는 오프라인 프로필(온라인 모드는 empty).
         * 느릴 수 있으니 Future 로 돌려준다(부른 스레드를 막지 않는다).
         */
        CompletableFuture<Optional<GameProfile>> profileByName(String name);

        /** 세션 서버 profile/&lt;uuid&gt;?unsigned=false(서명된 속성 포함). 못 찾거나 실패하면 empty. */
        CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id);
    }

    /** 칸이 어디에 있든 "지금 값을 fn 에 넘겨 null 이 아닌 값을 돌려주면 그것으로 바꾸는" 갱신을 그 칸의 소유 스레드에서 해 주는 쪽. fn 은 여러 번 불릴 수 있다. */
    @FunctionalInterface
    public interface Target {
        void update(UnaryOperator<ItemStack> fn);
    }

    private static volatile Service service;

    /** 서버가 뜰 때 한 번. 이전 서비스는 버린다. */
    public static Service install(Backend backend) { return install(new Service(backend, newPool())); }

    /** 시험용: 만들어 둔 서비스를 설치한다(조회 풀을 직접 쥔 것). */
    public static Service install(Service s) {
        service = s;
        return s;
    }

    /** 서버가 멈출 때(자기가 설치한 서비스일 때만). */
    public static void uninstall(Service s) {
        if (service == s) service = null;
        s.shutdown();
    }

    public static Service service() { return service; }

    private static ThreadPoolExecutor newPool() {
        var tf = new java.util.concurrent.ThreadFactory() {
            private int n;

            @Override
            public synchronized Thread newThread(Runnable r) {
                Thread t = new Thread(r, "furnace-profile-fill-" + (++n));
                t.setDaemon(true);
                return t;
            }
        };
        ThreadPoolExecutor p = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), tf);
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    // ------------------------------------------------------------------ 확인 안 된 프로필 찾기

    private static volatile int headIdSeen = -1;

    private static int headId() {
        int id = headIdSeen;
        if (id >= 0) return id;
        Integer found = ItemContext.installed() ? ItemContext.id("minecraft:item", "minecraft:player_head") : null;
        if (found == null) return -1;
        return headIdSeen = found;
    }

    /** 시험이 레지스트리를 다시 설치할 때 기억한 아이템 번호를 비운다. */
    public static void resetItemIdForTests() { headIdSeen = -1; }

    /**
     * ResolvableProfile.isResolved: 속성이 있거나, 이름과 UUID 가 둘 다 있거나 둘 다 없다.
     * 이름만 있거나 UUID 만 있고 속성이 없으면 확인되지 않은 것이다.
     */
    public static boolean isResolved(Map<String, Object> profile) {
        Object props = profile.get("properties");
        if (props instanceof List<?> l && !l.isEmpty()) return true;
        return profile.containsKey("id") == profile.containsKey("name");
    }

    /** 플레이어 머리 아이템이 확인 안 된 프로필(정규 값)을 들고 있으면 그것, 아니면 null. 쓰기 지점마다 부르므로 싸야 한다. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> unresolvedProfile(ItemStack s) {
        if (s == null || s.patch().isEmpty() || s.itemId() != headId() || s.isEmpty()) return null;
        Object v = s.patch().entries().get(PROFILE);
        if (!(v instanceof Map<?, ?> m)) return null;
        Map<String, Object> profile = (Map<String, Object>) m;
        return isResolved(profile) ? null : profile;
    }

    /** 프로필 컴포넌트 값 두 개가 같은가(정규 값 비교 — UUID 는 int[] 라 equals 로는 안 된다). */
    public static boolean sameProfile(Object a, Object b) {
        return a != null && b != null && DataComponents.PROFILE_TYPE.same(a, b);
    }

    /** current 가 아직 original 프로필을 든 머리일 때만 채운 프로필로 바꾼 스택, 아니면 null(그대로 둔다). */
    public static ItemStack replaceIfStillTheSame(ItemStack current, Map<String, Object> original, Map<String, Object> resolved) {
        if (current == null || current.isEmpty() || current.itemId() != headId()) return null;
        if (!sameProfile(original, current.patch().entries().get(PROFILE))) return null;
        return new ItemStack(current.itemId(), current.count(), withProfile(current, resolved));
    }

    private static ComponentPatch withProfile(ItemStack s, Map<String, Object> resolved) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(s.patch().entries());
        merged.put(PROFILE, resolved);
        return ComponentPatch.ofNormalized(merged);
    }

    // ------------------------------------------------------------------ 쓰기 지점에서 부르는 문

    /**
     * stack 이 target 에 막 놓였다. 확인 안 된 머리면 확인을 시작하고(진행 중·끝난 확인은 서로 나눈다), 끝나면 target 에서 "아직 같은 프로필을 든 머리일 때만" 채운다.
     * 어느 스레드에서든, 어떤 잠금 안에서든 부를 수 있다 — 조회는 조회 풀에서 돌고 채우기도 조회 풀에서 target 으로 넘어가므로 부른 스레드를 막거나 되부르지 않는다.
     * 예외를 던지지 않는다.
     */
    public static void onStore(ItemStack stack, Target target) {
        Service s = service;
        if (s == null) return;
        Map<String, Object> original = unresolvedProfile(stack);
        if (original == null) return;
        s.fill(original, target);
    }

    /** onStore 와 같지만 확인 안 된 프로필인지만 먼저 알려 준다(타깃을 만들기 전에 싸게 거르려고). */
    public static boolean needsFill(ItemStack stack) {
        return service != null && unresolvedProfile(stack) != null;
    }

    // ------------------------------------------------------------------ 서비스

    public static final class Service {
        private static final int CACHE_SIZE = 256;
        private static final long EXPIRE_NANOS = TimeUnit.MINUTES.toNanos(10);
        static final long FAILURE_NANOS = TimeUnit.SECONDS.toNanos(30);

        private final Backend backend;
        private final Executor pool;
        private final Cache<String, Optional<GameProfile>> byName = new Cache<>();
        private final Cache<UUID, Optional<GameProfile>> byId = new Cache<>();
        private volatile boolean stopped;

        Service(Backend backend, Executor pool) {
            this.backend = backend;
            this.pool = pool;
        }

        void shutdown() {
            stopped = true;
            if (pool instanceof ThreadPoolExecutor t) t.shutdown();
        }

        /** 시험에서 쓴다: 조회 풀을 바꿔 끼운 서비스. */
        public static Service forTests(Backend backend, Executor pool) { return new Service(backend, pool); }

        /** original(확인 안 된 정규 프로필)을 확인해 target 에 채운다(아이템 스택용). */
        public void fill(Map<String, Object> original, Target target) {
            resolved(original, found -> target.update(cur -> replaceIfStillTheSame(cur, original, found)));
        }

        /**
         * original 을 확인하고, 끝나면 조회 풀에서 then 에 확인된 프로필을 넘긴다. 확인이 실패하면(바닐라처럼) 아무것도 하지 않는다.
         * then 이 던진 예외(칸이 사라짐·접속 끊김 등 채울 곳이 없는 것)는 삼킨다.
         */
        public void resolved(Map<String, Object> original, java.util.function.Consumer<Map<String, Object>> then) {
            if (stopped) return;
            try {
                resolve(original).whenCompleteAsync((found, error) -> {
                    if (error != null || found == null) {
                        LOG.log(System.Logger.Level.DEBUG, "Failed to resolve player head profile", error);
                        return;
                    }
                    try {
                        then.accept(found);
                    } catch (RuntimeException e) {
                        LOG.log(System.Logger.Level.DEBUG, "player head profile fill dropped", e);
                    }
                }, pool);
            } catch (RuntimeException e) {   // 조회 풀이 닫힘 등
                LOG.log(System.Logger.Level.DEBUG, "player head profile lookup not started", e);
            }
        }

        /** ResolvableProfile.resolve: 이미 확인됐으면 그대로, UUID 가 있으면 UUID 로, 아니면 이름으로 묻는다. 못 찾으면 있는 값으로 떨어진다. */
        public CompletableFuture<Map<String, Object>> resolve(Map<String, Object> profile) {
            if (isResolved(profile)) return CompletableFuture.completedFuture(profile);
            if (profile.get("id") instanceof int[] ints) {
                UUID id = uuid(ints);
                String name = profile.get("name") instanceof String n ? n : "";
                return fetchById(id).thenApply(found -> canonical(found.orElseGet(() -> new GameProfile(id, name))));
            }
            String name = (String) profile.get("name");
            return fetchByName(name).thenApply(found -> canonical(found.orElseGet(() -> new GameProfile(NIL_UUID, name))));
        }

        /** SkullBlockEntity.fetchGameProfile(name): 올바른 플레이어 이름만, 이름 → 사용자 캐시/조회 → UUID 로 세션 서버(서명된 속성), 세션이 못 주면 이름 조회 결과. */
        CompletableFuture<Optional<GameProfile>> fetchByName(String name) {
            if (name == null || !GameProfile.isValidPlayerName(name)) return CompletableFuture.completedFuture(Optional.empty());
            return byName.get(name, () -> backend.profileByName(name).thenCompose(found -> {
                if (found.isEmpty()) return CompletableFuture.completedFuture(Optional.<GameProfile>empty());
                return fetchById(found.get().id()).thenApply(session -> session.isPresent() ? session : found);
            }));
        }

        /** SkullBlockEntity.fetchGameProfile(uuid). */
        CompletableFuture<Optional<GameProfile>> fetchById(UUID id) {
            return byId.get(id, () -> backend.fetchProfile(id));
        }

        /**
         * guava LoadingCache(10 분 동안 안 쓰면 만료, 256 개) 같은 것. 바닐라는 실패한 Future 도 10 분 그대로 두지만, 여기서는 조회가 일시 장애로 실패하면
         * 머리가 확인 안 됨으로 남아 다시 시도되어야 하므로 실패한 Future 는 {@link #FAILURE_NANOS} 만 기억한다(그 사이 같은 머리가 쓰일 때마다 요청이 나가지 않게).
         */
        private static final class Cache<K, V> {
            private record Slot<V>(CompletableFuture<V> future, long lastUse, long created) {}

            private final LinkedHashMap<K, Slot<V>> map = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Slot<V>> eldest) { return size() > CACHE_SIZE; }
            };

            CompletableFuture<V> get(K key, java.util.function.Supplier<CompletableFuture<V>> load) {
                long now = System.nanoTime();
                synchronized (map) {
                    Slot<V> s = map.get(key);
                    if (s != null && live(s, now)) {
                        map.put(key, new Slot<>(s.future(), now, s.created()));
                        return s.future();
                    }
                }
                CompletableFuture<V> f;
                try {
                    f = load.get();
                } catch (RuntimeException e) {
                    f = CompletableFuture.failedFuture(e);
                }
                synchronized (map) {
                    Slot<V> s = map.get(key);
                    if (s != null && live(s, now)) return s.future();   // 다른 스레드가 먼저 시작했다
                    map.put(key, new Slot<>(f, now, now));
                }
                return f;
            }

            private static <V> boolean live(Slot<V> s, long now) {
                if (s.future().isCompletedExceptionally()) return now - s.created() < FAILURE_NANOS;
                return now - s.lastUse() < EXPIRE_NANOS;
            }
        }
    }

    // ------------------------------------------------------------------ 정규 값 ↔ GameProfile

    private static UUID uuid(int[] a) {
        return new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL), ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
    }

    /** new ResolvableProfile(GameProfile): 이름·UUID 둘 다 있고 속성은 세션이 준 것. */
    static Map<String, Object> canonical(GameProfile gp) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", gp.name());
        int[] ints = Codecs.uuidInts(gp.id());
        raw.put("id", List.of(ints[0], ints[1], ints[2], ints[3]));
        List<Object> props = new ArrayList<>();
        for (GameProfile.Property p : gp.properties()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("value", p.value());
            if (p.signature() != null) m.put("signature", p.signature());
            props.add(m);
        }
        if (!props.isEmpty()) raw.put("properties", props);
        @SuppressWarnings("unchecked") Map<String, Object> out = (Map<String, Object>) DataComponents.PROFILE_TYPE.normalize(raw);
        return out;
    }
}

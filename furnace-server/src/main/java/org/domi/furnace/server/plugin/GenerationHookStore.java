// 월드젠 단계 훅 저장소(LifecycleEvents.WORLD_GENERATION). 키 → (플러그인, 훅). 월드는 키만 쥐고 생성 때마다 여기서 찾는다 —
// 플러그인이 꺼지면 항목이 빠지므로 월드가 플러그인 클래스를 붙들지 않는다(핫 리로드 때 클래스로더가 새지 않게).
package org.domi.furnace.server.plugin;

import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.domi.furnace.api.plugin.lifecycle.WorldGenerationRegistrar;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;

public final class GenerationHookStore {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private record Entry(String owner, ChunkGenerationHooks hooks) {}

    private final Map<String, Entry> byKey = new ConcurrentHashMap<>();

    /** 그 키의 훅(없거나 플러그인이 꺼졌으면 null). 아무 스레드. */
    public ChunkGenerationHooks get(String key) {
        Entry e = key == null ? null : byKey.get(key);
        return e == null ? null : e.hooks();
    }

    /** 그 키를 등록한 플러그인 이름(없으면 null). */
    public String ownerOf(String key) {
        Entry e = byKey.get(key);
        return e == null ? null : e.owner();
    }

    /** 등록된 키(정렬). */
    public java.util.Set<String> keys() { return new TreeSet<>(byKey.keySet()); }

    /** 플러그인이 꺼졌다 — 그 플러그인 키를 모두 뺀다(그 월드의 새 청크는 바닐라로). */
    public void removeOwner(String owner) {
        byKey.values().removeIf(e -> e.owner().equals(owner));
    }

    /** 그 플러그인에 묶인 등록기. close() 뒤에는 쓸 수 없다. */
    public Registrar registrar(String owner, String namespace, Logger logger) { return new Registrar(owner, namespace, logger); }

    public final class Registrar implements WorldGenerationRegistrar {
        private final String owner;
        private final String namespace;
        private final Logger logger;
        private volatile boolean open = true;

        Registrar(String owner, String namespace, Logger logger) {
            this.owner = owner;
            this.namespace = namespace;
            this.logger = logger;
        }

        public void close() { open = false; }

        @Override
        public String register(String key, ChunkGenerationHooks hooks) {
            if (!open) throw new IllegalStateException("월드젠 훅 등록은 WORLD_GENERATION 처리기 안에서만 할 수 있다");
            java.util.Objects.requireNonNull(key, "key");
            java.util.Objects.requireNonNull(hooks, "hooks");
            String full = key.indexOf(':') < 0 ? namespace + ":" + key : key;
            if (!KEY.matcher(full).matches()) throw new IllegalArgumentException("키 형식이 잘못됐다(namespace:path): " + key);
            if (!full.startsWith(namespace + ":")) {
                throw new IllegalArgumentException("키의 namespace 는 이 플러그인의 '" + namespace + "' 여야 한다: " + key);
            }
            Entry prev = byKey.get(full);
            if (prev != null && !prev.owner().equals(owner)) {
                throw new IllegalArgumentException("다른 플러그인(" + prev.owner() + ")이 쓰는 월드젠 훅 키: " + full);
            }
            byKey.put(full, new Entry(owner, hooks));
            if (logger != null) logger.fine("월드젠 훅 등록: " + full);
            return full;
        }
    }
}

// 플러그인 정의 레지스트리 저장소(API PluginRegistries 구현). 레지스트리·항목·알림마다 소유 플러그인 이름을 기록해 두고
// 플러그인이 꺼지거나 부트스트랩·불러오기에 실패하면 removeOwner 로 그 플러그인 몫을 정리한다.
// 쓰기는 레지스트리 단위로 잠그고 바꿀 수 없는 스냅숏을 갈아 끼운다 — 읽기는 잠금이 없다. 알림은 잠금 밖에서 부른다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.registry.PluginRegistries;
import org.domi.furnace.api.registry.PluginRegistry;
import org.domi.furnace.api.registry.RegistryListener;
import org.domi.furnace.server.data.ResourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginRegistryStore {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 키 → 레지스트리(만든 순서). 바꿀 때는 this 잠금 + 사본 교체. */
    private volatile Map<String, Reg<?>> registries = Map.of();

    /** 그 플러그인(이름·namespace)에 묶인 보기. */
    public PluginRegistries view(String owner, String namespace) { return new View(owner, namespace); }

    /** 그 플러그인 몫을 모두 정리한다: 만든 레지스트리는 통째로, 남의 레지스트리에 넣은 항목·알림은 하나씩. */
    public void removeOwner(String owner) {
        List<Reg<?>> dropped = new ArrayList<>();
        synchronized (this) {
            Map<String, Reg<?>> next = new LinkedHashMap<>();
            for (Reg<?> r : registries.values()) {
                if (r.owner.equals(owner)) dropped.add(r);
                else next.put(r.key, r);
            }
            if (!dropped.isEmpty()) registries = Collections.unmodifiableMap(next);
        }
        for (Reg<?> r : dropped) r.remove();
        for (Reg<?> r : registries.values()) r.removeOwner(owner);
    }

    /** "ns:path" 꼴(namespace 생략 불가)인지, namespace 가 expected 인지 검사한다. */
    static String checkKey(String key, String expectedNamespace, String what) {
        if (key == null || key.indexOf(':') <= 0) throw new IllegalArgumentException(what + " 키는 namespace:path 꼴이어야 한다: " + key);
        ResourceId id = ResourceId.tryParse(key);
        if (id == null || id.path().isEmpty()) throw new IllegalArgumentException("잘못된 " + what + " 키: " + key);
        if (!id.namespace().equals(expectedNamespace)) {
            throw new IllegalArgumentException(what + " 키의 namespace 는 '" + expectedNamespace + "' 여야 한다: " + key);
        }
        return key;
    }

    // ------------------------------------------------------------------ 레지스트리

    private record Entry<T>(T value, String owner) {}

    private record Listener<T>(RegistryListener<? super T> listener, String owner) {}

    private static final class Reg<T> {
        final String key;
        final Class<T> type;
        final String owner;
        /** 키 → 항목(넣은 순서, 바꿀 수 없는 사본). */
        volatile Map<String, Entry<T>> entries = Map.of();
        volatile boolean frozen, removed;
        final List<Listener<T>> listeners = new CopyOnWriteArrayList<>();

        Reg(String key, Class<T> type, String owner) {
            this.key = key;
            this.type = type;
            this.owner = owner;
        }

        T register(String who, String k, Object value) {
            if (value == null) throw new NullPointerException("value");
            if (!type.isInstance(value)) throw new IllegalArgumentException(key + " 의 값은 " + type.getName() + " 이어야 한다: " + value.getClass().getName());
            T v = type.cast(value);
            synchronized (this) {
                if (removed) throw new IllegalStateException("사라진 레지스트리(만든 플러그인이 꺼졌다): " + key);
                if (frozen) throw new IllegalStateException("고정된 레지스트리: " + key);
                if (entries.containsKey(k)) throw new IllegalArgumentException(key + " 에 이미 있는 항목: " + k + " (" + entries.get(k).owner() + ")");
                Map<String, Entry<T>> next = new LinkedHashMap<>(entries);
                next.put(k, new Entry<>(v, who));
                entries = Collections.unmodifiableMap(next);
            }
            notify(k, v, RegistryListener::added);
            return v;
        }

        boolean unregister(String who, String k) {
            Entry<T> e;
            synchronized (this) {
                if (removed) throw new IllegalStateException("사라진 레지스트리(만든 플러그인이 꺼졌다): " + key);
                e = entries.get(k);
                if (e == null) return false;
                if (!e.owner().equals(who)) throw new IllegalArgumentException(k + " 는 " + e.owner() + " 의 항목이다");
                if (frozen) throw new IllegalStateException("고정된 레지스트리: " + key);
                Map<String, Entry<T>> next = new LinkedHashMap<>(entries);
                next.remove(k);
                entries = Collections.unmodifiableMap(next);
            }
            notify(k, e.value(), RegistryListener::removed);
            return true;
        }

        /** 꺼진 플러그인의 항목·알림을 뺀다(고정돼 있어도). */
        void removeOwner(String who) {
            listeners.removeIf(l -> l.owner().equals(who));
            Map<String, Entry<T>> gone = new LinkedHashMap<>();
            synchronized (this) {
                if (removed) return;
                Map<String, Entry<T>> next = new LinkedHashMap<>();
                for (var e : entries.entrySet()) {
                    if (e.getValue().owner().equals(who)) gone.put(e.getKey(), e.getValue());
                    else next.put(e.getKey(), e.getValue());
                }
                if (gone.isEmpty()) return;
                entries = Collections.unmodifiableMap(next);
            }
            for (var e : gone.entrySet()) notify(e.getKey(), e.getValue().value(), RegistryListener::removed);
        }

        /** 만든 플러그인이 꺼졌다 — 남은 알림에 모든 항목을 removed 로 알리고 비운다. */
        void remove() {
            Map<String, Entry<T>> gone;
            synchronized (this) {
                if (removed) return;
                removed = true;
                gone = entries;
                entries = Map.of();
            }
            listeners.removeIf(l -> l.owner().equals(owner));
            for (var e : gone.entrySet()) notify(e.getKey(), e.getValue().value(), RegistryListener::removed);
            listeners.clear();
        }

        private void notify(String k, T v, TriConsumer<RegistryListener<? super T>, String, T> call) {
            for (Listener<T> l : listeners) {
                try {
                    call.accept(l.listener(), k, v);
                } catch (Throwable t) {
                    log.error("플러그인 레지스트리 {} 알림 처리 중 예외 ({})", key, l.owner(), t);
                }
            }
        }
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    // ------------------------------------------------------------------ 보기

    private final class View implements PluginRegistries {
        private final String owner, namespace;

        View(String owner, String namespace) {
            this.owner = owner;
            this.namespace = namespace;
        }

        @Override
        public String namespace() { return namespace; }

        @Override
        public <T> PluginRegistry<T> create(String key, Class<T> type) {
            checkKey(key, namespace, "레지스트리");
            if (type == null) throw new NullPointerException("type");
            Reg<T> r = new Reg<>(key, type, owner);
            synchronized (PluginRegistryStore.this) {
                if (registries.containsKey(key)) throw new IllegalArgumentException("이미 있는 레지스트리: " + key);
                Map<String, Reg<?>> next = new LinkedHashMap<>(registries);
                next.put(key, r);
                registries = Collections.unmodifiableMap(next);
            }
            return new RegView<>(r, owner, namespace);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> PluginRegistry<T> registry(String key, Class<T> type) {
            Reg<?> r = registries.get(key);
            if (r == null) return null;
            if (!type.isAssignableFrom(r.type)) {
                throw new IllegalArgumentException(key + " 의 값 타입은 " + r.type.getName() + " 이다(요청: " + type.getName() + ")");
            }
            return new RegView<>((Reg<T>) r, owner, namespace);
        }

        @Override
        public Collection<String> keys() { return List.copyOf(registries.keySet()); }
    }

    /** 한 플러그인에 묶인 레지스트리 보기. 값 타입 T 는 실제 타입의 상위 타입일 수 있다(넣을 때 실제 타입으로 검사). */
    private static final class RegView<T> implements PluginRegistry<T> {
        private final Reg<T> reg;
        private final String viewer, namespace;

        RegView(Reg<T> reg, String viewer, String namespace) {
            this.reg = reg;
            this.viewer = viewer;
            this.namespace = namespace;
        }

        @Override public String key() { return reg.key; }

        @Override public Class<T> type() { return reg.type; }

        @Override public String owner() { return reg.owner; }

        @Override
        public T register(String key, T value) {
            checkKey(key, namespace, "항목");
            return reg.register(viewer, key, value);
        }

        @Override
        public boolean unregister(String key) { return reg.unregister(viewer, key); }

        @Override
        public T get(String key) {
            Entry<T> e = reg.entries.get(key);
            return e == null ? null : e.value();
        }

        @Override
        public boolean contains(String key) { return reg.entries.containsKey(key); }

        @Override
        public String entryOwner(String key) {
            Entry<T> e = reg.entries.get(key);
            return e == null ? null : e.owner();
        }

        @Override
        public List<String> keys() { return List.copyOf(reg.entries.keySet()); }

        @Override
        public Collection<T> values() {
            List<T> out = new ArrayList<>(reg.entries.size());
            for (Entry<T> e : reg.entries.values()) out.add(e.value());
            return out;
        }

        @Override
        public Map<String, T> entries() {
            Map<String, T> out = new LinkedHashMap<>();
            reg.entries.forEach((k, e) -> out.put(k, e.value()));
            return Collections.unmodifiableMap(out);
        }

        @Override
        public int size() { return reg.entries.size(); }

        @Override
        public void freeze() {
            if (!reg.owner.equals(viewer)) throw new IllegalStateException(reg.key + " 는 " + reg.owner + " 만 고정할 수 있다");
            synchronized (reg) {
                reg.frozen = true;
            }
        }

        @Override
        public boolean isFrozen() { return reg.frozen; }

        @Override
        public boolean isRemoved() { return reg.removed; }

        @Override
        public void addListener(RegistryListener<? super T> listener) {
            if (listener == null) throw new NullPointerException("listener");
            if (reg.removed) throw new IllegalStateException("사라진 레지스트리(만든 플러그인이 꺼졌다): " + reg.key);
            reg.listeners.add(new Listener<>(listener, viewer));
        }

        @Override
        public String toString() { return "PluginRegistry[" + reg.key + "]"; }
    }
}

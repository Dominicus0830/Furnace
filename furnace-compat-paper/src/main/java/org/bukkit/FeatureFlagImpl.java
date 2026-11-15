// FeatureFlag 구현(Paper 와 같은 record — equals·hashCode·toString 은 record 기본).
package org.bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record FeatureFlagImpl(NamespacedKey key) implements FeatureFlag {

    /** 지금 쓰는 플래그(폐기된 둘은 빠진다) — 키 문자열 → 플래그. */
    static final Map<String, FeatureFlag> ALL_FLAGS = new ConcurrentHashMap<>();

    static FeatureFlag create(String name) {
        FeatureFlag flag = new FeatureFlagImpl(NamespacedKey.minecraft(name));
        if (ALL_FLAGS.putIfAbsent(flag.getKey().toString(), flag) != null) throw new IllegalStateException("Tried to add duplicate feature flag: " + name);
        return flag;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    record Deprecated(NamespacedKey key) implements FeatureFlag {
        @Override
        public NamespacedKey getKey() { return key; }
    }
}
